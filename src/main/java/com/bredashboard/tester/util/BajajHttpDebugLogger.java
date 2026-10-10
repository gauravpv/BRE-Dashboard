package com.bredashboard.tester.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BajajHttpDebugLogger {

    private static final int MAX_BODY_CHARS = 12_000;
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "authorization", "token", "accesstoken", "idtoken", "cookie", "setcookie",
            "key", "iv", "encryptionkey", "encryptioniv", "hashcode", "salt", "secret",
            "password", "credential", "username", "userid", "deviceid", "source",
            "subscriptionid", "additionalinfo", "email", "phone", "mobile", "pan",
            "aadhaar", "account", "customer", "address", "dob", "birth", "otp", "name"
    );

    private BajajHttpDebugLogger() {
    }

    public static void logRequest(
            Logger log,
            String callName,
            HttpRequest request,
            String body,
            ObjectMapper objectMapper
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("Bajaj {} request: method={} uri={} headers={} body={}",
            callName, request.method(), safeUri(request.uri()), sanitizeHeaders(request.headers().map()),
                sanitizeBody(body, objectMapper));
    }

    public static void logResponse(
            Logger log,
            String callName,
            int statusCode,
            Map<String, List<String>> headers,
            String body,
            ObjectMapper objectMapper
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("Bajaj {} response: status={} headers={} body={}",
                callName, statusCode, sanitizeHeaders(headers), sanitizeBody(body, objectMapper));
    }

    public static void logDecryptedBody(
            Logger log,
            String callName,
            String direction,
            String body,
            ObjectMapper objectMapper
    ) {
        if (log.isDebugEnabled()) {
            log.debug("Bajaj {} {}: {}", callName, direction, sanitizeBody(body, objectMapper));
        }
    }

    static String sanitizeBody(String body, ObjectMapper objectMapper) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        try {
            JsonNode parsed = objectMapper.readTree(body);
            if (parsed.isTextual()) {
                return "<text body omitted; chars=" + parsed.textValue().length() + ">";
            }
            JsonNode sanitized = redact(parsed);
            String safeJson = objectMapper.writeValueAsString(sanitized);
            return abbreviate(safeJson);
        } catch (Exception ex) {
            return "<non-JSON body omitted; chars=" + body.length() + ">";
        }
    }

    private static JsonNode redact(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(field -> {
                if (isSensitive(field.getKey())) {
                    result.put(field.getKey(), "[REDACTED]");
                } else {
                    result.set(field.getKey(), redact(field.getValue()));
                }
            });
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(redact(value)));
            return result;
        }
        if (node.isTextual() && node.textValue().length() > MAX_BODY_CHARS) {
            return JsonNodeFactory.instance.textNode("[TEXT OMITTED; chars=" + node.textValue().length() + "]");
        }
        return node;
    }

    private static boolean isSensitive(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_NAMES.stream().anyMatch(normalized::contains);
    }

    private static Map<String, List<String>> sanitizeHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        headers.forEach((name, values) -> sanitized.put(name,
                isSensitive(name) ? List.of("[REDACTED]") : values));
        return sanitized;
    }

    private static String safeUri(URI uri) {
        String base = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
        return uri.getRawQuery() == null ? base : base + "?[REDACTED]";
    }

    private static String abbreviate(String body) {
        if (body.length() <= MAX_BODY_CHARS) {
            return body;
        }
        return body.substring(0, MAX_BODY_CHARS) + "...[truncated; chars=" + body.length() + "]";
    }

}