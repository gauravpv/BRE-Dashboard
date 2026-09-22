package com.bredashboard.admin.service;

import com.bredashboard.admin.domain.AdminAction;
import com.bredashboard.admin.domain.AdminActionLog;
import com.bredashboard.admin.domain.AdminActionStatus;
import com.bredashboard.admin.domain.ManagedService;
import com.bredashboard.admin.domain.SshCommandResult;
import com.bredashboard.admin.repository.AdminActionLogRepository;
import com.bredashboard.admin.util.SshOutputFormatter;
import com.bredashboard.auth.domain.AppUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminActionLogger {

    private final AdminActionLogRepository actionLogRepository;

    public AdminActionLogger(AdminActionLogRepository actionLogRepository) {
        this.actionLogRepository = actionLogRepository;
    }

    @Transactional
    public void log(AppUser actor, ManagedService service, AdminAction action, SshCommandResult result) {
        actionLogRepository.save(new AdminActionLog(
                actor.getId(),
                actor.getDisplayName(),
                service.getId(),
                service.getName(),
                action,
                result.success() ? AdminActionStatus.SUCCESS : AdminActionStatus.FAILED,
                SshOutputFormatter.summarize(result)
        ));
    }
}
