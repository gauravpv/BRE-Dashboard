package com.opsconsole.admin.repository;

import com.opsconsole.admin.domain.AdminAction;
import com.opsconsole.admin.domain.AdminActionLog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {

    List<AdminActionLog> findByActionNotOrderByCreatedAtDesc(AdminAction action, Pageable pageable);

    void deleteByAction(AdminAction action);
}
