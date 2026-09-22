package com.bredashboard.admin.controller;

import com.bredashboard.admin.service.ServiceAdminService;
import com.bredashboard.admin.util.AdminActionLabels;
import com.bredashboard.auth.domain.AppTab;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.CurrentUser;
import com.bredashboard.auth.service.NavAccessService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class ServiceAdminApiController {

    private final ServiceAdminService serviceAdminService;
    private final NavAccessService navAccessService;

    public ServiceAdminApiController(ServiceAdminService serviceAdminService, NavAccessService navAccessService) {
        this.serviceAdminService = serviceAdminService;
        this.navAccessService = navAccessService;
    }

    @GetMapping("/servers")
    public List<ServiceAdminService.ServerView> listServers() {
        requireSystemAdminAccess();
        return serviceAdminService.listServers();
    }

    @GetMapping("/services/{id}")
    public ServiceAdminService.ServiceDetailView getService(@PathVariable Long id) {
        requireSystemAdminAccess();
        return serviceAdminService.getServiceDetail(id);
    }

    @PostMapping("/services/{id}/start")
    public OperationResponse start(@PathVariable Long id) {
        requireSystemAdminAccess();
        AppUser actor = CurrentUser.requireUser();
        ServiceAdminService.OperationResult result = serviceAdminService.startService(id, actor);
        return OperationResponse.from(result);
    }

    @PostMapping("/services/{id}/stop")
    public OperationResponse stop(@PathVariable Long id) {
        requireSystemAdminAccess();
        AppUser actor = CurrentUser.requireUser();
        ServiceAdminService.OperationResult result = serviceAdminService.stopService(id, actor);
        return OperationResponse.from(result);
    }

    @PostMapping("/services/{id}/restart")
    public OperationResponse restart(@PathVariable Long id) {
        requireSystemAdminAccess();
        AppUser actor = CurrentUser.requireUser();
        ServiceAdminService.OperationResult result = serviceAdminService.restartService(id, actor);
        return OperationResponse.from(result);
    }

    @GetMapping("/services/{id}/properties")
    public PropertiesResponse getProperties(@PathVariable Long id) {
        requireSystemAdminAccess();
        ServiceAdminService.PropertiesContent content = serviceAdminService.readProperties(id);
        return new PropertiesResponse(content.content());
    }

    @PutMapping("/services/{id}/properties")
    public OperationResponse putProperties(@PathVariable Long id, @RequestBody PropertiesRequest body) {
        requireSystemAdminAccess();
        AppUser actor = CurrentUser.requireUser();
        ServiceAdminService.OperationResult result = serviceAdminService.writeProperties(id, actor, body.content());
        return OperationResponse.from(result);
    }

    @GetMapping("/actions")
    public List<ActionLogResponse> recentActions(@RequestParam(defaultValue = "20") int limit) {
        requireSystemAdminAccess();
        return serviceAdminService.recentActions(limit).stream()
                .map(log -> new ActionLogResponse(
                        log.getId(),
                        log.getActorDisplayName(),
                        log.getServiceName(),
                        AdminActionLabels.label(log.getAction()),
                        log.getStatus().name(),
                        log.getMessage(),
                        log.getCreatedAt()
                ))
                .toList();
    }

    private void requireSystemAdminAccess() {
        AppUser user = CurrentUser.requireUser();
        if (!navAccessService.canAccess(user, AppTab.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "System Admin access required");
        }
    }

    public record PropertiesRequest(String content) {
    }

    public record PropertiesResponse(String content) {
    }

    public record OperationResponse(boolean success, String message) {
        static OperationResponse from(ServiceAdminService.OperationResult result) {
            return new OperationResponse(result.success(), result.message());
        }
    }

    public record ActionLogResponse(
            Long id,
            String actorDisplayName,
            String serviceName,
            String action,
            String status,
            String message,
            Instant createdAt
    ) {
    }
}
