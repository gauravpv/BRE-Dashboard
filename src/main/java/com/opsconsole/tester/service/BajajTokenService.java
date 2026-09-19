package com.opsconsole.tester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsconsole.tester.config.BajajTesterProperties;
import com.opsconsole.tester.domain.BajajEnvironment;
import com.opsconsole.tester.dto.OperationEntryDto;
import com.opsconsole.tester.dto.TokenStatusDto;
import com.opsconsole.tester.exception.BajajTesterException;
import com.opsconsole.tester.util.AesCbcCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Obtains the short-lived {@code token} header value required by every Bajaj API.
 *
 * <p>Flow (mirrors the Postman pre/post scripts):
 * <ol>
 *   <li>Look up {@code oauth-token} in the operation list to get its per-API hashcode/salt.</li>
 *   <li>AES-256-CBC encrypt the credential body with that key/IV and POST it.</li>
 *   <li>Decrypt the response and read {@code access_token} out of it.</li>
 * </ol>
 * The token is then cached per environment and reused as the {@code token} header on every
 * subsequent API call until {@code expires_in} elapses (minus a small safety margin), at which
 * point the next call transparently fetches a new one.
 */
@Service
public class BajajTokenService {

    private static final Logger log = LoggerFactory.getLogger(BajajTokenService.class);

    /** Header names that must never be forwarded on the token call itself. */
    private static final List<String> TOKEN_HEADER_BLOCKLIST = List.of("token", "additionalinfo1");

    /**
     * Candidate field names for the token value inside the decrypted response.
     * {@code access_token} is what the Bajaj oauth-token API actually returns.
     */
    private static final List<String> TOKEN_FIELDS =
            List.of("access_token", "token", "accessToken", "authToken", "id_token");

    /** Candidate field names carrying the token lifetime, in seconds. */
    private static final List<String> EXPIRY_FIELDS = List.of("expires_in", "expiresIn", "ttl");

    /**
     * Renew slightly before the real expiry so an in-flight request never races the cutover.
     */
    private static final long EXPIRY_SKEW_SECONDS = 60;

    private final BajajTesterProperties properties;
    private final BajajOperationListService operationListService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<BajajEnvironment, CachedToken> cache = new ConcurrentHashMap<>();

    public BajajTokenService(
            BajajTesterProperties properties,
            BajajOperationListService operationListService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.operationListService = operationListService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    /** Returns a cached token when still fresh, otherwise fetches a new one. */
    public String currentToken(BajajEnvironment environment) {
        CachedToken cached = cache.get(environment);
        if (cached != null && !cached.isExpired()) {
            return cached.token();
        }
        return refreshToken(environment);
    }

    /**
     * Warms the token straight after the operation list has been loaded, reusing that list so
     * {@code oauth-token} is not looked up with a second round-trip. Never throws: a token
     * failure is reported back to the page instead of breaking the API list.
     */
    public TokenStatusDto primeToken(BajajEnvironment environment, List<OperationEntryDto> operations) {
        CachedToken cached = cache.get(environment);
        if (cached != null && !cached.isExpired()) {
            return TokenStatusDto.ready(cached.token(), cached.secondsRemaining());
        }
        try {
            OperationEntryDto tokenOperation = BajajOperationListService.findOperation(
                    operations, configFor(environment).getTokenPath(), environment);
            CachedToken fresh = fetchAndCache(environment, tokenOperation);
            return TokenStatusDto.ready(fresh.token(), fresh.secondsRemaining());
        } catch (BajajTesterException ex) {
            log.warn("Could not pre-fetch {} token: {}", environment, ex.getMessage());
            return TokenStatusDto.failed(ex.getMessage());
        } catch (Exception ex) {
            log.warn("Could not pre-fetch {} token", environment, ex);
            return TokenStatusDto.failed("Failed to fetch API token");
        }
    }

    /** Always performs a live token call and replaces the cached value. */
    public String refreshToken(BajajEnvironment environment) {
        BajajTesterProperties.EnvironmentConfig config = configFor(environment);
        OperationEntryDto tokenOperation =
                operationListService.findOperation(environment, config.getTokenPath());
        return fetchAndCache(environment, tokenOperation).token();
    }

    /** Current cached state without triggering a network call. */
    public TokenStatusDto status(BajajEnvironment environment) {
        CachedToken cached = cache.get(environment);
        if (cached == null || cached.isExpired()) {
            return TokenStatusDto.failed("No token cached yet");
        }
        return TokenStatusDto.ready(cached.token(), cached.secondsRemaining());
    }

    /** Drops the cached token so the next call fetches a new one. */
    public void invalidate(BajajEnvironment environment) {
        cache.remove(environment);
    }

    private CachedToken fetchAndCache(BajajEnvironment environment, OperationEntryDto tokenOperation) {
        BajajTesterProperties.EnvironmentConfig config = configFor(environment);
        String key = tokenOperation.encryptionKey();
        String iv = tokenOperation.encryptionIv();
        if (!StringUtils.hasText(key) || !StringUtils.hasText(iv)) {
            throw new BajajTesterException(
                    "Operation list did not provide a hashcode/salt for " + config.getTokenPath());
        }

        TokenPayload payload = parseTokenResponse(callTokenApi(config, key, iv));

        // Prefer the lifetime the server reported; fall back to the configured TTL.
        long ttl = payload.expiresInSeconds() > 0
                ? payload.expiresInSeconds()
                : (config.getTokenTtlSeconds() > 0 ? config.getTokenTtlSeconds() : 600L);
        long effectiveTtl = Math.max(ttl - EXPIRY_SKEW_SECONDS, 30L);

        CachedToken cached = new CachedToken(payload.token(), Instant.now().plusSeconds(effectiveTtl));
        cache.put(environment, cached);
        log.debug("Refreshed Bajaj {} token, reusing it for the next {}s", environment, effectiveTtl);
        return cached;
    }

    private String callTokenApi(BajajTesterProperties.EnvironmentConfig config, String key, String iv) {
        try {
            if (config.getTokenRequestBody().isEmpty()) {
                throw new BajajTesterException(
                        "Token credentials are not configured (opsconsole.bajaj-tester.*.token-request-body)");
            }

            String requestJson = objectMapper.writeValueAsString(config.getTokenRequestBody());
            String encryptedBody = AesCbcCrypto.encryptUtf8(requestJson, key, iv);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.tokenUrl()))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(encryptedBody, StandardCharsets.UTF_8));

