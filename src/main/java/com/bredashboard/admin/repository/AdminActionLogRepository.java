package com.bredashboard.admin.repository;

import com.bredashboard.admin.domain.AdminAction;
import com.bredashboard.admin.domain.AdminActionLog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {

    List<AdminActionLog> findByActionNotOrderByCreatedAtDesc(AdminAction action, Pageable pageable);

    void deleteByAction(AdminAction action);
}
