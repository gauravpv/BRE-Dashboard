package com.opsconsole.auth.service;

import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.domain.UserActivityAction;
import com.opsconsole.auth.domain.UserActivityLog;
import com.opsconsole.auth.dto.UserActivityLogView;
import com.opsconsole.auth.repository.UserActivityLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserActivityLogService {

    private final UserActivityLogRepository repository;

    public UserActivityLogService(UserActivityLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<UserActivityLogView> recentForUser(Long userId) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(UserActivityLogView::from)
                .toList();
    }

    @Transactional
    public void recordLogin(AppUser user, String signInMethod) {
        if (user == null || user.getId() == null) {
            return;
        }
        String detail = StringUtils.hasText(signInMethod)
                ? "Signed in via " + signInMethod
                : "Signed in";
        save(user.getId(), user.getId(), user.getDisplayName(), UserActivityAction.LOGIN, detail);
    }

    @Transactional
    public void recordLogin(AppUser user) {
        recordLogin(user, null);
    }

    @Transactional
    public void recordAzureProvision(AppUser user) {
        if (user == null || user.getId() == null) {
            return;
        }
        save(
                user.getId(),
                user.getId(),
                user.getDisplayName(),
                UserActivityAction.USER_CREATED,
                "Microsoft Entra ID access request created with Pending status"
        );
    }

    @Transactional
    public void recordAccessDenied(AppUser user, String reason) {
        if (user == null || user.getId() == null) {
            return;
        }
        save(user.getId(), user.getId(), user.getDisplayName(), UserActivityAction.ACCESS_DENIED, reason);
    }

    @Transactional
    public void recordApproved(AppUser actor, AppUser target) {
        save(
                target.getId(),
                actor != null ? actor.getId() : null,
                actorLabel(actor),
                UserActivityAction.ACCESS_APPROVED,
                "Access approved with role " + target.getRole().getName()
        );
    }

    @Transactional
    public void recordSessionRevoked(AppUser actor, AppUser target, String reason) {
        save(
                target.getId(),
                actor != null ? actor.getId() : null,
                actorLabel(actor),
                UserActivityAction.SESSION_REVOKED,
                reason
        );
    }

    @Transactional
    public void recordCreated(AppUser actor, AppUser created) {
        if (created == null || created.getId() == null) {
            return;
        }
        save(
                created.getId(),
                actor != null ? actor.getId() : null,
                actorLabel(actor),
                UserActivityAction.USER_CREATED,
                "Account created with role " + created.getRole().getName()
        );
    }

    @Transactional
    public void recordDeleted(AppUser actor, AppUser deleted) {
        if (deleted == null || deleted.getId() == null) {
            return;
        }
        save(
                deleted.getId(),
                actor != null ? actor.getId() : null,
                actorLabel(actor),
                UserActivityAction.USER_DELETED,
                "Account deleted"
        );
    }

    @Transactional
    public void recordRoleChanged(AppUser actor, AppUser target, String previousRoleName, String newRoleName) {
        if (target == null || target.getId() == null || previousRoleName.equals(newRoleName)) {
            return;
        }
        save(
                target.getId(),
                actor != null ? actor.getId() : null,
                actorLabel(actor),
                UserActivityAction.ROLE_CHANGED,
                "Role changed from " + previousRoleName + " to " + newRoleName
        );
    }

    @Transactional
    public void recordStatusChanged(AppUser actor, AppUser target, boolean enabled) {
        if (target == null || target.getId() == null) {
            return;
        }
        save(
                target.getId(),
                actor != null ? actor.getId() : null,
                actorLabel(actor),
                UserActivityAction.STATUS_CHANGED,
                enabled ? "Account activated" : "Account set to inactive"
        );
    }

    @Transactional
    public void recordProfileUpdated(AppUser actor, AppUser target, String detail) {
        if (target == null || target.getId() == null) {
            return;
        }
        save(
                target.getId(),
                actor != null ? actor.getId() : null,
                actorLabel(actor),
                UserActivityAction.PROFILE_UPDATED,
                detail
        );
    }

    private void save(
            Long userId,
            Long actorUserId,
            String actorDisplayName,
            UserActivityAction action,
            String detail
    ) {
        repository.save(new UserActivityLog(userId, actorUserId, actorDisplayName, action, detail));
    }

    private static String actorLabel(AppUser actor) {
        return actor != null ? actor.getDisplayName() : "System";
    }
}
