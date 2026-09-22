package com.bredashboard.health.repository;

import com.bredashboard.health.domain.HealthSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface HealthSnapshotRepository extends JpaRepository<HealthSnapshotEntity, Long> {

    List<HealthSnapshotEntity> findByRecordedAtAfterOrderByRecordedAtAsc(Instant after);

    void deleteByRecordedAtBefore(Instant before);
}
