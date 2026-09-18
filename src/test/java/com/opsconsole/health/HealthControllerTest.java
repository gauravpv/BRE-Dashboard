package com.opsconsole.health;

import com.opsconsole.auth.service.NavAccessService;
import com.opsconsole.activity.service.ActivityFeedService;
import com.opsconsole.health.controller.HealthApiController;
import com.opsconsole.health.controller.HealthPageController;
import com.opsconsole.health.domain.HealthDeploymentTier;
import com.opsconsole.health.dto.HealthRefreshResponse;
import com.opsconsole.health.dto.MonitorDetailsResponse;
import com.opsconsole.health.service.ActuatorHealthService;
import com.opsconsole.health.service.ModelHubHealthService;
import com.opsconsole.health.service.MonitorRegistrationService;
import com.opsconsole.health.service.SystemHealthMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({HealthPageController.class, HealthApiController.class})
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemHealthMonitor healthMonitor;

    @MockitoBean
    private ActuatorHealthService healthService;

    @MockitoBean
    private MonitorRegistrationService registrationService;

    @MockitoBean
    private NavAccessService navAccessService;

    @MockitoBean
    private ModelHubHealthService modelHubHealthService;

    @MockitoBean
    private ActivityFeedService activityFeedService;

    private static SystemHealthMonitor.HealthSummary emptySummary() {
        return new SystemHealthMonitor.HealthSummary(0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void healthPage_loads() throws Exception {
        when(healthMonitor.getSystems()).thenReturn(List.of());
        when(healthMonitor.summary()).thenReturn(emptySummary());
        when(healthMonitor.isModelHubEnabled()).thenReturn(false);
        when(modelHubHealthService.listEnvironments(HealthDeploymentTier.UAT)).thenReturn(List.of());
        when(modelHubHealthService.listEnvironments(HealthDeploymentTier.PROD)).thenReturn(List.of());
        when(healthService.refreshSeconds()).thenReturn(30);
        when(activityFeedService.recent(20)).thenReturn(List.of());

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(view().name("system-health"))
                .andExpect(model().attribute("activeNav", "health"))
                .andExpect(model().attributeExists("activities"));

        verify(healthMonitor).refreshIfStale();
    }

    @Test
    void healthPage_appliesInitialStatusFilter() throws Exception {
        when(healthMonitor.getSystems()).thenReturn(List.of());
        when(healthMonitor.summary()).thenReturn(emptySummary());
        when(healthMonitor.isModelHubEnabled()).thenReturn(false);
        when(modelHubHealthService.listEnvironments(HealthDeploymentTier.UAT)).thenReturn(List.of());
        when(modelHubHealthService.listEnvironments(HealthDeploymentTier.PROD)).thenReturn(List.of());
        when(healthService.refreshSeconds()).thenReturn(30);
        when(activityFeedService.recent(20)).thenReturn(List.of());

        mockMvc.perform(get("/health").param("status", "DOWN"))
                .andExpect(status().isOk())
                .andExpect(view().name("system-health"))
                .andExpect(model().attribute("initialStatusFilter", "DOWN"));
    }

    @Test
    void listMonitorsApi_works() throws Exception {
        when(registrationService.listAll()).thenReturn(List.of(
                new MonitorDetailsResponse(1L, "UAT", "Billing API", "10.0.0.8", 9090, "UAT", "BRE AUTH", "bre_auth_uat", "/actuator/health")
        ));

        mockMvc.perform(get("/api/health/monitors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Billing API"));
    }

    @Test
    void registerMonitorApi_works() throws Exception {
        when(registrationService.register(any())).thenReturn(
                new HealthRefreshResponse(List.of(), new SystemHealthMonitor.HealthSummary(1, 1, 0, 0, 0, 1, 0), Instant.now())
        );

        mockMvc.perform(post("/api/health/monitors")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"tier":"UAT","name":"New Svc","host":"192.168.1.10","port":8081,"environment":"UAT","region":"US-East-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(1));
    }

    @Test
    void monitorDetailsApi_works() throws Exception {
        when(registrationService.get(HealthDeploymentTier.UAT, 7L)).thenReturn(
                new MonitorDetailsResponse(7L, "UAT", "Billing API", "10.0.0.8", 9090, "Production", "US-East", "bre_auth_uat", "/actuator/health")
        );

        mockMvc.perform(get("/api/health/monitors/uat/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Billing API"));
    }

    @Test
    void updateMonitorApi_works() throws Exception {
        when(registrationService.update(eq(HealthDeploymentTier.UAT), eq(7L), any())).thenReturn(
                new HealthRefreshResponse(List.of(), new SystemHealthMonitor.HealthSummary(1, 1, 0, 0, 0, 1, 0), Instant.now())
        );

        mockMvc.perform(put("/api/health/monitors/uat/7")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"tier":"UAT","name":"Billing API","host":"10.0.0.8","port":9090,"region":"BRE AUTH"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(1));
    }

    @Test
    void removeMonitorApi_works() throws Exception {
        when(registrationService.remove(HealthDeploymentTier.UAT, 7L)).thenReturn(
                new HealthRefreshResponse(List.of(), emptySummary(), Instant.now())
        );

        mockMvc.perform(delete("/api/health/monitors/uat/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(0));

        verify(registrationService).remove(HealthDeploymentTier.UAT, 7L);
    }

    @Test
    void refreshApi_works() throws Exception {
        when(healthMonitor.getSystems()).thenReturn(List.of());
        when(healthMonitor.summary()).thenReturn(emptySummary());
        when(healthMonitor.getLastRefreshedAt()).thenReturn(Instant.now());

        mockMvc.perform(post("/api/health/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systems").isArray());
    }
}
