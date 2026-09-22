package com.bredashboard.health;

import com.bredashboard.health.config.HealthProperties;
import com.bredashboard.health.domain.HealthDeploymentTier;
import com.bredashboard.health.domain.MonitoredHost;
import com.bredashboard.health.dto.MonitorDetailsResponse;
import com.bredashboard.health.dto.RegisterMonitorRequest;
import com.bredashboard.health.exception.MonitorRegistrationException;
import com.bredashboard.health.service.MonitoredHostCatalog;
import com.bredashboard.health.service.MonitorRegistrationService;
import com.bredashboard.health.service.SystemHealthMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorRegistrationServiceTest {

    @Mock
    private MonitoredHostCatalog catalog;

    @Mock
    private SystemHealthMonitor healthMonitor;

    @Mock
    private HealthProperties healthProperties;

    @InjectMocks
    private MonitorRegistrationService registrationService;

    @Test
    void normalizeEnvironment_mapsCommonAliases() {
        assertThat(MonitorRegistrationService.normalizeEnvironment("prod")).isEqualTo("Production");
        assertThat(MonitorRegistrationService.normalizeEnvironment("UAT")).isEqualTo("UAT");
    }

    @Test
    void tierFromEnvironment_mapsUatAndProd() {
        assertThat(MonitorRegistrationService.tierFromEnvironment("UAT")).isEqualTo(HealthDeploymentTier.UAT);
        assertThat(MonitorRegistrationService.tierFromEnvironment("Prod")).isEqualTo(HealthDeploymentTier.PROD);
    }

    @Test
    void register_resolvesTierFromEnvironmentWhenTierMissing() {
        HealthProperties.Health health = new HealthProperties.Health();
        when(healthProperties.getHealth()).thenReturn(health);
        when(catalog.existsByHostAndPort(HealthDeploymentTier.PROD, "10.0.0.8", 9090, null)).thenReturn(false);
        when(catalog.saveProd(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(healthMonitor.getSystems()).thenReturn(List.of());
        when(healthMonitor.summary()).thenReturn(new SystemHealthMonitor.HealthSummary(1, 0, 1, 1, 0, 0, 1));
        when(healthMonitor.getLastRefreshedAt()).thenReturn(Instant.now());

        var request = new RegisterMonitorRequest(null, "Billing API", "10.0.0.8", 9090, "Prod", "US-East", null, null);
        registrationService.register(request);

        verify(catalog).saveProd(any());
        verify(catalog, never()).saveUat(any());
    }

    @Test
    void register_persistsHostAndRefreshesMonitor() {
        HealthProperties.Health health = new HealthProperties.Health();
        when(healthProperties.getHealth()).thenReturn(health);
        when(catalog.existsByHostAndPort(HealthDeploymentTier.UAT, "10.0.0.8", 9090, null)).thenReturn(false);
        when(catalog.saveUat(any(MonitoredHost.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(healthMonitor.getSystems()).thenReturn(List.of());
        when(healthMonitor.summary()).thenReturn(new SystemHealthMonitor.HealthSummary(1, 0, 1, 1, 0, 1, 0));
        when(healthMonitor.getLastRefreshedAt()).thenReturn(Instant.now());

        var request = new RegisterMonitorRequest("UAT", "Billing API", "10.0.0.8", 9090, "prod", "US-East", null, null);
        registrationService.register(request);

        ArgumentCaptor<MonitoredHost> captor = ArgumentCaptor.forClass(MonitoredHost.class);
        verify(catalog).saveUat(captor.capture());
        MonitoredHost saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Billing API");
        assertThat(saved.getEnvironment()).isEqualTo("UAT");
        verify(healthMonitor).refresh();
    }

    @Test
    void register_rejectsDuplicateHostPort() {
        HealthProperties.Health health = new HealthProperties.Health();
        when(healthProperties.getHealth()).thenReturn(health);
        when(catalog.existsByHostAndPort(HealthDeploymentTier.UAT, "localhost", 8080, null)).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(
                new RegisterMonitorRequest(null, "Dup", "localhost", 8080, "UAT", "Local", null, null)
        )).isInstanceOf(MonitorRegistrationException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void update_persistsChangesAndRefreshesMonitor() {
        HealthProperties.Health health = new HealthProperties.Health();
        when(healthProperties.getHealth()).thenReturn(health);
        MonitoredHost host = new MonitoredHost("Old Name", "10.0.0.8", 9090, "UAT", "BRE AUTH", "/actuator/health", "bre_auth_uat");
        when(catalog.findUat(7L)).thenReturn(host);
        when(catalog.existsByHostAndPort(HealthDeploymentTier.UAT, "10.0.0.9", 9091, 7L)).thenReturn(false);
        when(catalog.saveUat(host)).thenReturn(host);
        when(healthMonitor.getSystems()).thenReturn(List.of());
        when(healthMonitor.summary()).thenReturn(new SystemHealthMonitor.HealthSummary(1, 1, 0, 0, 0, 1, 0));
        when(healthMonitor.getLastRefreshedAt()).thenReturn(Instant.now());

        var request = new RegisterMonitorRequest("UAT", "Updated Name", "10.0.0.9", 9091, null, "BRE AUTH", null, null);
        registrationService.update(HealthDeploymentTier.UAT, 7L, request);

        assertThat(host.getName()).isEqualTo("Updated Name");
        verify(healthMonitor).refresh();
    }

    @Test
    void get_returnsMonitorDetails() {
        MonitoredHost host = new MonitoredHost("Billing API", "10.0.0.8", 9090, "UAT", "BRE AUTH", "/actuator/health", "bre_auth_uat");
        when(catalog.findUat(7L)).thenReturn(host);

        MonitorDetailsResponse details = registrationService.get(HealthDeploymentTier.UAT, 7L);

        assertThat(details.name()).isEqualTo("Billing API");
        assertThat(details.tier()).isEqualTo("UAT");
    }

    @Test
    void remove_deletesHostAndRefreshesMonitor() {
        MonitoredHost host = new MonitoredHost("Billing API", "10.0.0.8", 9090, "Production", "US-East", "/actuator/health", null);
        when(healthMonitor.getSystems()).thenReturn(List.of());
        when(healthMonitor.summary()).thenReturn(new SystemHealthMonitor.HealthSummary(0, 0, 0, 0, 0, 0, 0));
        when(healthMonitor.getLastRefreshedAt()).thenReturn(Instant.now());

        registrationService.remove(HealthDeploymentTier.UAT, 7L);

        verify(catalog).delete(HealthDeploymentTier.UAT, 7L);
        verify(healthMonitor).refresh();
    }
}
