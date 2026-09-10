package com.opsconsole.auth.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import com.opsconsole.auth.domain.AppRole;
import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.domain.RoleTabAccess;
import com.opsconsole.auth.repository.AppRoleRepository;
import com.opsconsole.auth.repository.AppUserRepository;
import com.opsconsole.auth.repository.RoleTabAccessRepository;
import com.opsconsole.config.OpsConsoleFeaturesProperties;
@Component
public class AuthDataInitializer implements ApplicationRunner {

    public static final String CODE_ADMINISTRATOR = "ADMIN";
    public static final String CODE_TESTER = "TESTER";
    public static final String CODE_MONITORING = "MONITORING";

    static final Map<String, String> SEED_PASSWORDS = Map.of(
            "admin@opsconsole.local", "Admin@123",
            "tester@opsconsole.local", "Tester@123",
            "monitoring@opsconsole.local", "Monitoring@123"
    );

    private final AppRoleRepository roleRepository;
    private final RoleTabAccessRepository tabAccessRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OpsConsoleFeaturesProperties features;

    public AuthDataInitializer(
            AppRoleRepository roleRepository,
            RoleTabAccessRepository tabAccessRepository,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            OpsConsoleFeaturesProperties features
    ) {
        this.roleRepository = roleRepository;
        this.tabAccessRepository = tabAccessRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.features = features;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateLegacyRoles();

        if (roleRepository.count() == 0) {
            AppRole admin = roleRepository.save(new AppRole(
                    CODE_ADMINISTRATOR, "Administrator", "Full platform access", true));
            AppRole tester = roleRepository.save(new AppRole(
                    CODE_TESTER, "Tester", "API testing and validation tools", true));
            AppRole monitoring = roleRepository.save(new AppRole(
                    CODE_MONITORING, "Monitoring", "Dashboard and system health monitoring", true));

            seedTabs(admin, EnumSet.allOf(AppTab.class));
            seedTabs(tester, EnumSet.of(AppTab.DASHBOARD, AppTab.HEALTH, AppTab.TESTER, AppTab.DEV_UTILS, AppTab.LOGS));
            seedTabs(monitoring, EnumSet.of(AppTab.DASHBOARD, AppTab.HEALTH, AppTab.TRANSACTIONS));

            userRepository.save(seedUser("dev-admin", "admin@opsconsole.local", "Administrator", admin));
            userRepository.save(seedUser("dev-tester", "tester@opsconsole.local", "Tester", tester));
            userRepository.save(seedUser("dev-monitoring", "monitoring@opsconsole.local", "Monitoring", monitoring));
        } else {
            if (tabAccessRepository.count() == 0) {
                for (AppRole role : roleRepository.findAll()) {
                    seedTabs(role, defaultTabsFor(role.getCode()));
                }
            }

            ensureDevUsers();
            backfillPasswords();
            ensureAllTabsRegistered();
        }

        if (!features.isApiTesterEnabled()) {
            revokeTab(AppTab.API_TESTER);
        }
    }

    private void migrateLegacyRoles() {
        renameRole("OPERATOR", CODE_TESTER, "Tester", "API testing and validation tools");
        renameRole("VIEWER", CODE_MONITORING, "Monitoring", "Dashboard and system health monitoring");
        updateRole(CODE_ADMINISTRATOR, "Administrator", "Full platform access");
        updateRole(CODE_TESTER, "Tester", "API testing and validation tools");
        updateRole(CODE_MONITORING, "Monitoring", "Dashboard and system health monitoring");
    }

    private void renameRole(String oldCode, String newCode, String name, String description) {
        roleRepository.findByCode(oldCode).ifPresent(role -> {
            if (!oldCode.equals(newCode)) {
                role.setCode(newCode);
            }
            role.setName(name);
            role.setDescription(description);
            roleRepository.save(role);
        });
    }

    private void updateRole(String code, String name, String description) {
        roleRepository.findByCode(code).ifPresent(role -> {
            role.setName(name);
            role.setDescription(description);
            roleRepository.save(role);
        });
    }

