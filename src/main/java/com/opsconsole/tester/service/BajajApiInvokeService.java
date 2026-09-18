package com.opsconsole.tester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsconsole.tester.config.BajajTesterProperties;
import com.opsconsole.tester.domain.BajajEnvironment;
import com.opsconsole.tester.dto.BajajInvokeRequest;
import com.opsconsole.tester.dto.BajajInvokeResponse;
import com.opsconsole.tester.exception.BajajTesterException;
import com.opsconsole.tester.util.AesCbcCrypto;
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

    private final BajajTesterProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BajajApiInvokeService(BajajTesterProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    public BajajInvokeResponse invoke(BajajInvokeRequest request) {
        validateRequest(request);
        BajajEnvironment environment = parseEnvironment(request.environment());
        BajajTesterProperties.EnvironmentConfig config = configFor(environment);
        String requestUrl = config.apiUrl(request.publicUrl());

        long start = System.nanoTime();
        try {
            String encryptedBody = AesCbcCrypto.encryptUtf8(
                    normalizeJson(request.requestBody()),
                    request.encryptionKey(),
                    request.encryptionIv()
            );

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(encryptedBody, StandardCharsets.UTF_8));

            for (Map.Entry<String, String> header : config.getHeaders().entrySet()) {
                if (header.getKey() != null && !header.getKey().isBlank()
                        && StringUtils.hasText(header.getValue())) {
                    builder.header(header.getKey(), header.getValue());
                }
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String body = response.body() == null ? "" : response.body();

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new BajajInvokeResponse(
                        response.statusCode(),
                        durationMs,
                        body.getBytes(StandardCharsets.UTF_8).length,
                        requestUrl,
                        tryPrettyPrint(body),
                        "HTTP " + response.statusCode()
                );
            }

            String decrypted = AesCbcCrypto.decryptUtf8(
                    body,
                    request.encryptionKey(),
                    request.encryptionIv()
            );

            return new BajajInvokeResponse(
                    response.statusCode(),
                    durationMs,
                    decrypted.getBytes(StandardCharsets.UTF_8).length,
                    requestUrl,
                    prettyJson(decrypted),
                    null
            );
        } catch (BajajTesterException ex) {
            throw ex;
        } catch (Exception ex) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
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

    private String normalizeJson(String body) {
        return body.trim();
    }

    private String prettyJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ex) {
            return json;
        }
    }

    private String tryPrettyPrint(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            return prettyJson(body);
        } catch (Exception ex) {
            return body;
        }
    }

    private static BajajEnvironment parseEnvironment(String environment) {
        if (environment != null && environment.equalsIgnoreCase("PROD")) {
            return BajajEnvironment.PROD;
        }
        return BajajEnvironment.UAT;
    }

    private BajajTesterProperties.EnvironmentConfig configFor(BajajEnvironment environment) {
        return environment == BajajEnvironment.PROD ? properties.getProd() : properties.getUat();
    }
}
