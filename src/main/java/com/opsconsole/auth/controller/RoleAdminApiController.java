package com.opsconsole.auth.controller;

import com.opsconsole.auth.dto.UserActivityLogView;
import com.opsconsole.auth.dto.UserDetailResponse;
import com.opsconsole.auth.service.RoleAdminService;
import com.opsconsole.auth.service.UserActivityLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.domain.CurrentUser;
import com.opsconsole.auth.service.NavAccessService;
import com.opsconsole.auth.service.RoleAdminService;
@RestController
@RequestMapping("/api/admin")
public class RoleAdminApiController {

    private final RoleAdminService roleAdminService;
    private final NavAccessService navAccessService;
    private final UserActivityLogService userActivityLogService;

    public RoleAdminApiController(
            RoleAdminService roleAdminService,
            NavAccessService navAccessService,
            UserActivityLogService userActivityLogService
    ) {
        this.roleAdminService = roleAdminService;
        this.navAccessService = navAccessService;
        this.userActivityLogService = userActivityLogService;
    }

    @GetMapping("/users/{userId}")
    public UserDetailResponse getUser(@PathVariable Long userId) {
        requireUserAdminAccess();
        return UserDetailResponse.from(roleAdminService.getUser(userId));
    }

    @GetMapping("/users/{userId}/activity")
    public List<UserActivityLogView> userActivity(@PathVariable Long userId) {
        requireUserAdminAccess();
        roleAdminService.getUser(userId);
        return userActivityLogService.recentForUser(userId);
    }

    @PostMapping("/users")
    public ResponseEntity<CreatedUserResponse> createUser(@RequestBody RoleAdminService.CreateUserRequest body) {
        requireUserAdminAccess();
        AppUser actor = CurrentUser.requireUser();
        AppUser created = roleAdminService.createUser(body, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedUserResponse(created.getId(), created.getEmail(), created.getDisplayName()));
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long userId) {
        requireUserAdminAccess();
        roleAdminService.deleteUser(userId, CurrentUser.requireUser());
    }

    @PutMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUserProfile(
            @PathVariable Long userId,
            @RequestBody RoleAdminService.UpdateUserProfileRequest body
    ) {
        requireUserAdminAccess();
        roleAdminService.updateUserProfile(userId, body, CurrentUser.requireUser());
    }

    @PutMapping("/users/{userId}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUserRole(
            @PathVariable Long userId,
            @RequestBody RoleAdminService.UserRoleUpdateRequest body
    ) {
        requireUserAdminAccess();
        roleAdminService.updateUserRole(userId, body.roleCode(), CurrentUser.requireUser());
    }

    @PutMapping("/users/{userId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUserStatus(
            @PathVariable Long userId,
            @RequestBody RoleAdminService.UserStatusUpdateRequest body
    ) {
        requireUserAdminAccess();
        roleAdminService.updateUserEnabled(userId, body.enabled(), CurrentUser.requireUser());
    }

    @PutMapping("/users/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUserPassword(
            @PathVariable Long userId,
            @RequestBody RoleAdminService.PasswordUpdateRequest body
    ) {
        requireUserAdminAccess();
        roleAdminService.updateUserPassword(userId, body.password(), CurrentUser.requireUser());
    }

    @PutMapping("/roles/{roleId}/tabs")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRoleTabs(
            @PathVariable Long roleId,
            @RequestBody RoleAdminService.RoleTabsUpdateRequest body
    ) {
        requireUserAdminAccess();
        roleAdminService.updateRoleTabs(roleId, body.tabs(), CurrentUser.requireUser());
    }

    private void requireUserAdminAccess() {
        AppUser user = CurrentUser.requireUser();
        if (!navAccessService.canAccess(user, AppTab.USERS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User Admin access required");
        }
    }

    public record CreatedUserResponse(Long id, String email, String displayName) {
    }
}
