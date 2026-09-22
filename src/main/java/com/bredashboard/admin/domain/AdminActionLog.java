package com.bredashboard.admin.domain;

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
@Table(name = "admin_action_logs")
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_display_name", nullable = false, length = 200)
    private String actorDisplayName;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "service_name", nullable = false, length = 120)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminActionStatus status;

    @Column(length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AdminActionLog() {
    }

    public AdminActionLog(
            Long actorUserId,
            String actorDisplayName,
            Long serviceId,
            String serviceName,
            AdminAction action,
            AdminActionStatus status,
            String message
    ) {
        this.actorUserId = actorUserId;
        this.actorDisplayName = actorDisplayName;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.action = action;
        this.status = status;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorDisplayName() {
        return actorDisplayName;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public AdminAction getAction() {
        return action;
    }

    public AdminActionStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
