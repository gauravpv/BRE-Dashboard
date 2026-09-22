package com.bredashboard.auth.repository;

import com.bredashboard.auth.domain.AppRole;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {

    Optional<AppRole> findByCode(String code);
}
