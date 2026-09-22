package com.bredashboard.health.domain;

public enum HealthStatus {
    UP,
    DOWN,
    DEGRADED,
    UNKNOWN;

    public static HealthStatus fromActuator(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        return switch (raw.trim().toUpperCase()) {
            case "UP" -> UP;
            case "DOWN" -> DOWN;
            case "OUT_OF_SERVICE" -> DEGRADED;
            default -> UNKNOWN;
        };
    }

    public String displayLabel() {
        return name();
    }
}