            for (Map.Entry<String, String> header : config.effectiveApiHeaders().entrySet()) {
                String name = header.getKey();
                if (name == null || name.isBlank() || !StringUtils.hasText(header.getValue())) {
                    continue;
                }
                if (TOKEN_HEADER_BLOCKLIST.contains(name.toLowerCase())) {
                    continue;
                }
                builder.header(name, header.getValue());
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BajajTesterException(
                        "Token request failed with HTTP " + response.statusCode()
                                + (StringUtils.hasText(response.body())
                                ? ": " + abbreviate(response.body(), 240)
                                : ""));
            }

            return AesCbcCrypto.decryptUtf8(response.body(), key, iv);
        } catch (BajajTesterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BajajTesterException("Failed to fetch API token", ex);
        }
    }

    /** Parses the decrypted oauth-token response. Package-private for testing. */
    TokenPayload parseTokenResponse(String decryptedJson) {
        try {
            JsonNode root = objectMapper.readTree(decryptedJson);
            String token = searchToken(root, 0);
            if (!StringUtils.hasText(token)) {
                throw new BajajTesterException(
                        "Token response did not contain an access_token field: "
                                + abbreviate(decryptedJson, 240));
            }
            return new TokenPayload(token, readExpiry(root));
        } catch (BajajTesterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BajajTesterException("Failed to parse token response", ex);
        }
    }

    /** Reads {@code expires_in} (seconds) from the response; 0 when absent or unusable. */
    private long readExpiry(JsonNode root) {
        for (String field : EXPIRY_FIELDS) {
            JsonNode value = root.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asLong();
            }
            if (value.isTextual()) {
                try {
                    return Long.parseLong(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // fall through to the next candidate field
                }
            }
        }
        return 0L;
    }

    /** Depth-limited search so a nested {@code data}/{@code response} wrapper still resolves. */
    private String searchToken(JsonNode node, int depth) {
        if (node == null || !node.isObject() || depth > 3) {
            return null;
        }
        for (String field : TOKEN_FIELDS) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        for (JsonNode child : node) {
            String nested = searchToken(child, depth + 1);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private BajajTesterProperties.EnvironmentConfig configFor(BajajEnvironment environment) {
        return properties.config(environment);
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private record CachedToken(String token, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        long secondsRemaining() {
            return Math.max(Duration.between(Instant.now(), expiresAt).getSeconds(), 0L);
        }
    }

    record TokenPayload(String token, long expiresInSeconds) {
    }
}