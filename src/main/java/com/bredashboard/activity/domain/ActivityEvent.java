package com.bredashboard.activity.domain;

import java.time.Instant;

public record ActivityEvent(
        ActivityType type,
        String icon,
        String iconBgClass,
        String iconColorClass,
        String messagePrefix,
        String messageHighlight,
        String messageSuffix,
        String detail,
        Instant occurredAt
) {
}
