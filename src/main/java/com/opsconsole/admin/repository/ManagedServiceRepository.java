package com.opsconsole.admin.repository;

import com.opsconsole.admin.domain.ManagedService;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ManagedServiceRepository extends JpaRepository<ManagedService, Long> {

    @Query("""
            SELECT s FROM ManagedService s
            JOIN FETCH s.server srv
            WHERE s.enabled = true AND srv.enabled = true
            ORDER BY srv.name ASC, s.category ASC, s.name ASC
            """)
    List<ManagedService> findByEnabledTrueWithServer();

    List<ManagedService> findByServer_IdAndEnabledTrueOrderByCategoryAscNameAsc(Long serverId);

    List<ManagedService> findByEnabledTrueOrderByCategoryAscNameAsc();

    Optional<ManagedService> findByIdAndEnabledTrue(Long id);
}
