package com.opsconsole.tester.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsconsole.tester.config.BajajTesterProperties;
import com.opsconsole.tester.domain.BajajEnvironment;
import com.opsconsole.tester.dto.OperationEntryDto;
import com.opsconsole.tester.dto.TokenStatusDto;
import com.opsconsole.tester.exception.BajajTesterException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BajajTokenParsingTest {

    private static BajajTokenService newService() {
        BajajTesterProperties properties = new BajajTesterProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        return new BajajTokenService(
                properties,
                new BajajOperationListService(properties, objectMapper),
                objectMapper
        );
    }

    @Test
    void parseTokenResponse_readsAccessTokenAndExpiry() {
        String body = """
                {
                  "access_token": "202609192219451789836585950nSrfa23RmWqQyfAu1q7npUyORr2qig",
                  "token_type": "Bearer",
                  "expires_in": 54000,
                  "status": "SUCCESS",
                  "refresh_token": "202609192219451789836585951lSF4jNamMO9rZu5xt2UnaVzKt1r6sc",
                  "token_generation_time": "20260919221945",
                  "deviceId": "hqoYIrMWpVbGXPh2pqBKJxfTo110as",
                  "statuscode": 200,
                  "username": "bflbre",
                  "ttl": 54000
                }
                """;

        BajajTokenService.TokenPayload payload = newService().parseTokenResponse(body);

        assertThat(payload.token())
                .isEqualTo("202609192219451789836585950nSrfa23RmWqQyfAu1q7npUyORr2qig");
        assertThat(payload.expiresInSeconds()).isEqualTo(54000L);
    }

    @Test
    void parseTokenResponse_defaultsExpiryWhenAbsent() {
        BajajTokenService.TokenPayload payload =
                newService().parseTokenResponse("{\"access_token\":\"abc123\"}");

        assertThat(payload.token()).isEqualTo("abc123");
        assertThat(payload.expiresInSeconds()).isZero();
    }

    @Test
    void parseTokenResponse_rejectsResponseWithoutToken() {
        assertThatThrownBy(() -> newService().parseTokenResponse("{\"status\":\"FAILED\"}"))
                .isInstanceOf(BajajTesterException.class)
                .hasMessageContaining("access_token");
    }

    /**
     * The token is primed right after the operation list; a failure there must be reported as
     * status rather than thrown, so the API list still renders.
     */
    @Test
    void primeToken_reportsFailureInsteadOfThrowing() {
        TokenStatusDto status = newService().primeToken(BajajEnvironment.UAT, List.of());

        assertThat(status.ready()).isFalse();
        assertThat(status.error()).isNotBlank();
        assertThat(status.preview()).isNull();
    }

    @Test
    void primeToken_failsWhenOauthTokenMissingFromList() {
        List<OperationEntryDto> operations = List.of(new OperationEntryDto(
                "authbre/authorization", "authorization", "v1", "*", "v1",
                "hash", "salt", null, null,
                "https://example.test/authbre/authorization", "hash", "salt"));

        TokenStatusDto status = newService().primeToken(BajajEnvironment.UAT, operations);

        assertThat(status.ready()).isFalse();
        assertThat(status.error()).contains("oauth-token");
    }
}