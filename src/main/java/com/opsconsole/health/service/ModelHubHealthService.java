package com.opsconsole.health.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsconsole.health.config.HealthProperties;
import com.opsconsole.health.domain.HealthDeploymentTier;
import com.opsconsole.health.domain.HealthStatus;
import com.opsconsole.health.domain.SystemHealthView;
import com.opsconsole.health.dto.ModelHubEnvironmentOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ModelHubHealthService {

    private static final Logger log = LoggerFactory.getLogger(ModelHubHealthService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HealthProperties healthProperties;
    private final ModelHubOAuthTokenClient oauthTokenClient;

    public ModelHubHealthService(
            ObjectMapper objectMapper,
            HealthProperties healthProperties,
            ModelHubOAuthTokenClient oauthTokenClient
    ) {
        this.objectMapper = objectMapper;
        this.healthProperties = healthProperties;
        this.oauthTokenClient = oauthTokenClient;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(healthProperties.getHealth().getConnectTimeoutMs());
        requestFactory.setReadTimeout(healthProperties.getHealth().getReadTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isEnabled() {
        return healthProperties.getModelHub().isEnabled();
    }

    public List<SystemHealthView> fetchAll(HealthDeploymentTier tier) {
        if (!isEnabled()) {
            return List.of();
        }

        Instant fetchedAt = Instant.now();
        List<HubBoundEnvironment> environments = fetchEnvironmentsFromAllHubs(tier);
        List<SystemHealthView> views = new ArrayList<>();
        String tierId = tier.id();

        for (HubBoundEnvironment bound : environments) {
            try {
                views.addAll(fetchInstances(bound.baseUrl(), bound.environment(), fetchedAt, tier));
            } catch (Exception ex) {
                log.warn("Failed to fetch {} instances for environment {} from {}: {}",
                        tierId, bound.environment().environmentId(), bound.baseUrl(), ex.getMessage());
            }
        }

        views.sort(Comparator
                .comparing(SystemHealthView::environmentId, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(SystemHealthView::name, String::compareToIgnoreCase));
        return views;
    }

    List<EnvironmentEntry> fetchEnvironments(HealthDeploymentTier tier) {
        return fetchEnvironmentsFromAllHubs(tier).stream()
                .map(HubBoundEnvironment::environment)
                .toList();
    }

    private List<HubBoundEnvironment> fetchEnvironmentsFromAllHubs(HealthDeploymentTier tier) {
        List<String> hubs = listBaseUrls(tier);
        if (hubs.isEmpty()) {
            throw new ModelHubFetchException("No Model Hub base URLs configured for " + tier.id());
        }

        List<HubBoundEnvironment> environments = new ArrayList<>();
        int failures = 0;
        for (String baseUrl : hubs) {
            try {
                String url = baseUrl + "/deployment/v1/environments?page=0&size=100&sort=position,environmentId,asc";
                for (EnvironmentEntry environment : parseEnvironments(get(url))) {
                    environments.add(new HubBoundEnvironment(baseUrl, environment));
                }
            } catch (Exception ex) {
                failures++;
                log.warn("Failed to list Model Hub environments from {}: {}", baseUrl, ex.getMessage());
            }
        }
        if (environments.isEmpty() && failures == hubs.size()) {
            throw new ModelHubFetchException("All Model Hub URLs failed for " + tier.id());
        }
        return environments;
    }

    public List<ModelHubEnvironmentOption> listEnvironments(HealthDeploymentTier tier) {
        if (!isEnabled()) {
            return List.of();
        }
        try {
            return fetchEnvironments(tier).stream()
                    .map(entry -> new ModelHubEnvironmentOption(entry.environmentId(), entry.label(), entry.tag()))
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to list Model Hub environments for {}: {}", tier.id(), ex.getMessage());
            return List.of();
        }
    }

    public Map<String, String> environmentTagsById(HealthDeploymentTier tier) {
        return listEnvironments(tier).stream()
                .collect(Collectors.toMap(
                        entry -> entry.environmentId().toLowerCase(Locale.ROOT),
                        entry -> entry.tag() != null && !entry.tag().isBlank() ? entry.tag() : entry.label(),
                        (left, right) -> left
                ));
    }

    public Optional<String> resolveEnvironmentTag(String environmentId, HealthDeploymentTier tier) {
        if (environmentId == null || environmentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(environmentTagsById(tier).get(environmentId.toLowerCase(Locale.ROOT)));
    }

    List<SystemHealthView> fetchInstances(
            String baseUrl,
            EnvironmentEntry environment,
            Instant fetchedAt,
            HealthDeploymentTier tier
    ) {
        String url = baseUrl + "/deployment/v1/environments/" + environment.environmentId() + "/instances";
        return parseInstances(get(url), environment, fetchedAt, tier);
    }

    public List<EnvironmentEntry> parseEnvironments(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("content");
            if (!content.isArray()) {
                return List.of();
            }
            List<EnvironmentEntry> environments = new ArrayList<>();
            for (JsonNode item : content) {
                JsonNode data = item.path("data");
                String environmentId = text(data, "environmentId");
                if (environmentId == null || environmentId.isBlank()) {
                    continue;
                }
                String label = Optional.ofNullable(text(data, "label")).orElse(environmentId);
                String tag = firstTagName(data.path("tags"));
                environments.add(new EnvironmentEntry(environmentId, label, tag));
            }
            return environments;
        } catch (Exception ex) {
            throw new ModelHubFetchException("Could not parse environments response", ex);
        }
    }

    public List<SystemHealthView> parseInstances(String body, EnvironmentEntry environment, Instant fetchedAt, HealthDeploymentTier tier) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("content");
            if (!content.isArray()) {
                return List.of();
            }
            List<SystemHealthView> views = new ArrayList<>();
            for (JsonNode item : content) {
                JsonNode data = item.path("data");
                SystemHealthView view = toView(data, environment, fetchedAt, tier);
                if (view != null) {
                    views.add(view);
                }
            }
            return views;
        } catch (Exception ex) {
            throw new ModelHubFetchException("Could not parse instances response for " + environment.environmentId(), ex);
        }
    }

    SystemHealthView toView(JsonNode data, EnvironmentEntry environment, Instant fetchedAt, HealthDeploymentTier tier) {
        String instanceId = text(data, "id");
        String name = text(data, "name");
        String serviceUrl = text(data, "url");
        if (name == null || serviceUrl == null) {
            return null;
        }

        URI uri = parseUri(serviceUrl);
        String host = uri != null ? uri.getHost() : serviceUrl;
        int port = uri != null && uri.getPort() > 0 ? uri.getPort() : 80;

        Instant heartbeatUntil = parseInstant(text(data, "validUntil"));
        HealthStatus status = resolveStatus(heartbeatUntil, fetchedAt);

        JsonNode metrics = data.path("metrics");
        long requests200 = metricCount(metrics, "http.server.requests.status.200");
        long requests500 = metricCount(metrics, "http.server.requests.status.500");

        String appName = text(data, "appName");
        String appVersion = text(data, "appVersion");
        String region = environment.tag() != null ? environment.tag() : environment.label();

        return SystemHealthView.fromModelHub(
                stableId(instanceId),
                name,
                host,
                port,
                resolveEnvironmentLabel(environment),
                region,
                status,
                status.displayLabel(),
                fetchedAt,
                serviceUrl,
                environment.environmentId(),
                requests200,
                requests500,
                appVersion,
                appName,
                heartbeatUntil,
                tier.id()
        );
    }

    private static HealthStatus resolveStatus(Instant heartbeatUntil, Instant now) {
        if (heartbeatUntil == null) {
            return HealthStatus.UNKNOWN;
        }
        return now.isBefore(heartbeatUntil) ? HealthStatus.UP : HealthStatus.DOWN;
    }

    private static long metricCount(JsonNode metrics, String key) {
        JsonNode count = metrics.path(key).path("measurements").path("COUNT");
        if (count.isMissingNode() || count.isNull()) {
            return 0L;
        }
        return count.isNumber() ? count.asLong() : 0L;
    }

    private static String firstTagName(JsonNode tags) {
        if (!tags.isArray() || tags.isEmpty()) {
            return null;
        }
        return text(tags.get(0), "name");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static URI parseUri(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Long stableId(String instanceId) {
        if (instanceId == null) {
            return null;
        }
        return (long) instanceId.hashCode();
    }

    private static String resolveEnvironmentLabel(EnvironmentEntry environment) {
        String id = environment.environmentId().toLowerCase(Locale.ROOT);
        if (id.contains("prod") && !id.contains("uat")) {
            return "Production";
        }
        if (id.contains("uat") || id.contains("test")) {
            return "UAT";
        }
        return environment.label();
    }

    private String get(String url) {
        try {
            String accessToken = oauthTokenClient.getAccessToken();
            return restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
        } catch (ModelHubOAuthTokenClient.ModelHubOAuthException ex) {
            throw new ModelHubFetchException(ex.getMessage(), ex);
        } catch (RestClientException ex) {
            throw new ModelHubFetchException("Request failed for " + url + ": " + ex.getMessage(), ex);
        }
    }

    List<String> listBaseUrls(HealthDeploymentTier tier) {
        HealthProperties.HubTarget target = tier == HealthDeploymentTier.PROD
                ? healthProperties.getModelHub().getProd()
                : healthProperties.getModelHub().getUat();
        return target.resolvedBaseUrls();
    }

    public record EnvironmentEntry(String environmentId, String label, String tag) {
    }

    private record HubBoundEnvironment(String baseUrl, EnvironmentEntry environment) {
    }

    static class ModelHubFetchException extends RuntimeException {
        ModelHubFetchException(String message, Throwable cause) {
            super(message, cause);
        }

        ModelHubFetchException(String message) {
            super(message);
        }
    }
}
