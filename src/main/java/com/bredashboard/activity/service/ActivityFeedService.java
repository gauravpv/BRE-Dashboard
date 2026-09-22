package com.bredashboard.activity.service;

import com.bredashboard.activity.domain.ActivityEvent;
import com.bredashboard.activity.domain.ActivityType;
import com.bredashboard.activity.domain.SystemActivityLog;
import com.bredashboard.activity.repository.SystemActivityLogRepository;
import com.bredashboard.admin.domain.AdminAction;
import com.bredashboard.auth.domain.AppRole;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.health.domain.HealthStatus;
import com.bredashboard.health.domain.SystemHealthView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ActivityFeedService {

    private final SystemActivityLogRepository repository;

    public ActivityFeedService(SystemActivityLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordLogin(AppUser user) {
        if (user == null) {
            return;
        }
        add(event(
                ActivityType.USER_LOGIN,
                "login",
                "bg-surface-container-high",
                "text-on-surface-variant",
                "User signed in:",
                user.getDisplayName(),
                "",
                user.getEmail()
        ));
    }

    @Transactional
    public void recordHealthStatusChange(SystemHealthView system, HealthStatus previous, HealthStatus current) {
        if (system == null || previous == null || current == null || previous == current) {
            return;
        }
        if (current == HealthStatus.UP) {
            add(event(
                    ActivityType.HEALTH_UP,
                    "check_circle",
                    "bg-green-500/10",
                    "text-green-700",
                    "Service is UP:",
                    system.name(),
                    "",
                    system.subtitle()
            ));
        } else if (current == HealthStatus.DOWN) {
            String suffix = system.errorMessage() != null && !system.errorMessage().isBlank()
                    ? " — " + system.errorMessage()
                    : "";
            add(event(
                    ActivityType.HEALTH_DOWN,
                    "error",
                    "bg-error/10",
                    "text-error",
                    "Service is DOWN:",
                    system.name(),
                    suffix,
                    system.subtitle()
            ));
        }
    }

    @Transactional
    public void recordUserRoleChanged(AppUser actor, AppUser target, String previousRoleName, String newRoleName) {
        if (target == null || previousRoleName == null || newRoleName == null
                || previousRoleName.equals(newRoleName)) {
            return;
        }
        String actorLabel = actorLabel(actor);
        add(event(
                ActivityType.USER_ROLE_CHANGED,
                "manage_accounts",
                "bg-surface-container-high",
                "text-on-surface-variant",
                actorLabel + " changed role for",
                target.getDisplayName(),
                " to " + newRoleName,
                "was " + previousRoleName
        ));
    }

    @Transactional
    public void recordUserStatusChanged(AppUser actor, AppUser target, boolean enabled) {
        if (target == null) {
            return;
        }
        String actorLabel = actorLabel(actor);
        add(event(
                ActivityType.USER_STATUS_CHANGED,
                enabled ? "person" : "person_off",
                "bg-surface-container-high",
                "text-on-surface-variant",
                actorLabel + (enabled ? " activated " : " suspended "),
                target.getDisplayName(),
                "",
                target.getEmail()
        ));
    }

    @Transactional
    public void recordRoleTabsChanged(AppUser actor, AppRole role) {
        if (role == null) {
            return;
        }
        String actorLabel = actorLabel(actor);
        add(event(
                ActivityType.ROLE_TABS_CHANGED,
                "tune",
                "bg-surface-container-high",
                "text-on-surface-variant",
                actorLabel + " updated tab access for",
                role.getName(),
                " role",
                role.getCode()
        ));
    }

    @Transactional
    public void recordServiceControl(AppUser actor, String serviceName, AdminAction action, boolean success) {
        if (serviceName == null || serviceName.isBlank()) {
            return;
        }
        String verb = switch (action) {
            case START -> "started";
            case STOP -> "stopped";
            case RESTART -> "restarted";
            default -> action.name().toLowerCase();
        };
        add(event(
                ActivityType.SERVICE_CONTROL,
                success ? "play_circle" : "error",
                success ? "bg-green-500/10" : "bg-red-500/10",
                success ? "text-green-700" : "text-red-700",
                actorLabel(actor) + " " + verb + " service",
                serviceName,
                success ? "" : " (failed)",
                ""
        ));
    }

    @Transactional
    public void recordServicePropertiesUpdated(AppUser actor, String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return;
        }
        add(event(
                ActivityType.PROPS_UPDATED,
                "edit_note",
                "bg-surface-container-high",
                "text-on-surface-variant",
                actorLabel(actor) + " updated properties for",
                serviceName,
                "",
                ""
        ));
    }

    @Transactional(readOnly = true)
    public List<ActivityEvent> recent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return repository.findByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(SystemActivityLog::toEvent)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityEvent> recentHealthIncidents(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return repository.findByTypeOrderByCreatedAtDesc(ActivityType.HEALTH_DOWN, PageRequest.of(0, limit)).stream()
                .map(SystemActivityLog::toEvent)
                .toList();
    }

    private static ActivityEvent event(
            ActivityType type,
            String icon,
            String iconBgClass,
            String iconColorClass,
            String messagePrefix,
            String messageHighlight,
            String messageSuffix,
            String detail
    ) {
        return new ActivityEvent(
                type,
                icon,
                iconBgClass,
                iconColorClass,
                messagePrefix,
                messageHighlight,
                messageSuffix,
                detail,
                Instant.now()
        );
    }

    private void add(ActivityEvent event) {
        repository.save(new SystemActivityLog(
                event.type(),
                event.icon(),
                event.iconBgClass(),
                event.iconColorClass(),
                event.messagePrefix(),
                event.messageHighlight(),
                event.messageSuffix(),
                event.detail(),
                event.occurredAt()
        ));
    }

    private static String actorLabel(AppUser actor) {
        return actor != null ? actor.getDisplayName() : "System";
    }
}
