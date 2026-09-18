package com.opsconsole.auth.service;

import com.opsconsole.activity.service.ActivityFeedService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.opsconsole.auth.domain.AppRole;
import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.domain.RoleTabAccess;
import com.opsconsole.auth.repository.AppRoleRepository;
import com.opsconsole.auth.repository.AppUserRepository;
import com.opsconsole.auth.repository.RoleTabAccessRepository;
@Service
public class RoleAdminService {

    private final AppRoleRepository roleRepository;
    private final RoleTabAccessRepository tabAccessRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityFeedService activityFeedService;
    private final UserActivityLogService userActivityLogService;

    public RoleAdminService(
            AppRoleRepository roleRepository,
            RoleTabAccessRepository tabAccessRepository,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ActivityFeedService activityFeedService,
            UserActivityLogService userActivityLogService
    ) {
        this.roleRepository = roleRepository;
        this.tabAccessRepository = tabAccessRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityFeedService = activityFeedService;
        this.userActivityLogService = userActivityLogService;
    }

    @Transactional(readOnly = true)
    public AppUser getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional(readOnly = true)
    public List<AppUser> allUsers() {
        return userRepository.findAllByOrderByDisplayNameAsc();
    }

    @Transactional(readOnly = true)
    public List<AppRole> allRoles() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<RoleTabAccessView> tabMatrixForRole(Long roleId) {
        return tabAccessRepository.findByRoleIdOrderByTabAsc(roleId).stream()
                .map(rta -> new RoleTabAccessView(rta.getTab(), rta.isAllowed()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, Map<String, Boolean>> tabMatrixForAllRoles() {
        Map<Long, Map<String, Boolean>> result = new LinkedHashMap<>();
        for (AppRole role : roleRepository.findAll()) {
            Map<String, Boolean> flags = new LinkedHashMap<>();
            for (AppTab tab : AppTab.values()) {
                flags.put(tab.id(), false);
            }
            result.put(role.getId(), flags);
        }
        for (RoleTabAccess access : tabAccessRepository.findAll()) {
            Map<String, Boolean> flags = result.get(access.getRole().getId());
            if (flags != null) {
                flags.put(access.getTab().id(), access.isAllowed());
            }
        }
        return result;
    }

    @Transactional
    public void updateUserRole(Long userId, String roleCode, AppUser actor) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String previousRoleName = user.getRole().getName();
        AppRole role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        user.setRole(role);
        userRepository.save(user);
        activityFeedService.recordUserRoleChanged(actor, user, previousRoleName, role.getName());
        userActivityLogService.recordRoleChanged(actor, user, previousRoleName, role.getName());
    }

    @Transactional
    public void updateUserProfile(Long userId, UpdateUserProfileRequest request, AppUser actor) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String displayName = request.displayName() == null ? "" : request.displayName().trim();
        if (!StringUtils.hasText(displayName)) {
            throw new IllegalArgumentException("Display name is required");
        }

        StringBuilder changes = new StringBuilder();
        if (!displayName.equals(user.getDisplayName())) {
            changes.append("Display name updated");
        }

        String jobTitle = StringUtils.hasText(request.jobTitle()) ? request.jobTitle().trim() : null;
        String previousJobTitle = user.getJobTitle();
        if ((jobTitle == null && previousJobTitle != null) || (jobTitle != null && !jobTitle.equals(previousJobTitle))) {
            if (!changes.isEmpty()) {
                changes.append("; ");
            }
            changes.append("Job title updated");
        }

        user.setDisplayName(displayName);
        user.setJobTitle(jobTitle);
        userRepository.save(user);

        if (!changes.isEmpty()) {
            userActivityLogService.recordProfileUpdated(actor, user, changes.toString());
        }
    }

    @Transactional
    public void updateUserEnabled(Long userId, boolean enabled, AppUser actor) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        boolean wasEnabled = user.isEnabled();
        user.setEnabled(enabled);
        userRepository.save(user);
        if (wasEnabled != enabled) {
            activityFeedService.recordUserStatusChanged(actor, user, enabled);
            userActivityLogService.recordStatusChanged(actor, user, enabled);
        }
    }

