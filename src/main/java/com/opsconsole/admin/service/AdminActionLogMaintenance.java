package com.opsconsole.admin.service;

import com.opsconsole.admin.domain.AdminAction;
import com.opsconsole.admin.repository.AdminActionLogRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminActionLogMaintenance implements ApplicationRunner {

    private final AdminActionLogRepository actionLogRepository;

    public AdminActionLogMaintenance(AdminActionLogRepository actionLogRepository) {
        this.actionLogRepository = actionLogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        actionLogRepository.deleteByAction(AdminAction.PROPS_READ);
    }
}
