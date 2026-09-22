package com.bredashboard.health.controller;

import com.bredashboard.auth.domain.AppTab;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.CurrentUser;
import com.bredashboard.auth.service.NavAccessService;
import com.bredashboard.common.dto.ErrorResponse;
import com.bredashboard.health.domain.HealthDeploymentTier;
import com.bredashboard.health.domain.SystemHealthView;
import com.bredashboard.health.dto.HealthRefreshResponse;
import com.bredashboard.health.dto.ModelHubEnvironmentOption;
import com.bredashboard.health.dto.MonitorDetailsResponse;
import com.bredashboard.health.dto.RegisterMonitorRequest;
import com.bredashboard.health.exception.MonitorRegistrationException;
import com.bredashboard.health.service.ModelHubHealthService;
import com.bredashboard.health.service.MonitorRegistrationService;
import com.bredashboard.health.service.SystemHealthMonitor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/health")
public class HealthApiController {

    private final SystemHealthMonitor healthMonitor;
    private final MonitorRegistrationService registrationService;
    private final ModelHubHealthService modelHubHealthService;
    private final NavAccessService navAccessService;

    public HealthApiController(
            SystemHealthMonitor healthMonitor,
            MonitorRegistrationService registrationService,
            ModelHubHealthService modelHubHealthService,
            NavAccessService navAccessService
    ) {
        this.healthMonitor = healthMonitor;
        this.registrationService = registrationService;
        this.modelHubHealthService = modelHubHealthService;
        this.navAccessService = navAccessService;
    }

    @GetMapping("/systems")
    public List<SystemHealthView> systemsApi() {
        requireHealthAccess();
        healthMonitor.refreshIfStale();
        return healthMonitor.getSystems();
    }

    @PostMapping("/refresh")
    public HealthRefreshResponse refreshNow() {
        requireHealthAccess();
        healthMonitor.refresh();
        return new HealthRefreshResponse(
                healthMonitor.getSystems(),
                healthMonitor.summary(),
                healthMonitor.getLastRefreshedAt()
        );
    }

    @GetMapping("/monitors")
    public List<MonitorDetailsResponse> listMonitors() {
        requireHealthAccess();
        return registrationService.listAll();
    }

    @PostMapping("/monitors")
    public HealthRefreshResponse registerMonitor(@RequestBody RegisterMonitorRequest request) {
        requireHealthAccess();
        return registrationService.register(request);
    }

    @GetMapping("/monitors/{tier}/{id}")
    public MonitorDetailsResponse monitorDetails(@PathVariable String tier, @PathVariable Long id) {
        requireHealthAccess();
        return registrationService.get(HealthDeploymentTier.fromPathSegment(tier), id);
    }

    @PutMapping("/monitors/{tier}/{id}")
    public HealthRefreshResponse updateMonitor(
            @PathVariable String tier,
            @PathVariable Long id,
            @RequestBody RegisterMonitorRequest request
    ) {
        requireHealthAccess();
        return registrationService.update(HealthDeploymentTier.fromPathSegment(tier), id, request);
    }

    @DeleteMapping("/monitors/{tier}/{id}")
    public HealthRefreshResponse removeMonitor(@PathVariable String tier, @PathVariable Long id) {
        requireHealthAccess();
        return registrationService.remove(HealthDeploymentTier.fromPathSegment(tier), id);
    }

    @GetMapping("/environments/{tier}")
    public List<ModelHubEnvironmentOption> environmentsForTier(@PathVariable String tier) {
        requireHealthAccess();
        return modelHubHealthService.listEnvironments(HealthDeploymentTier.fromPathSegment(tier));
    }

    @GetMapping("/environments")
    public List<ModelHubEnvironmentOption> environmentsApi() {
        requireHealthAccess();
        return modelHubHealthService.listEnvironments(HealthDeploymentTier.UAT);
    }

    @ExceptionHandler(MonitorRegistrationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ErrorResponse handleRegistrationError(MonitorRegistrationException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    private void requireHealthAccess() {
        AppUser user = CurrentUser.userOrNull();
        if (user == null) {
            return;
        }
        navAccessService.require(user, AppTab.HEALTH);
    }
}
