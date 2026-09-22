package com.bredashboard.health.domain;

import com.bredashboard.health.exception.MonitorRegistrationException;

public enum HealthDeploymentTier {
    UAT,
    PROD;

    public String id() {
        return name();
    }

    public static HealthDeploymentTier fromId(String value) {
        if (value == null || value.isBlank()) {
            throw new MonitorRegistrationException("Deployment tier is required (UAT or PROD)");
        }
        return switch (value.trim().toUpperCase()) {
            case "UAT" -> UAT;
            case "PROD", "PRODUCTION" -> PROD;
            default -> throw new MonitorRegistrationException("Unknown deployment tier: " + value);
        };
    }

    public static HealthDeploymentTier fromPathSegment(String segment) {
        return fromId(segment);
    }
}
