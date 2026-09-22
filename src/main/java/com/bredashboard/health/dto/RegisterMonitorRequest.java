package com.bredashboard.health.dto;

public record RegisterMonitorRequest(
        String tier,
        String name,
        String host,
        Integer port,
        String environment,
        String region,
        String actuatorPath,
        String modelHubEnvironmentId
) {
}
