package com.opsconsole.auth.repository;

import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.auth.domain.RoleTabAccess;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleTabAccessRepository extends JpaRepository<RoleTabAccess, Long> {

    List<RoleTabAccess> findByRoleIdOrderByTabAsc(Long roleId);

    List<RoleTabAccess> findByRoleIdAndAllowedTrue(Long roleId);

    RoleTabAccess findByRole_IdAndTab(Long roleId, AppTab tab);

    boolean existsByRoleIdAndTabAndAllowedTrue(Long roleId, AppTab tab);
}
