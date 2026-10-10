package com.bredashboard.tester.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BajajHttpDebugLoggerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sanitizeBody_redactsSecretsAndKeepsDiagnosticFields() {
        String sanitized = BajajHttpDebugLogger.sanitizeBody(
                "{\"description\":\"Success\",\"statusCode\":\"7001\","
                        + "\"operationList\":[{\"publicurl\":\"api/path\","
                        + "\"hashcode\":\"secret-hash\",\"salt\":\"secret-salt\"}]}",
                objectMapper);

        assertThat(sanitized).contains("Success", "7001", "api/path", "[REDACTED]")
                .doesNotContain("secret-hash", "secret-salt");
    }

    @Test
    void sanitizeBody_omitsNonJsonPayload() {
        assertThat(BajajHttpDebugLogger.sanitizeBody("not-json-secret", objectMapper))
                .isEqualTo("<non-JSON body omitted; chars=15>");
    }

        @Test
        void sanitizeBody_omitsQuotedOpaquePayload() {
                assertThat(BajajHttpDebugLogger.sanitizeBody("\"opaque-secret\"", objectMapper))
                                .isEqualTo("<text body omitted; chars=13>");
        }
}