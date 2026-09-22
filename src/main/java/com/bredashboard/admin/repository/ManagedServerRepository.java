package com.bredashboard.admin.repository;

import com.bredashboard.admin.domain.ManagedServer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManagedServerRepository extends JpaRepository<ManagedServer, Long> {

    Optional<ManagedServer> findByName(String name);

    List<ManagedServer> findByEnabledTrueOrderByNameAsc();
}