    @Transactional
    public AppUser createUser(CreateUserRequest request, AppUser actor) {
        String email = normalizeEmail(request.email());
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email is required");
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("Email is already in use");
        }

        String azureAdId = resolveAzureAdId(request.azureAdId(), email);
        if (userRepository.findByAzureAdId(azureAdId).isPresent()) {
            throw new IllegalArgumentException("Azure AD ID is already in use");
        }

        String displayName = request.displayName() == null ? "" : request.displayName().trim();
        if (!StringUtils.hasText(displayName)) {
            throw new IllegalArgumentException("Display name is required");
        }

        String password = request.password();
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        AppRole role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        AppUser user = new AppUser(
                azureAdId,
                email,
                displayName,
                role,
                passwordEncoder.encode(password)
        );
        if (StringUtils.hasText(request.jobTitle())) {
            user.setJobTitle(request.jobTitle().trim());
        }
        user.setEnabled(request.enabled() == null || request.enabled());
        AppUser saved = userRepository.save(user);
        userActivityLogService.recordCreated(actor, saved);
        return saved;
    }

    @Transactional
    public void deleteUser(Long userId, AppUser actor) {
        if (userId.equals(actor.getId())) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (AuthDataInitializer.CODE_ADMINISTRATOR.equals(user.getRole().getCode())) {
            long adminCount = userRepository.countByRole_Code(AuthDataInitializer.CODE_ADMINISTRATOR);
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot delete the last Administrator account");
            }
        }

        userActivityLogService.recordDeleted(actor, user);
        userRepository.delete(user);
    }

    @Transactional
    public void updateUserPassword(Long userId, String rawPassword, AppUser actor) {
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        userActivityLogService.recordStatusChanged(actor, user, user.isEnabled());
    }

    @Transactional
    public void changeOwnPassword(AppUser actor, String currentPassword, String newPassword) {
        AppUser user = userRepository.findById(actor.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.hasLocalPassword() || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void updateRoleTabs(Long roleId, Map<String, Boolean> tabAccess, AppUser actor) {
        AppRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        Map<AppTab, RoleTabAccess> existing = new LinkedHashMap<>();
        for (RoleTabAccess entry : tabAccessRepository.findByRoleIdOrderByTabAsc(roleId)) {
            existing.put(entry.getTab(), entry);
        }

        List<RoleTabAccess> toSave = new ArrayList<>();
        for (AppTab tab : AppTab.values()) {
            boolean allowed = Boolean.TRUE.equals(tabAccess.get(tab.id()));
            RoleTabAccess entry = existing.get(tab);
            if (entry == null) {
                toSave.add(new RoleTabAccess(role, tab, allowed));
            } else {
                entry.setAllowed(allowed);
                toSave.add(entry);
            }
        }
        tabAccessRepository.saveAll(toSave);
        activityFeedService.recordRoleTabsChanged(actor, role);
    }

    public record RoleTabAccessView(AppTab tab, boolean allowed) {
    }

    public record RoleTabsUpdateRequest(Map<String, Boolean> tabs) {
    }

    public record UserRoleUpdateRequest(String roleCode) {
    }

    public record UserStatusUpdateRequest(boolean enabled) {
    }

    public record UpdateUserProfileRequest(String displayName, String jobTitle) {
    }

    public record PasswordUpdateRequest(String password) {
    }

    public record CreateUserRequest(
            String displayName,
            String email,
            String azureAdId,
            String roleCode,
            String password,
            String jobTitle,
            Boolean enabled
    ) {
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static String resolveAzureAdId(String azureAdId, String email) {
        if (StringUtils.hasText(azureAdId)) {
            return azureAdId.trim();
        }
        return "local-" + UUID.randomUUID();
    }
}
