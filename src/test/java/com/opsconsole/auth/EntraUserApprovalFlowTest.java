package com.opsconsole.auth;

import com.opsconsole.auth.domain.AccountStatus;
import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.repository.AppUserRepository;
import com.opsconsole.auth.service.AuthDataInitializer;
import com.opsconsole.auth.service.NavAccessService;
import com.opsconsole.auth.service.RoleAdminService;
import com.opsconsole.auth.service.UserProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class EntraUserApprovalFlowTest {

    @Autowired
    private UserProvisioningService provisioningService;

    @Autowired
    private RoleAdminService roleAdminService;

    @Autowired
    private NavAccessService navAccessService;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void firstEntraSignIn_isPendingUntilAdminAssignsRoleAndApproves() {
        var entraUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OIDC_USER")),
                Map.of(
                        "oid", "entra-pending-oid",
                        "preferred_username", "pending.user@bajajfinserv.in",
                        "name", "Pending User"
                ),
                "oid"
        );

        AppUser pending = provisioningService.provisionFromOAuth(entraUser);

        assertThat(pending.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(pending.getLastLoginAt()).isNull();
        assertThat(navAccessService.canAccess(pending, AppTab.DASHBOARD)).isFalse();

        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        roleAdminService.approveUser(pending.getId(), AuthDataInitializer.CODE_MONITORING, admin);

        AppUser approved = userRepository.findById(pending.getId()).orElseThrow();
        assertThat(approved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(approved.getRole().getCode()).isEqualTo(AuthDataInitializer.CODE_MONITORING);
        assertThat(navAccessService.canAccess(approved, AppTab.HEALTH)).isTrue();
        assertThat(navAccessService.canAccess(approved, AppTab.ADMIN)).isFalse();
    }

    @Test
    void pendingAccount_cannotBeActivatedWithoutApprovalRole() {
        var entraUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OIDC_USER")),
                Map.of(
                        "oid", "entra-direct-activation-oid",
                        "preferred_username", "direct.activation@bajajfinserv.in",
                        "name", "Direct Activation"
                ),
                "oid"
        );
        AppUser pending = provisioningService.provisionFromOAuth(entraUser);
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();

        assertThatThrownBy(() -> roleAdminService.updateUserEnabled(pending.getId(), true, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approved with a role");
    }
}
