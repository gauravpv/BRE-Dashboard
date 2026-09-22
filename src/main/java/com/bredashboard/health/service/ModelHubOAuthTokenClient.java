package com.bredashboard.health.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bredashboard.health.config.HealthProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

@Service
public class ModelHubOAuthTokenClient {

    public static final int EXPIRY_SKEW_SECONDS = 60;
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 3600L;

    private final HealthProperties healthProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Object refreshLock = new Object();
    private volatile TokenCacheEntry cachedToken;

    public ModelHubOAuthTokenClient(ObjectMapper objectMapper, HealthProperties healthProperties) {
        this.objectMapper = objectMapper;
        this.healthProperties = healthProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(healthProperties.getHealth().getConnectTimeoutMs());
        requestFactory.setReadTimeout(healthProperties.getHealth().getReadTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public String getAccessToken() {
        requireCredentials();
        TokenCacheEntry current = cachedToken;
        Instant now = Instant.now();
        if (current != null && current.isValid(now, EXPIRY_SKEW_SECONDS)) {
            return current.accessToken();
        }
        synchronized (refreshLock) {
            current = cachedToken;
            if (current != null && current.isValid(Instant.now(), EXPIRY_SKEW_SECONDS)) {
                return current.accessToken();
            }
            TokenResponse token = fetchToken();
            cachedToken = TokenCacheEntry.from(token, Instant.now());
            return cachedToken.accessToken();
        }
    }

    void requireCredentials() {
        HealthProperties.OAuth oauth = oauthConfig();
        if (!StringUtils.hasText(oauth.getUsername()) || !StringUtils.hasText(oauth.getPassword())) {
            throw new ModelHubOAuthException(
                    "Model Hub OAuth username and password must be set in "
                            + "bredashboard.health.model-hub.oauth");
        }
    }

    private TokenResponse fetchToken() {
        HealthProperties.OAuth oauth = oauthConfig();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", oauth.getClientId());
        form.add("grant_type", oauth.getGrantType());
        form.add("username", oauth.getUsername());
        form.add("password", oauth.getPassword());
        form.add("scope", oauth.getScope());

        try {
            String body = restClient.post()
                    .uri(oauth.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return parseTokenResponse(body, objectMapper);
        } catch (RestClientException ex) {
            throw new ModelHubOAuthException("Model Hub OAuth token request failed: " + ex.getMessage(), ex);
        }
    }

    public static TokenResponse parseTokenResponse(String body, ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String accessToken = text(root, "access_token");
            if (!StringUtils.hasText(accessToken)) {
                throw new ModelHubOAuthException("OAuth token response missing access_token");
            }
            long expiresIn = root.path("expires_in").isNumber()
                    ? root.path("expires_in").asLong()
                    : DEFAULT_EXPIRES_IN_SECONDS;
            if (expiresIn <= 0) {
                expiresIn = DEFAULT_EXPIRES_IN_SECONDS;
            }
            return new TokenResponse(accessToken, expiresIn);
        } catch (ModelHubOAuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ModelHubOAuthException("Could not parse OAuth token response", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private HealthProperties.OAuth oauthConfig() {
        return healthProperties.getHealth().getModelHub().getOauth();
    }

    public record TokenResponse(String accessToken, long expiresIn) {
    }

    public record TokenCacheEntry(String accessToken, Instant expiresAt) {

        public static TokenCacheEntry from(TokenResponse token, Instant fetchedAt) {
            return new TokenCacheEntry(token.accessToken(), fetchedAt.plusSeconds(token.expiresIn()));
        }

        public boolean isValid(Instant now, int skewSeconds) {
            return expiresAt.isAfter(now.plusSeconds(skewSeconds));
        }
    }

    public static class ModelHubOAuthException extends RuntimeException {
        ModelHubOAuthException(String message) {
            super(message);
        }

        ModelHubOAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
