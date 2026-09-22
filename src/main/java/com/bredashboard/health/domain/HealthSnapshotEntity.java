package com.bredashboard.health.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "health_snapshots")
public class HealthSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int up;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "avg_response_time_ms", nullable = false)
    private int avgResponseTimeMs;

    protected HealthSnapshotEntity() {
    }

    public HealthSnapshotEntity(int total, int up, Instant recordedAt) {
        this(total, up, recordedAt, 0);
    }

    public HealthSnapshotEntity(int total, int up, Instant recordedAt, int avgResponseTimeMs) {
        this.total = total;
        this.up = up;
        this.recordedAt = recordedAt != null ? recordedAt : Instant.now();
        this.avgResponseTimeMs = Math.max(0, avgResponseTimeMs);
    }

    public Long getId() {
        return id;
    }

    public int getTotal() {
        return total;
    }

    public int getUp() {
        return up;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public int getAvgResponseTimeMs() {
        return avgResponseTimeMs;
    }
}
