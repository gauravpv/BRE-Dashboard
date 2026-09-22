package com.bredashboard.web.service;

import com.bredashboard.activity.domain.ActivityEvent;
import com.bredashboard.activity.service.ActivityFeedService;
import com.bredashboard.web.dto.DashboardIncidentView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private static final int RECENT_INCIDENTS_LIMIT = 8;

    private final ActivityFeedService activityFeedService;

    public DashboardService(ActivityFeedService activityFeedService) {
        this.activityFeedService = activityFeedService;
    }

    @Transactional(readOnly = true)
    public List<DashboardIncidentView> recentIncidents() {
        return recentIncidents(RECENT_INCIDENTS_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<DashboardIncidentView> recentIncidents(int limit) {
        return activityFeedService.recentHealthIncidents(limit).stream()
                .map(DashboardService::toIncident)
                .toList();
    }

    private static DashboardIncidentView toIncident(ActivityEvent event) {
        return new DashboardIncidentView(
                event.messageHighlight(),
                event.detail(),
                event.occurredAt(),
                "P1"
        );
    }
}
