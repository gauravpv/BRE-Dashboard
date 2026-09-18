package com.opsconsole.health.controller;

import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.activity.service.ActivityFeedService;
import com.opsconsole.health.service.ActuatorHealthService;
import com.opsconsole.health.domain.HealthRegionGroup;
import com.opsconsole.health.service.SystemHealthMonitor;
import com.opsconsole.health.domain.SystemHealthView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
@Controller
public class HealthPageController {

    private static final int ACTIVITY_LIMIT = 20;

    private final SystemHealthMonitor healthMonitor;
    private final ActuatorHealthService healthService;
    private final ActivityFeedService activityFeedService;

    public HealthPageController(
            SystemHealthMonitor healthMonitor,
            ActuatorHealthService healthService,
            ActivityFeedService activityFeedService
    ) {
        this.healthMonitor = healthMonitor;
        this.healthService = healthService;
        this.activityFeedService = activityFeedService;
    }

    @GetMapping("/health")
    public String systemHealth(@RequestParam(name = "status", required = false) String status, Model model) {
        healthMonitor.refreshIfStale();
        List<SystemHealthView> systems = healthMonitor.getSystems();
        model.addAttribute("activeNav", AppTab.HEALTH.id());
        model.addAttribute("systems", systems);
        model.addAttribute("systemGroups", HealthRegionGroup.fromSystems(systems));
        model.addAttribute("summary", healthMonitor.summary());
        model.addAttribute("refreshSeconds", healthService.refreshSeconds());
        model.addAttribute("lastRefreshedAt", healthMonitor.getLastRefreshedAt());
        model.addAttribute("modelHubEnabled", healthMonitor.isModelHubEnabled());
        model.addAttribute("environmentIds", systems.stream()
                .map(SystemHealthView::environmentId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .sorted()
                .toList());
        model.addAttribute("activities", activityFeedService.recent(ACTIVITY_LIMIT));
        if ("UP".equalsIgnoreCase(status) || "DOWN".equalsIgnoreCase(status)) {
            model.addAttribute("initialStatusFilter", status.toUpperCase());
        }
        return "system-health";
    }
}
