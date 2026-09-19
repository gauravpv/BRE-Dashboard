package com.opsconsole.health.repository;

import com.opsconsole.health.domain.MonitoredHost;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitoredHostRepository extends JpaRepository<MonitoredHost, Long> {

    List<MonitoredHost> findByEnabledTrueOrderByNameAsc();

    List<MonitoredHost> findAllByOrderByNameAsc();

    boolean existsByHostAndPort(String host, int port);

    boolean existsByHostAndPortAndIdNot(String host, int port, Long id);
}
