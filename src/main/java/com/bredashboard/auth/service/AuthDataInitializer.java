package com.bredashboard.auth.service;

import com.bredashboard.auth.config.AuthProperties;
import com.bredashboard.auth.domain.AppRole;
import com.bredashboard.auth.domain.AppTab;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.RoleTabAccess;
import com.bredashboard.auth.repository.AppRoleRepository;
import com.bredashboard.auth.repository.AppUserRepository;
import com.bredashboard.auth.repository.RoleTabAccessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Component
public class AuthDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthDataInitializer.class);

    public static final String CODE_ADMINISTRATOR = "ADMIN";
    public static final String CODE_TESTER = "TESTER";
    public static final String CODE_MONITORING = "MONITORING";

    static final Map<String, String> SEED_PASSWORDS = Map.of(
            "admin@bredashboard.local", "Admin@123",
            "tester@bredashboard.local", "Tester@123",
            "monitoring@bredashboard.local", "Monitoring@123"
    );

    private final AppRoleRepository roleRepository;
    private final RoleTabAccessRepository tabAccessRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public AuthDataInitializer(
            AppRoleRepository roleRepository,
            RoleTabAccessRepository tabAccessRepository,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties authProperties
    ) {
        this.roleRepository = roleRepository;
        this.tabAccessRepository = tabAccessRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateLegacyRoles();
        ensureSystemRoles();

        if (authProperties.isSeedDevUsers()) {
            ensureDevUsers();
            backfillPasswords();
        }

        ensureBootstrapAdministrator();
        ensureAllTabsRegistered();
    }

    private void ensureSystemRoles() {
        AppRole admin = roleRepository.findByCode(CODE_ADMINISTRATOR).orElseGet(() ->
                roleRepository.save(new AppRole(CODE_ADMINISTRATOR, "Administrator", "Full platform access", true)));
        AppRole tester = roleRepository.findByCode(CODE_TESTER).orElseGet(() ->
                roleRepository.save(new AppRole(CODE_TESTER, "Tester", "API testing and validation tools", true)));
        AppRole monitoring = roleRepository.findByCode(CODE_MONITORING).orElseGet(() ->
                roleRepository.save(new AppRole(CODE_MONITORING, "Monitoring", "Dashboard and system health monitoring", true)));

        if (tabAccessRepository.count() == 0) {
            seedTabs(admin, defaultTabsFor(CODE_ADMINISTRATOR));
            seedTabs(tester, defaultTabsFor(CODE_TESTER));
            seedTabs(monitoring, defaultTabsFor(CODE_MONITORING));
        }
    }

    private void ensureBootstrapAdministrator() {
        if (userRepository.countByRole_Code(CODE_ADMINISTRATOR) > 0) {
            return;
        }
        String email = authProperties.getBootstrapEmail() == null ? "" : authProperties.getBootstrapEmail().trim().toLowerCase();
        String password = authProperties.getBootstrapPassword();
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.warn("No Administrator exists and bootstrap credentials are not set. Set BRE_DASHBOARD_BOOTSTRAP_EMAIL and BRE_DASHBOARD_BOOTSTRAP_PASSWORD.");
            return;
        }
        if (password.length() < 8) {
            throw new IllegalStateException("BRE_DASHBOARD_BOOTSTRAP_PASSWORD must be at least 8 characters");
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            log.warn("Bootstrap email {} already exists but is not an Administrator", email);
            return;
        }
        AppRole admin = roleRepository.findByCode(CODE_ADMINISTRATOR).orElseThrow();
        String displayName = StringUtils.hasText(authProperties.getBootstrapDisplayName())
                ? authProperties.getBootstrapDisplayName().trim()
                : "Administrator";
        userRepository.save(new AppUser("bootstrap-admin", email, displayName, admin, passwordEncoder.encode(password)));
        log.info("Created bootstrap Administrator {}", email);
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
                new DevUserSeed("dev-admin", "admin@bredashboard.local", "Administrator", CODE_ADMINISTRATOR),
                new DevUserSeed("dev-operator", "operator@bredashboard.local", "Tester", CODE_TESTER),
                new DevUserSeed("dev-viewer", "viewer@bredashboard.local", "Monitoring", CODE_MONITORING),
                new DevUserSeed("dev-tester", "tester@bredashboard.local", "Tester", CODE_TESTER),
                new DevUserSeed("dev-monitoring", "monitoring@bredashboard.local", "Monitoring", CODE_MONITORING)
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
        String rawPassword = SEED_PASSWORDS.getOrDefault(email.toLowerCase(), "BreDashboard@123");
        return new AppUser(azureAdId, email, displayName, role, passwordEncoder.encode(rawPassword));
    }

    private void backfillPasswords() {
        Map<String, String> legacyPasswords = Map.of(
                "operator@bredashboard.local", "Operator@123",
                "viewer@bredashboard.local", "Viewer@123"
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
            case CODE_TESTER, "OPERATOR" -> EnumSet.of(AppTab.DASHBOARD, AppTab.HEALTH, AppTab.TESTER, AppTab.DEV_UTILS);
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
