package com.bredashboard.health.service;

import com.bredashboard.health.config.HealthProperties;
import com.bredashboard.health.domain.HealthStatus;
import com.bredashboard.health.domain.MonitoredHost;
import com.bredashboard.health.domain.MonitoredHostProd;
import com.bredashboard.health.domain.SystemHealthView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

@Service
public class ActuatorHealthService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HealthProperties healthProperties;

    public ActuatorHealthService(ObjectMapper objectMapper, HealthProperties healthProperties) {
        this.objectMapper = objectMapper;
        this.healthProperties = healthProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(healthProperties.getHealth().getConnectTimeoutMs());
        requestFactory.setReadTimeout(healthProperties.getHealth().getReadTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public SystemHealthView check(MonitoredHost host, String deploymentTier) {
        return checkHost(
                host.getId(),
                host.getName(),
                host.getHost(),
                host.getPort(),
                host.getEnvironment(),
                host.getRegion(),
                host.getActuatorPath(),
                host.baseUrl(),
                deploymentTier
        );
    }

    public SystemHealthView checkProd(MonitoredHostProd host, String deploymentTier) {
        return checkHost(
                host.getId(),
                host.getName(),
                host.getHost(),
                host.getPort(),
                host.getEnvironment(),
                host.getRegion(),
                host.getActuatorPath(),
                host.baseUrl(),
                deploymentTier
        );
    }

    private SystemHealthView checkHost(
            Long id,
            String name,
            String hostAddress,
            int port,
            String environment,
            String region,
            String actuatorPath,
            String baseUrl,
            String deploymentTier
    ) {
        Instant started = Instant.now();
        long startNanos = System.nanoTime();
        String probePath = resolveProbePath(actuatorPath);

        try {
            HealthStatus status = probe(baseUrl, probePath);
            return buildView(id, name, hostAddress, port, environment, region, status, null, started, startNanos, deploymentTier);
        } catch (Exception primaryError) {
            String fallbackPath = "/actuator/health";
            if (!fallbackPath.equals(probePath)) {
                try {
                    HealthStatus status = probe(baseUrl, fallbackPath);
                    return buildView(id, name, hostAddress, port, environment, region, status, null, started, startNanos, deploymentTier);
                } catch (Exception fallbackError) {
                    return buildView(id, name, hostAddress, port, environment, region, HealthStatus.DOWN,
                            describeError(fallbackError), started, startNanos, deploymentTier);
                }
            }
            return buildView(id, name, hostAddress, port, environment, region, HealthStatus.DOWN,
                    describeError(primaryError), started, startNanos, deploymentTier);
        }
    }

    public static String resolveProbePath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return "/actuator/health";
        }
        String path = configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
        if ("/actuator".equals(path) || path.endsWith("/actuator")) {
            return "/actuator/health";
        }
        return path;
    }

    private HealthStatus probe(String baseUrl, String path) throws Exception {
        String url = baseUrl + path;
        String body = restClient.get().uri(url).retrieve().body(String.class);
        if (body == null || body.isBlank()) {
            throw new RestClientException("Empty response from " + url);
        }
        return parseStatus(baseUrl, body);
    }

    HealthStatus parseStatus(String baseUrl, String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (root.has("status")) {
            return HealthStatus.fromActuator(root.get("status").asText());
        }
        if (baseUrl != null) {
            JsonNode healthLink = root.path("_links").path("health");
            if (healthLink.has("href")) {
                String healthPath = toProbePath(baseUrl, healthLink.get("href").asText());
                return probe(baseUrl, healthPath);
            }
        }
        throw new RestClientException("No status field in actuator response");
    }

    private static String toProbePath(String baseUrl, String href) {
        try {
            URI uri = URI.create(href);
            String path = uri.getPath();
            if (path != null && !path.isBlank()) {
                return path;
            }
        } catch (IllegalArgumentException ignored) {
            // use href as path below
        }
        if (href.startsWith(baseUrl)) {
            return href.substring(baseUrl.length());
        }
        return href.startsWith("/") ? href : "/" + href;
    }

    private static String describeError(Exception error) {
        if (error instanceof RestClientResponseException response) {
            return "HTTP " + response.getStatusCode().value() + ": " + response.getStatusText();
        }
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private SystemHealthView buildView(
            Long id,
            String name,
            String host,
            int port,
            String environment,
            String region,
            HealthStatus status,
            String errorMessage,
            Instant started,
            long startNanos,
            String deploymentTier
    ) {
        long responseMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        return new SystemHealthView(
                id,
                name,
                host,
                port,
                environment,
                region,
                status,
                status.displayLabel(),
                started,
                responseMs,
                errorMessage,
                deploymentTier
        );
    }

    public int refreshSeconds() {
        return healthProperties.getHealth().getRefreshSeconds();
    }

    /** Test helper for JSON parsing without HTTP. */
    public HealthStatus parseStatus(String body) throws Exception {
        return parseStatus((String) null, body);
    }
}
