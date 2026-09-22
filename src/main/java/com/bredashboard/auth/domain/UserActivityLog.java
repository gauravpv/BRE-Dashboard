package com.bredashboard.auth.domain;

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
@Table(name = "user_activity_logs")
public class UserActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_display_name", nullable = false, length = 200)
    private String actorDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UserActivityAction action;

    @Column(length = 2000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserActivityLog() {
    }

    public UserActivityLog(
            Long userId,
            Long actorUserId,
            String actorDisplayName,
            UserActivityAction action,
            String detail
    ) {
        this.userId = userId;
        this.actorUserId = actorUserId;
        this.actorDisplayName = actorDisplayName;
        this.action = action;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorDisplayName() {
        return actorDisplayName;
    }

    public UserActivityAction getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
