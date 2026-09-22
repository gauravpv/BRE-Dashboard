package com.bredashboard.activity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "system_activity_logs")
public class SystemActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ActivityType type;

    @Column(nullable = false, length = 64)
    private String icon;

    @Column(name = "icon_bg_class", nullable = false, length = 120)
    private String iconBgClass;

    @Column(name = "icon_color_class", nullable = false, length = 120)
    private String iconColorClass;

    @Column(name = "message_prefix", nullable = false, length = 500)
    private String messagePrefix;

    @Column(name = "message_highlight", nullable = false, length = 500)
    private String messageHighlight;

    @Column(name = "message_suffix", length = 500)
    private String messageSuffix;

    @Column(length = 1000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected SystemActivityLog() {
    }

    public SystemActivityLog(
            ActivityType type,
            String icon,
            String iconBgClass,
            String iconColorClass,
            String messagePrefix,
            String messageHighlight,
            String messageSuffix,
            String detail,
            Instant createdAt
    ) {
        this.type = type;
        this.icon = icon;
        this.iconBgClass = iconBgClass;
        this.iconColorClass = iconColorClass;
        this.messagePrefix = messagePrefix;
        this.messageHighlight = messageHighlight;
        this.messageSuffix = messageSuffix;
        this.detail = detail;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public ActivityEvent toEvent() {
        return new ActivityEvent(
                type,
                icon,
                iconBgClass,
                iconColorClass,
                messagePrefix,
                messageHighlight,
                messageSuffix != null ? messageSuffix : "",
                detail != null ? detail : "",
                createdAt
        );
    }

    public Long getId() {
        return id;
    }

    public ActivityType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
