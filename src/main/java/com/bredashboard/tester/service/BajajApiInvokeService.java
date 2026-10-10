package com.bredashboard.tester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bredashboard.tester.config.BajajTesterProperties;
import com.bredashboard.tester.domain.BajajEnvironment;
import com.bredashboard.tester.dto.BajajInvokeRequest;
import com.bredashboard.tester.dto.BajajInvokeResponse;
import com.bredashboard.tester.exception.BajajTesterException;
import com.bredashboard.tester.util.AesCbcCrypto;
import com.bredashboard.tester.util.BajajHttpDebugLogger;
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
import java.util.Map;

@Service
public class BajajApiInvokeService {

    private static final Logger log = LoggerFactory.getLogger(BajajApiInvokeService.class);

    private final BajajTesterProperties properties;
    private final BajajTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BajajApiInvokeService(
            BajajTesterProperties properties,
            BajajTokenService tokenService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    public BajajInvokeResponse invoke(BajajInvokeRequest request) {
        validateRequest(request);
        BajajEnvironment environment = BajajEnvironment.parseOrUat(request.environment());
        BajajTesterProperties.EnvironmentConfig config = properties.config(environment);
        String requestUrl = config.apiUrl(request.publicUrl());

        long start = System.nanoTime();
        String stage = "request encryption";
        try {
            String diagnosticBody = normalizeJson(request.requestBody());
            String encryptedBody;
            try {
                encryptedBody = AesCbcCrypto.encryptUtf8(
                        diagnosticBody,
                        request.encryptionKey(),
                        request.encryptionIv()
                );
            } catch (IllegalStateException ex) {
                log.warn("Bajaj API request encryption failed: keyBytes={}, ivBytes={}, bodyChars={}, reason={}",
                        request.encryptionKey().getBytes(StandardCharsets.UTF_8).length,
                        request.encryptionIv().getBytes(StandardCharsets.UTF_8).length,
                        diagnosticBody.length(), ex.getMessage());
                throw ex;
            }

            // The per-API Postman script posts the ciphertext wrapped in JSON quotes.
            String payload = config.isQuoteEncryptedBody()
                    ? "\"" + encryptedBody + "\""
                    : encryptedBody;

            // Step 1: obtain the rotating token header (cached per environment).
                stage = "token acquisition";
            String token = tokenService.currentToken(environment);
                stage = "HTTP request";
            HttpResponse<String> response = execute(config, requestUrl, payload, token, diagnosticBody);

            // A rejected token is indistinguishable from a normal 401/403, so retry once
            // with a freshly minted token before surfacing the failure.
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.debug("Bajaj API invoke received HTTP {}; retrying once with a refreshed token",
                        response.statusCode());
                tokenService.invalidate(environment);
                token = tokenService.refreshToken(environment);
                response = execute(config, requestUrl, payload, token, diagnosticBody);
            }

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String body = response.body() == null ? "" : response.body();

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new BajajInvokeResponse(
                        response.statusCode(),
                        durationMs,
                        body.getBytes(StandardCharsets.UTF_8).length,
                        requestUrl,
                        prettyJson(body),
                        "HTTP " + response.statusCode()
                );
            }

            stage = "response decryption";
            String decrypted = AesCbcCrypto.decryptUtf8(
                    body,
                    request.encryptionKey(),
                    request.encryptionIv()
            );
                BajajHttpDebugLogger.logDecryptedBody(log, "API invoke", "decrypted response", decrypted, objectMapper);

            return new BajajInvokeResponse(
                    response.statusCode(),
                    durationMs,
                    decrypted.getBytes(StandardCharsets.UTF_8).length,
                    requestUrl,
                    prettyJson(decrypted),
                    null
            );
        } catch (BajajTesterException ex) {
            log.warn("Bajaj API invoke failed during {}: {}", stage, ex.getClass().getSimpleName());
            throw ex;
        } catch (Exception ex) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("Bajaj API invoke failed during {}: {}", stage, ex.getClass().getSimpleName());
            return new BajajInvokeResponse(
                    0,
                    durationMs,
                    0,
                    requestUrl,
                    "",
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()
            );
        }
    }

    private HttpResponse<String> execute(
            BajajTesterProperties.EnvironmentConfig config,
            String requestUrl,
            String encryptedBody,
            String token,
            String diagnosticBody
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(encryptedBody, StandardCharsets.UTF_8));

        for (Map.Entry<String, String> header : config.effectiveApiHeaders().entrySet()) {
            String name = header.getKey();
            if (name == null || name.isBlank() || !StringUtils.hasText(header.getValue())) {
                continue;
            }
            // The live token always wins over any statically configured value.
            if ("token".equalsIgnoreCase(name)) {
                continue;
            }
            builder.header(name, header.getValue());
        }

        if (StringUtils.hasText(token)) {
            builder.header("token", token);
        }

        HttpRequest httpRequest = builder.build();
        BajajHttpDebugLogger.logRequest(log, "API invoke", httpRequest, diagnosticBody, objectMapper);
        HttpResponse<String> response = httpClient.send(
            httpRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        BajajHttpDebugLogger.logResponse(
            log, "API invoke", response.statusCode(), response.headers().map(), response.body(), objectMapper);
        return response;
    }

    private void validateRequest(BajajInvokeRequest request) {
        if (!StringUtils.hasText(request.publicUrl())) {
            throw new BajajTesterException("API path is required");
        }
        if (!StringUtils.hasText(request.encryptionKey()) || !StringUtils.hasText(request.encryptionIv())) {
            throw new BajajTesterException("Encryption key and IV are required for the selected API");
        }
        if (!StringUtils.hasText(request.requestBody())) {
            throw new BajajTesterException("Request body is required");
        }
        try {
            objectMapper.readTree(normalizeJson(request.requestBody()));
        } catch (Exception ex) {
            throw new BajajTesterException("Request body must be valid JSON");
        }
    }

    private String prettyJson(String json) {
        if (!StringUtils.hasText(json)) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ex) {
            return json;
        }
    }

    private String normalizeJson(String body) {
        return body.trim();
    }
}