    private void ensureDevUsers() {
        List<DevUserSeed> seeds = List.of(
                new DevUserSeed("dev-admin", "admin@opsconsole.local", "Administrator", CODE_ADMINISTRATOR),
                new DevUserSeed("dev-operator", "operator@opsconsole.local", "Tester", CODE_TESTER),
                new DevUserSeed("dev-viewer", "viewer@opsconsole.local", "Monitoring", CODE_MONITORING),
                new DevUserSeed("dev-tester", "tester@opsconsole.local", "Tester", CODE_TESTER),
                new DevUserSeed("dev-monitoring", "monitoring@opsconsole.local", "Monitoring", CODE_MONITORING)
        );

        for (DevUserSeed seed : seeds) {
            userRepository.findByAzureAdId(seed.azureAdId()).ifPresentOrElse(user -> {
                user.setDisplayName(seed.displayName());
                roleRepository.findByCode(seed.roleCode()).ifPresent(user::setRole);
                userRepository.save(user);
            }, () -> {
                if (userRepository.findByEmailIgnoreCase(seed.email()).isEmpty()) {
                    roleRepository.findByCode(seed.roleCode()).ifPresent(role ->
                            userRepository.save(seedUser(seed.azureAdId(), seed.email(), seed.displayName(), role))
                    );
                }
            });
        }
    }

    private AppUser seedUser(String azureAdId, String email, String displayName, AppRole role) {
        String rawPassword = SEED_PASSWORDS.getOrDefault(email.toLowerCase(), "OpsConsole@123");
        return new AppUser(azureAdId, email, displayName, role, passwordEncoder.encode(rawPassword));
    }

    private void backfillPasswords() {
        Map<String, String> legacyPasswords = Map.of(
                "operator@opsconsole.local", "Operator@123",
                "viewer@opsconsole.local", "Viewer@123"
        );

        for (AppUser user : userRepository.findAll()) {
            if (!user.hasLocalPassword()) {
                String raw = SEED_PASSWORDS.get(user.getEmail().toLowerCase());
                if (raw == null) {
                    raw = legacyPasswords.get(user.getEmail().toLowerCase());
                }
                if (raw != null) {
                    user.setPasswordHash(passwordEncoder.encode(raw));
                    userRepository.save(user);
                }
            }
        }
    }

    static EnumSet<AppTab> defaultTabsFor(String roleCode) {
        return switch (roleCode) {
            case CODE_ADMINISTRATOR, "ADMINISTRATOR" -> EnumSet.allOf(AppTab.class);
            case CODE_TESTER, "OPERATOR" -> EnumSet.of(AppTab.DASHBOARD, AppTab.HEALTH, AppTab.TESTER, AppTab.DEV_UTILS, AppTab.LOGS);
            case CODE_MONITORING, "VIEWER" -> EnumSet.of(AppTab.DASHBOARD, AppTab.HEALTH, AppTab.TRANSACTIONS);
            default -> EnumSet.of(AppTab.DASHBOARD);
        };
    }

    private void ensureAllTabsRegistered() {
        for (AppRole role : roleRepository.findAll()) {
            EnumSet<AppTab> defaults = defaultTabsFor(role.getCode());
            for (AppTab tab : AppTab.values()) {
                RoleTabAccess entry = tabAccessRepository.findByRole_IdAndTab(role.getId(), tab);
                if (entry == null) {
                    tabAccessRepository.save(new RoleTabAccess(role, tab, defaults.contains(tab)));
                }
            }
        }
    }

    private void revokeTab(AppTab tab) {
        for (AppRole role : roleRepository.findAll()) {
            RoleTabAccess entry = tabAccessRepository.findByRole_IdAndTab(role.getId(), tab);
            if (entry != null && entry.isAllowed()) {
                entry.setAllowed(false);
                tabAccessRepository.save(entry);
            }
        }
    }

    private void seedTabs(AppRole role, EnumSet<AppTab> allowed) {
        for (AppTab tab : AppTab.values()) {
            RoleTabAccess entry = tabAccessRepository.findByRole_IdAndTab(role.getId(), tab);
            if (entry == null) {
                tabAccessRepository.save(new RoleTabAccess(role, tab, allowed.contains(tab)));
            } else {
                entry.setAllowed(allowed.contains(tab));
                tabAccessRepository.save(entry);
            }
        }
    }

    private record DevUserSeed(String azureAdId, String email, String displayName, String roleCode) {
    }
}
