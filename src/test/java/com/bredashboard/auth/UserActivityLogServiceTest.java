package com.bredashboard.auth;

import com.bredashboard.auth.domain.AppRole;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.UserActivityAction;
import com.bredashboard.auth.repository.AppRoleRepository;
import com.bredashboard.auth.repository.AppUserRepository;
import com.bredashboard.auth.repository.UserActivityLogRepository;
import com.bredashboard.auth.service.AuthDataInitializer;
import com.bredashboard.auth.service.UserActivityLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserActivityLogServiceTest {

    @Autowired
    private UserActivityLogService userActivityLogService;

    @Autowired
    private UserActivityLogRepository userActivityLogRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Test
    void recordLogin_storesMicrosoftEntraDetail() {
        AppUser user = userRepository.findByAzureAdId("dev-admin").orElseThrow();

        userActivityLogService.recordLogin(user, "Microsoft Entra ID");

        assertThat(userActivityLogRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId()))
                .anyMatch(log -> log.getAction() == UserActivityAction.LOGIN
                        && log.getDetail().contains("Microsoft Entra ID"));
    }

    @Test
    void recordAzureProvision_storesEntraAccountCreation() {
        AppRole role = roleRepository.findByCode(AuthDataInitializer.CODE_MONITORING).orElseThrow();
        AppUser user = userRepository.save(new AppUser(
                "azure-test-" + System.currentTimeMillis(),
                "azure-test-" + System.currentTimeMillis() + "@bredashboard.local",
                "Azure Test User",
                role
        ));

        userActivityLogService.recordAzureProvision(user);

        assertThat(userActivityLogRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId()))
                .anyMatch(log -> log.getAction() == UserActivityAction.USER_CREATED
                        && log.getDetail().contains("Microsoft Entra ID"));
    }
}
