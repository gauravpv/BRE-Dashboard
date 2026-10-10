package com.bredashboard.tester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bredashboard.tester.config.BajajTesterProperties;
import com.bredashboard.tester.domain.BajajEnvironment;
import com.bredashboard.tester.dto.OperationEntryDto;
import com.bredashboard.tester.dto.OperationListResponseDto;
import com.bredashboard.tester.exception.BajajTesterException;
import com.bredashboard.tester.util.AesCbcCrypto;
import com.bredashboard.tester.util.BajajHttpDebugLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BajajOperationListService {

    private static final Logger log = LoggerFactory.getLogger(BajajOperationListService.class);

    /** Refresh a few minutes before Bajaj rotates hashcode/salt (every 24 hours). */
    private static final long CACHE_SKEW_SECONDS = 900;
    private static final long DEFAULT_TTL_SECONDS = 86_400;

    private final BajajTesterProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<BajajEnvironment, CachedList> cache = new ConcurrentHashMap<>();

    public BajajOperationListService(BajajTesterProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    public OperationListResponseDto fetchOperations(BajajEnvironment environment) {
        return fetchOperations(environment, false);
    }

    /**
     * Returns the cached list when it is still within the 24-hour window. {@code forceRefresh}
     * is the tester reload button — it hits Bajaj again and replaces the cache.
     */
    public OperationListResponseDto fetchOperations(BajajEnvironment environment, boolean forceRefresh) {
        if (!forceRefresh) {
            CachedList cached = cache.get(environment);
            if (cached != null && !cached.isExpired()) {
                return cached.list().withCacheMeta(true, cached.secondsRemaining());
            }
        }
        BajajTesterProperties.EnvironmentConfig config = configFor(environment);
        OperationListResponseDto fresh = parseOperationList(fetchLiveOperationList(config), environment, config);
        long ttl = config.getOperationListTtlSeconds() > 0 ? config.getOperationListTtlSeconds() : DEFAULT_TTL_SECONDS;
        long effectiveTtl = Math.max(ttl - Math.min(CACHE_SKEW_SECONDS, ttl / 2), 60L);
        cache.put(environment, new CachedList(fresh, Instant.now().plusSeconds(effectiveTtl)));
        return fresh.withCacheMeta(false, effectiveTtl);
    }

    /**
     * Resolves a single operation (by publicurl or slug, case-insensitive) from the cached
     * operation list, fetching from Bajaj only when the 24-hour cache is empty or expired.
     */
    public OperationEntryDto findOperation(BajajEnvironment environment, String publicUrlOrSlug) {
        requireOperationName(publicUrlOrSlug);
        return findOperation(fetchOperations(environment).operations(), publicUrlOrSlug, environment);
    }

    /**
     * Same lookup against an already-fetched list, so callers that just loaded the operation
     * list do not trigger a second round-trip.
     */
    public static OperationEntryDto findOperation(
            List<OperationEntryDto> operations,
            String publicUrlOrSlug,
            BajajEnvironment environment
    ) {
        requireOperationName(publicUrlOrSlug);
        String wanted = normalizePath(publicUrlOrSlug);
        for (OperationEntryDto entry : operations) {
            if (wanted.equals(normalizePath(entry.publicUrl())) || wanted.equals(normalizePath(entry.slug()))) {
                return entry;
            }
        }
        throw new BajajTesterException(
                "Operation '" + publicUrlOrSlug + "' not found in the " + environment + " operation list");
    }

    private static void requireOperationName(String publicUrlOrSlug) {
        if (publicUrlOrSlug == null || publicUrlOrSlug.isBlank()) {
            throw new BajajTesterException("Operation name is required");
        }
    }

    private BajajTesterProperties.EnvironmentConfig configFor(BajajEnvironment environment) {
        return properties.config(environment);
    }

    private String fetchLiveOperationList(BajajTesterProperties.EnvironmentConfig config) {
        try {
            String requestJson = objectMapper.writeValueAsString(config.getRequestBody());
            String encryptedBody = AesCbcCrypto.encryptUtf8(
                    requestJson,
                    config.getEncryptionKey(),
                    config.getEncryptionIv()
            );

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.operationListUrl()))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(encryptedBody, StandardCharsets.UTF_8));

            for (Map.Entry<String, String> header : config.getHeaders().entrySet()) {
                if (header.getKey() != null
                        && !header.getKey().isBlank()
                        && !"additionalinfo1".equalsIgnoreCase(header.getKey())) {
                    builder.header(header.getKey(), header.getValue() != null ? header.getValue() : "");
                }
            }

                HttpRequest request = builder.build();
                BajajHttpDebugLogger.logRequest(log, "operation-list", request, requestJson, objectMapper);
                HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
                BajajHttpDebugLogger.logResponse(
                    log, "operation-list", response.statusCode(), response.headers().map(), response.body(), objectMapper);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BajajTesterException(
                        "Operation list request failed with HTTP " + response.statusCode()
                                + (response.body() != null && !response.body().isBlank()
                                ? ": " + abbreviate(response.body(), 240)
                                : "")
                );
            }

                String decrypted = AesCbcCrypto.decryptUtf8(
                    response.body(),
                    config.getEncryptionKey(),
                    config.getEncryptionIv()
            );
                BajajHttpDebugLogger.logDecryptedBody(log, "operation-list", "decrypted response", decrypted, objectMapper);
                return decrypted;
        } catch (BajajTesterException ex) {
            log.warn("Bajaj operation-list call failed: {}", ex.getClass().getSimpleName());
            throw ex;
        } catch (Exception ex) {
            log.warn("Bajaj operation-list call failed: {}", ex.getClass().getSimpleName());
            throw new BajajTesterException("Failed to fetch operation list", ex);
        }
    }

    private OperationListResponseDto parseOperationList(
            String plainJson,
            BajajEnvironment environment,
            BajajTesterProperties.EnvironmentConfig config
    ) {
        try {
            JsonNode root = objectMapper.readTree(plainJson);
            JsonNode operationsNode = root.path("operationList");
            if (!operationsNode.isArray()) {
                log.warn("Bajaj operation-list response rejected: missing operationList array; rootType={}, "
                                + "rootFields={}, description={}, statusCode={}",
                        root.getNodeType(), root.isObject() ? fieldNames(root) : "<not-an-object>",
                        abbreviate(text(root, "description"), 160), abbreviate(text(root, "statusCode"), 80));
                throw new BajajTesterException("Operation list response missing operationList array");
            }

            List<OperationEntryDto> operations = new ArrayList<>(operationsNode.size());
            for (JsonNode node : operationsNode) {
                String publicUrl = text(node, "publicurl");
                operations.add(new OperationEntryDto(
                        publicUrl,
                        text(node, "slug"),
                        text(node, "appversion"),
                        text(node, "module"),
                        text(node, "apiversion"),
                        config.apiUrl(publicUrl),
                        text(node, "salt"),
                        text(node, "hashcode")
                ));
            }

            operations.sort((a, b) -> a.publicUrl().compareToIgnoreCase(b.publicUrl()));

            return new OperationListResponseDto(
                    environment.name(),
                    config.getBaseUrl(),
                    text(root, "description"),
                    text(root, "statusCode"),
                    operations,
                    null,
                    false,
                    null
            );
        } catch (BajajTesterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BajajTesterException("Failed to parse operation list response", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "<none>";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "…";
    }

    private static String normalizePath(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase();
    }

    /** Visible for tests. */
    void putCache(BajajEnvironment environment, OperationListResponseDto list, Instant expiresAt) {
        cache.put(environment, new CachedList(list, expiresAt));
    }

    private record CachedList(OperationListResponseDto list, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        long secondsRemaining() {
            return Math.max(Duration.between(Instant.now(), expiresAt).getSeconds(), 0L);
        }
    }
}