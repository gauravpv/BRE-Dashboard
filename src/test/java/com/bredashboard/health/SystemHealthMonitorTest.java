package com.bredashboard.health;

import com.bredashboard.activity.service.ActivityFeedService;
import com.bredashboard.health.config.HealthProperties;
import com.bredashboard.health.domain.HealthDeploymentTier;
import com.bredashboard.health.domain.HealthStatus;
import com.bredashboard.health.domain.MonitoredHost;
import com.bredashboard.health.domain.SystemHealthView;
import com.bredashboard.health.service.ActuatorHealthService;
import com.bredashboard.health.service.HealthHistoryService;
import com.bredashboard.health.service.ModelHubHealthService;
import com.bredashboard.health.service.ModelHubWatchlistService;
import com.bredashboard.health.service.MonitoredHostCatalog;
import com.bredashboard.health.service.SystemHealthMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemHealthMonitorTest {

    @Mock private MonitoredHostCatalog hostCatalog;
    @Mock private ActuatorHealthService healthService;
    @Mock private ModelHubHealthService modelHubHealthService;
    @Mock private ModelHubWatchlistService modelHubWatchlistService;
    @Mock private ActivityFeedService activityFeedService;
    @Mock private HealthHistoryService healthHistoryService;

    @Test
    void prodProfileFetchesOnlyProdModelHubAndCountsOnlyProd() {
        HealthProperties properties = new HealthProperties();
        properties.getHealth().setMonitoredTiers(List.of(HealthDeploymentTier.PROD));
        when(modelHubHealthService.isEnabled()).thenReturn(true);
        SystemHealthView prod = mock(SystemHealthView.class);
        when(prod.id()).thenReturn(1L);
        when(prod.deploymentTier()).thenReturn("PROD");
        when(prod.environment()).thenReturn("Production");
        when(prod.status()).thenReturn(HealthStatus.UP);
        when(modelHubWatchlistService.evaluateProdWatchlist(anyList(), anyList(), anyMap(), any(Instant.class), eq("PROD")))
                .thenReturn(List.of(prod));

        SystemHealthMonitor monitor = monitor(properties);
        monitor.refresh();

        assertThat(monitor.getSystems()).containsExactly(prod);
        assertThat(monitor.summary().total()).isEqualTo(1);
        assertThat(monitor.summary().prodTier()).isEqualTo(1);
        assertThat(monitor.summary().uatTier()).isZero();
        verify(modelHubHealthService).fetchAll(HealthDeploymentTier.PROD);
        verify(modelHubHealthService, never()).fetchAll(HealthDeploymentTier.UAT);
    }

    @Test
    void uatProfileSkipsProdActuatorMonitors() {
        HealthProperties properties = new HealthProperties();
        properties.getHealth().setMonitoredTiers(List.of(HealthDeploymentTier.UAT));
        when(modelHubHealthService.isEnabled()).thenReturn(false);

        SystemHealthMonitor monitor = monitor(properties);
        monitor.refresh();

        assertThat(monitor.getSystems()).isEmpty();
        verify(hostCatalog).findEnabledUat();
        verify(hostCatalog, never()).findEnabledProd();
    }

        @Test
        void modelHubFailure_keepsRegisteredSystemsInsteadOfSyntheticApiRow() {
        HealthProperties properties = new HealthProperties();
        properties.getHealth().setMonitoredTiers(List.of(HealthDeploymentTier.UAT));
        MonitoredHost host = mock(MonitoredHost.class);
        SystemHealthView unavailableSystem = mock(SystemHealthView.class);
        when(modelHubHealthService.isEnabled()).thenReturn(true);
        when(hostCatalog.findEnabledUat()).thenReturn(List.of(host));
        when(modelHubHealthService.fetchAll(HealthDeploymentTier.UAT))
            .thenThrow(new IllegalStateException("Model Hub unavailable"));
        when(modelHubWatchlistService.unavailableUatWatchlist(
            anyList(), any(Instant.class), eq("UAT"), eq("Model Hub unavailable")))
            .thenReturn(List.of(unavailableSystem));

        SystemHealthMonitor monitor = monitor(properties);
        monitor.refresh();

        assertThat(monitor.getSystems()).containsExactly(unavailableSystem);
        verify(modelHubWatchlistService).unavailableUatWatchlist(
            anyList(), any(Instant.class), eq("UAT"), eq("Model Hub unavailable"));
        }

    private SystemHealthMonitor monitor(HealthProperties properties) {
        return new SystemHealthMonitor(hostCatalog, healthService, modelHubHealthService,
                modelHubWatchlistService, activityFeedService, healthHistoryService, properties);
    }
}