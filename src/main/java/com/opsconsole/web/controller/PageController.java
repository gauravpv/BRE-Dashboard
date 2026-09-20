package com.opsconsole.web.controller;

import com.opsconsole.activity.service.ActivityFeedService;
import com.opsconsole.admin.domain.AdminActionLog;
import com.opsconsole.admin.service.ServiceAdminService;
import com.opsconsole.admin.util.AdminActionLabels;
import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.health.service.SystemHealthMonitor;
import com.opsconsole.web.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class PageController {

    private static final int DASHBOARD_ACTIVITY_LIMIT = 20;
    private static final DateTimeFormatter ACTION_TIME_FMT = DateTimeFormatter
            .ofPattern("d MMM yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final SystemHealthMonitor healthMonitor;
    private final ActivityFeedService activityFeedService;
    private final DashboardService dashboardService;
    private final ServiceAdminService serviceAdminService;

    public PageController(
            SystemHealthMonitor healthMonitor,
            ActivityFeedService activityFeedService,
            DashboardService dashboardService,
            ServiceAdminService serviceAdminService
    ) {
        this.healthMonitor = healthMonitor;
        this.activityFeedService = activityFeedService;
        this.dashboardService = dashboardService;
        this.serviceAdminService = serviceAdminService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        healthMonitor.refreshIfStale();
        model.addAttribute("summary", healthMonitor.summary());
        model.addAttribute("servicesUpChart", healthMonitor.servicesUpChart());
        model.addAttribute("apiSuccess", healthMonitor.apiSuccess());
        model.addAttribute("responseTimeChart", healthMonitor.responseTimeChart());
        model.addAttribute("activities", activityFeedService.recent(DASHBOARD_ACTIVITY_LIMIT));
        model.addAttribute("recentIncidents", dashboardService.recentIncidents());
        return page("dashboard", AppTab.DASHBOARD, model);
    }

    @GetMapping("/admin")
    public String systemAdmin(
            @RequestParam(required = false) Long serverId,
            @RequestParam(required = false) Long serviceId,
            Model model
    ) {
        List<ServiceAdminService.ServerView> servers = serviceAdminService.listServers();
        ServiceAdminService.ServiceDetailView selectedService = resolveSelectedService(servers, serverId, serviceId);
        List<AdminActionLog> recentActions = serviceAdminService.recentActions(20);
        model.addAttribute("servers", servers);
        model.addAttribute("selectedService", selectedService);
        model.addAttribute("selectedServiceId", selectedService != null ? selectedService.id() : null);
        model.addAttribute("recentActions", recentActions);
        model.addAttribute("actionTimeFmt", ACTION_TIME_FMT);
        model.addAttribute("actionLabels", AdminActionLabels.class);
        return page("system-admin", AppTab.ADMIN, model);
    }

    private ServiceAdminService.ServiceDetailView resolveSelectedService(
            List<ServiceAdminService.ServerView> servers,
            Long serverId,
            Long serviceId
    ) {
        if (servers.isEmpty()) {
            return null;
        }
        if (serviceId != null) {
            try {
                return serviceAdminService.getServiceDetail(serviceId);
            } catch (IllegalArgumentException ignored) {
                // fall through to default selection
            }
        }
        ServiceAdminService.ServerView server = servers.stream()
                .filter(s -> serverId == null || serverId.equals(s.id()))
                .findFirst()
                .orElse(servers.get(0));
        if (server.services().isEmpty()) {
            return null;
        }
        return serviceAdminService.getServiceDetail(server.services().get(0).id());
    }

    @GetMapping("/dev-utils")
    public String developerUtils(Model model) {
        return page("developer-utils", AppTab.DEV_UTILS, model);
    }

    private String page(String view, AppTab nav, Model model) {
        model.addAttribute("activeNav", nav.id());
        return view;
    }
}
