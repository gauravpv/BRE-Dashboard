package com.bredashboard.health.dto;

import com.bredashboard.health.domain.SystemHealthView;
import com.bredashboard.health.service.SystemHealthMonitor;

import java.time.Instant;
import java.util.List;

public record HealthRefreshResponse(
        List<SystemHealthView> systems,
        SystemHealthMonitor.HealthSummary summary,
        Instant lastRefreshedAt
) {
}
