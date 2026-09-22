package com.bredashboard.health.service;

import com.bredashboard.activity.service.ActivityFeedService;
import com.bredashboard.health.config.HealthProperties;
import com.bredashboard.health.domain.HealthDeploymentTier;
import com.bredashboard.health.domain.HealthStatus;
import com.bredashboard.health.domain.MonitoredHost;
import com.bredashboard.health.domain.MonitoredHostProd;
import com.bredashboard.health.domain.SystemHealthView;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SystemHealthMonitor {

    private final MonitoredHostCatalog hostCatalog;
    private final ActuatorHealthService healthService;
    private final ModelHubHealthService modelHubHealthService;
    private final ModelHubWatchlistService modelHubWatchlistService;
    private final ActivityFeedService activityFeedService;
    private final HealthHistoryService healthHistoryService;
    private final HealthProperties healthProperties;
    private final Object refreshLock = new Object();
    private final AtomicReference<List<SystemHealthView>> latest =
            new AtomicReference<>(Collections.emptyList());
    private final Map<String, HealthStatus> previousStatuses = new ConcurrentHashMap<>();
    private volatile Instant lastRefreshedAt;

    public SystemHealthMonitor(
            MonitoredHostCatalog hostCatalog,
            ActuatorHealthService healthService,
            ModelHubHealthService modelHubHealthService,
            ModelHubWatchlistService modelHubWatchlistService,
            ActivityFeedService activityFeedService,
            HealthHistoryService healthHistoryService,
            HealthProperties healthProperties
    ) {
        this.hostCatalog = hostCatalog;
        this.healthService = healthService;
        this.modelHubHealthService = modelHubHealthService;
        this.modelHubWatchlistService = modelHubWatchlistService;
        this.activityFeedService = activityFeedService;
        this.healthHistoryService = healthHistoryService;
        this.healthProperties = healthProperties;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedRateString = "${bredashboard.health.refresh-interval-ms:30000}")
    public void scheduledRefresh() {
        refresh();
    }

    public void refreshIfStale() {
        Instant last = lastRefreshedAt;
        long maxAgeMs = healthProperties.getHealth().getRefreshIntervalMs();
        if (last == null || Duration.between(last, Instant.now()).toMillis() >= maxAgeMs) {
            refresh();
        }
    }

    public void refresh() {
        synchronized (refreshLock) {
            List<SystemHealthView> results = modelHubHealthService.isEnabled()
                    ? fetchFromModelHub()
                    : fetchFromActuatorMonitors();
            for (SystemHealthView system : results) {
                if (system.id() == null) {
                    continue;
                }
                String statusKey = system.deploymentTier() + ":" + system.id();
                HealthStatus previous = previousStatuses.get(statusKey);
                if (previous != null && previous != system.status()) {
                    activityFeedService.recordHealthStatusChange(system, previous, system.status());
                }
                previousStatuses.put(statusKey, system.status());
            }
            latest.set(results);
            lastRefreshedAt = Instant.now();
            HealthSummary summary = summary();
            healthHistoryService.record(summary.total(), summary.up(), lastRefreshedAt, averageResponseTimeMs(results));
        }
    }

    public HealthHistoryService.ServicesUpChart servicesUpChart() {
        HealthSummary summary = summary();
        return healthHistoryService.chartLast24Hours(summary.total(), summary.up());
    }

    public HealthHistoryService.ResponseTimeChart responseTimeChart() {
        return healthHistoryService.responseTimeChartLast24Hours(averageResponseTimeMs(latest.get()));
    }

    public ApiSuccessMetrics apiSuccess() {
        List<SystemHealthView> systems = latest.get();
        long requests200 = 0;
        long requests500 = 0;
        for (SystemHealthView system : systems) {
            requests200 += system.requests200();
            requests500 += system.requests500();
        }
        long total = requests200 + requests500;
        HealthSummary summary = summary();
        if (total > 0) {
            double percent = requests200 * 100.0 / total;
            return new ApiSuccessMetrics(percent, "From HTTP metrics", requests200, requests500);
        }
        return new ApiSuccessMetrics(summary.upPercent(), "From health checks", 0, 0);
    }

    private static int averageResponseTimeMs(List<SystemHealthView> systems) {
        long sum = 0;
        int count = 0;
        for (SystemHealthView system : systems) {
            if (system.responseTimeMs() > 0) {
                sum += system.responseTimeMs();
                count++;
            }
        }
        return count == 0 ? 0 : (int) Math.round((double) sum / count);
    }

    public Instant getLastRefreshedAt() {
        return lastRefreshedAt;
    }

    public List<SystemHealthView> getSystems() {
        return latest.get();
    }

    public HealthSummary summary() {
        List<SystemHealthView> systems = latest.get();
        int up = 0;
        int down = 0;
        int production = 0;
        int uat = 0;
        int uatTier = 0;
        int prodTier = 0;
        for (SystemHealthView system : systems) {
            if (system.status() == HealthStatus.UP) {
                up++;
            } else if (system.status() == HealthStatus.DOWN) {
                down++;
            }
            if (isEnvironment(system.environment(), "Production")) {
                production++;
            }
            if (isEnvironment(system.environment(), "UAT")) {
                uat++;
            }
            if (HealthDeploymentTier.PROD.id().equals(system.deploymentTier())) {
                prodTier++;
            } else {
                uatTier++;
            }
        }
        return new HealthSummary(systems.size(), up, down, production, uat, uatTier, prodTier);
    }

    private List<SystemHealthView> fetchFromModelHub() {
        List<SystemHealthView> results = new ArrayList<>();
        results.addAll(fetchModelHubTier(HealthDeploymentTier.UAT));
        results.addAll(fetchModelHubTier(HealthDeploymentTier.PROD));
        return results;
    }

    private List<SystemHealthView> fetchModelHubTier(HealthDeploymentTier tier) {
        try {
            Instant fetchedAt = Instant.now();
            List<SystemHealthView> instances = modelHubHealthService.fetchAll(tier);
            Map<String, String> tags = modelHubHealthService.environmentTagsById(tier);
            if (tier == HealthDeploymentTier.UAT) {
                return modelHubWatchlistService.evaluateUatWatchlist(
                        hostCatalog.findEnabledUat(),
                        instances,
                        tags,
                        fetchedAt,
                        tier.id()
                );
            }
            return modelHubWatchlistService.evaluateProdWatchlist(
                    hostCatalog.findEnabledProd(),
                    instances,
                    tags,
                    fetchedAt,
                    tier.id()
            );
        } catch (Exception ex) {
            return List.of(new SystemHealthView(
                    null,
                    "Model Hub API (" + tier.id() + ")",
                    "—",
                    0,
                    tier == HealthDeploymentTier.PROD ? "Production" : "UAT",
                    tier.id(),
                    HealthStatus.DOWN,
                    "DOWN",
                    Instant.now(),
                    0L,
                    ex.getMessage(),
                    null,
                    null,
                    0L,
                    0L,
                    null,
                    null,
                    null,
                    tier.id()
            ));
        }
    }

    private List<SystemHealthView> fetchFromActuatorMonitors() {
        List<SystemHealthView> results = new ArrayList<>();
        hostCatalog.findEnabledUat().parallelStream()
                .map(host -> healthService.check(host, HealthDeploymentTier.UAT.id()))
                .forEach(results::add);
        hostCatalog.findEnabledProd().parallelStream()
                .map(host -> healthService.checkProd(host, HealthDeploymentTier.PROD.id()))
                .forEach(results::add);
        return results;
    }

    public boolean isModelHubEnabled() {
        return modelHubHealthService.isEnabled();
    }

    private static boolean isEnvironment(String actual, String expected) {
        return actual != null && actual.equalsIgnoreCase(expected);
    }

    public record HealthSummary(int total, int up, int down, int production, int uat, int uatTier, int prodTier) {
        public double upPercent() {
            return total == 0 ? 0.0 : (up * 100.0 / total);
        }
    }

    public record ApiSuccessMetrics(double percent, String sourceLabel, long requests200, long requests500) {
    }
}
