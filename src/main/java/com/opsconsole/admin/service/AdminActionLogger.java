package com.opsconsole.admin.service;

import com.opsconsole.admin.domain.AdminAction;
import com.opsconsole.admin.domain.AdminActionLog;
import com.opsconsole.admin.domain.AdminActionStatus;
import com.opsconsole.admin.domain.ManagedService;
import com.opsconsole.admin.domain.SshCommandResult;
import com.opsconsole.admin.repository.AdminActionLogRepository;
import com.opsconsole.admin.util.SshOutputFormatter;
import com.opsconsole.auth.domain.AppUser;

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
