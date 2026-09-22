package com.bredashboard.web.dto;

import java.time.Instant;

public record DashboardIncidentView(
        String serviceName,
        String detail,
        Instant occurredAt,
        String severityLabel
) {
}
