package com.bredashboard.health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bredashboard.health.config.HealthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelHubOAuthTokenClientTest {

    private ObjectMapper objectMapper;
    private HealthProperties properties;
    private ModelHubOAuthTokenClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new HealthProperties();
        client = new ModelHubOAuthTokenClient(objectMapper, properties);
    }

    @Test
    void parseTokenResponse_extractsAccessTokenAndExpiresIn() {
        String body = """
                {
                  "token_type": "Bearer",
                  "scope": "api://91becc5d-213e-4d9b-b0dc-e481c31fde4c/default",
                  "expires_in": 86399,
                  "ext_expires_in": 86399,
                  "access_token": "eyJ.test.access",
                  "refresh_token": "1.test.refresh",
                  "id_token": "eyJ.test.id"
                }
                """;

        var token = ModelHubOAuthTokenClient.parseTokenResponse(body, objectMapper);

        assertThat(token.accessToken()).isEqualTo("eyJ.test.access");
        assertThat(token.expiresIn()).isEqualTo(86399L);
    }

    @Test
    void parseTokenResponse_defaultsExpiresInWhenMissing() {
        String body = """
                {
                  "access_token": "eyJ.test.token"
                }
                """;

        var token = ModelHubOAuthTokenClient.parseTokenResponse(body, objectMapper);

        assertThat(token.accessToken()).isEqualTo("eyJ.test.token");
        assertThat(token.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void parseTokenResponse_failsWhenAccessTokenMissing() {
        assertThatThrownBy(() -> ModelHubOAuthTokenClient.parseTokenResponse("{}", objectMapper))
                .isInstanceOf(ModelHubOAuthTokenClient.ModelHubOAuthException.class)
                .hasMessageContaining("access_token");
    }

    @Test
    void tokenCacheEntry_honoursExpirySkew() {
        Instant fetchedAt = Instant.parse("2026-01-01T12:00:00Z");
        var entry = ModelHubOAuthTokenClient.TokenCacheEntry.from(
                new ModelHubOAuthTokenClient.TokenResponse("token-a", 3600),
                fetchedAt);

        assertThat(entry.isValid(fetchedAt.plusSeconds(3539), ModelHubOAuthTokenClient.EXPIRY_SKEW_SECONDS))
                .isTrue();
        assertThat(entry.isValid(fetchedAt.plusSeconds(3540), ModelHubOAuthTokenClient.EXPIRY_SKEW_SECONDS))
                .isFalse();
    }

    @Test
    void getAccessToken_failsWhenCredentialsMissing() {
        assertThatThrownBy(client::getAccessToken)
                .isInstanceOf(ModelHubOAuthTokenClient.ModelHubOAuthException.class)
                .hasMessageContaining("bredashboard.health.model-hub.oauth");
    }

    @Test
    void getAccessToken_failsWhenPasswordMissing() {
        properties.getHealth().getModelHub().getOauth().setUsername("user@test");

        assertThatThrownBy(client::getAccessToken)
                .isInstanceOf(ModelHubOAuthTokenClient.ModelHubOAuthException.class)
                .hasMessageContaining("bredashboard.health.model-hub.oauth");
    }
}
