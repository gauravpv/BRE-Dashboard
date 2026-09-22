package com.bredashboard.health.domain;

import java.time.Instant;

public record SystemHealthView(
        Long id,
        String name,
        String host,
        int port,
        String environment,
        String region,
        HealthStatus status,
        String statusLabel,
        Instant lastChecked,
        long responseTimeMs,
        String errorMessage,
        String serviceUrl,
        String environmentId,
        long requests200,
        long requests500,
        String appVersion,
        String appName,
        Instant heartbeatUntil,
        String deploymentTier
) {
    public SystemHealthView(
            Long id,
            String name,
            String host,
            int port,
            String environment,
            String region,
            HealthStatus status,
            String statusLabel,
            Instant lastChecked,
            long responseTimeMs,
            String errorMessage
    ) {
        this(id, name, host, port, environment, region, status, statusLabel, lastChecked, responseTimeMs, errorMessage,
                null, null, 0L, 0L, null, null, null, HealthDeploymentTier.UAT.id());
    }

    public SystemHealthView(
            Long id,
            String name,
            String host,
            int port,
            String environment,
            String region,
            HealthStatus status,
            String statusLabel,
            Instant lastChecked,
            long responseTimeMs,
            String errorMessage,
            String deploymentTier
    ) {
        this(id, name, host, port, environment, region, status, statusLabel, lastChecked, responseTimeMs, errorMessage,
                null, null, 0L, 0L, null, null, null, deploymentTier);
    }

    public static SystemHealthView fromModelHub(
            Long id,
            String name,
            String host,
            int port,
            String environment,
            String region,
            HealthStatus status,
            String statusLabel,
            Instant lastChecked,
            String serviceUrl,
            String environmentId,
            long requests200,
            long requests500,
            String appVersion,
            String appName,
            Instant heartbeatUntil,
            String deploymentTier
    ) {
        return new SystemHealthView(
                id, name, host, port, environment, region, status, statusLabel, lastChecked, 0L, null,
                serviceUrl, environmentId, requests200, requests500, appVersion, appName, heartbeatUntil, deploymentTier
        );
    }

    public boolean fromModelHub() {
        return serviceUrl != null && environmentId != null;
    }

    public String subtitle() {
        if (environmentId != null && !environmentId.isBlank()) {
            return environmentId + " • " + region;
        }
        return environment + " • " + region;
    }
}
