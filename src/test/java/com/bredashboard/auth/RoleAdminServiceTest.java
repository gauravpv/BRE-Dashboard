package com.bredashboard.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.BreUserPrincipal;
import com.bredashboard.auth.repository.AppUserRepository;
import com.bredashboard.auth.service.AuthDataInitializer;
import com.bredashboard.auth.service.RoleAdminService;
import com.bredashboard.auth.service.UserSessionService;
@SpringBootTest
@Transactional
class RoleAdminServiceTest {

    @Autowired
    private RoleAdminService roleAdminService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private UserSessionService userSessionService;

    @Test
    void createUser_persistsWithPassword() {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        AppUser created = roleAdminService.createUser(new RoleAdminService.CreateUserRequest(
                "New Tester",
                "new-tester@bredashboard.local",
                null,
                AuthDataInitializer.CODE_TESTER,
                "TestPass@1",
                "QA",
                true
        ), admin);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getEmail()).isEqualTo("new-tester@bredashboard.local");
        assertThat(created.hasLocalPassword()).isTrue();
        assertThat(created.getJobTitle()).isEqualTo("QA");
    }

    @Test
    void createUser_rejectsDuplicateEmail() {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        roleAdminService.createUser(new RoleAdminService.CreateUserRequest(
                "Dup",
                "dup@bredashboard.local",
                null,
                AuthDataInitializer.CODE_MONITORING,
                "TestPass@1",
                null,
                true
        ), admin);

        assertThatThrownBy(() -> roleAdminService.createUser(new RoleAdminService.CreateUserRequest(
                "Dup 2",
                "dup@bredashboard.local",
                null,
                AuthDataInitializer.CODE_MONITORING,
                "TestPass@2",
                null,
                true
        ), admin)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void deleteUser_removesAccount() {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        AppUser created = roleAdminService.createUser(new RoleAdminService.CreateUserRequest(
                "To Delete",
                "delete-me@bredashboard.local",
                "local-delete-test",
                AuthDataInitializer.CODE_MONITORING,
                "TestPass@1",
                null,
                true
        ), admin);

        roleAdminService.deleteUser(created.getId(), admin);

        assertThat(userRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void deleteUser_cannotDeleteSelf() {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();

        assertThatThrownBy(() -> roleAdminService.deleteUser(admin.getId(), admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own account");
    }

    @Test
    void revokeSessions_expiresTheUsersActiveSession() {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        String sessionId = "admin-session-test";
        sessionRegistry.registerNewSession(sessionId, BreUserPrincipal.fromUser(admin));

        assertThat(userSessionService.activeSessionCount(admin.getId())).isEqualTo(1);
        assertThat(userSessionService.revokeAll(admin.getId())).isEqualTo(1);
        assertThat(userSessionService.activeSessionCount(admin.getId())).isZero();
    }
}
