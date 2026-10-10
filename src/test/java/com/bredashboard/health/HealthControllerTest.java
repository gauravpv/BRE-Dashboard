package com.bredashboard.health;

import com.bredashboard.auth.service.NavAccessService;
import com.bredashboard.activity.service.ActivityFeedService;
import com.bredashboard.auth.domain.AppRole;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.BreUserPrincipal;
import com.bredashboard.health.controller.HealthApiController;
import com.bredashboard.health.controller.HealthPageController;
import com.bredashboard.health.domain.HealthDeploymentTier;
import com.bredashboard.health.dto.HealthRefreshResponse;
import com.bredashboard.health.dto.MonitorDetailsResponse;
import com.bredashboard.health.service.ActuatorHealthService;
import com.bredashboard.health.service.ModelHubHealthService;
import com.bredashboard.health.service.MonitorRegistrationService;
import com.bredashboard.health.service.SystemHealthMonitor;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

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

        private static BreUserPrincipal principalWithRole(String roleCode) {
                AppRole role = new AppRole(roleCode, roleCode, "", true);
                AppUser appUser = new AppUser("health-test-" + roleCode.toLowerCase(),
                                roleCode.toLowerCase() + "@test.local", roleCode, role);
                return BreUserPrincipal.fromUser(appUser);
        }

    @Test
    void healthPage_loads() throws Exception {
        when(healthMonitor.getSystems()).thenReturn(List.of());
        when(healthMonitor.summary()).thenReturn(emptySummary());
        when(healthMonitor.isModelHubEnabled()).thenReturn(false);
        when(modelHubHealthService.listEnvironments(HealthDeploymentTier.UAT)).thenReturn(List.of());
        when(modelHubHealthService.listEnvironments(HealthDeploymentTier.PROD)).thenReturn(List.of());
        when(healthService.refreshSeconds()).thenReturn(30);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(view().name("system-health"))
                .andExpect(model().attribute("activeNav", "health"))
                .andExpect(model().attribute("isAdministrator", false))
                .andExpect(model().attributeDoesNotExist("activities"));

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

        mockMvc.perform(get("/health").param("status", "DOWN"))
                .andExpect(status().isOk())
                .andExpect(view().name("system-health"))
                .andExpect(model().attribute("initialStatusFilter", "DOWN"));
    }

    @Test
    void healthPageOnlyShowsSystemManagementControlsToAdministrators() throws Exception {
        when(healthMonitor.getSystems()).thenReturn(List.of());
        when(healthMonitor.summary()).thenReturn(emptySummary());
        when(healthMonitor.isModelHubEnabled()).thenReturn(false);
        when(modelHubHealthService.listEnvironments(HealthDeploymentTier.UAT)).thenReturn(List.of());
        when(modelHubHealthService.listEnvironments(HealthDeploymentTier.PROD)).thenReturn(List.of());
        when(healthService.refreshSeconds()).thenReturn(30);

        mockMvc.perform(get("/health").with(user(principalWithRole("MONITORING"))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("register-service-card"))))
                .andExpect(content().string(containsString("System configuration is managed by Administrators")));

        mockMvc.perform(get("/health").with(user(principalWithRole("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("register-service-card")))
                .andExpect(content().string(containsString("Add or edit systems below")));
    }

        @Test
        void healthPage_usesConfiguredProdTier() throws Exception {
                when(healthMonitor.getSystems()).thenReturn(List.of());
                when(healthMonitor.summary()).thenReturn(emptySummary());
                when(healthMonitor.monitoredTiers()).thenReturn(List.of(HealthDeploymentTier.PROD));

                mockMvc.perform(get("/health"))
                                .andExpect(status().isOk())
                                .andExpect(model().attribute("monitoredTiers", List.of(HealthDeploymentTier.PROD)))
                                .andExpect(model().attribute("deploymentTier", HealthDeploymentTier.PROD));
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

        mockMvc.perform(post("/api/health/monitors").with(user(principalWithRole("ADMIN")))
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

        mockMvc.perform(put("/api/health/monitors/uat/7").with(user(principalWithRole("ADMIN")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"tier":"UAT","name":"Billing API","host":"10.0.0.8","port":9090,"region":"BRE AUTH"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(1));
    }

    @Test
    void updateMonitorPostApi_works() throws Exception {
        when(registrationService.update(eq(HealthDeploymentTier.PROD), eq(7L), any())).thenReturn(
                new HealthRefreshResponse(List.of(), new SystemHealthMonitor.HealthSummary(1, 1, 0, 1, 0, 0, 1), Instant.now())
        );

        mockMvc.perform(post("/api/health/monitors/prod/7/update").with(user(principalWithRole("ADMIN")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"tier":"PROD","name":"BRE AA","host":"10.59.14.13","port":8070,"region":"BRE AM MFA"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(1));

        verify(registrationService).update(eq(HealthDeploymentTier.PROD), eq(7L), any());
    }

    @Test
    void removeMonitorApi_works() throws Exception {
        when(registrationService.remove(HealthDeploymentTier.UAT, 7L)).thenReturn(
                new HealthRefreshResponse(List.of(), emptySummary(), Instant.now())
        );

        mockMvc.perform(delete("/api/health/monitors/uat/7").with(user(principalWithRole("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(0));

        verify(registrationService).remove(HealthDeploymentTier.UAT, 7L);
    }

        @Test
        void removeMonitorPostApi_works() throws Exception {
                when(registrationService.remove(HealthDeploymentTier.PROD, 7L)).thenReturn(
                                new HealthRefreshResponse(List.of(), emptySummary(), Instant.now())
                );

                mockMvc.perform(post("/api/health/monitors/prod/7/delete").with(user(principalWithRole("ADMIN"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.summary.total").value(0));

                verify(registrationService).remove(HealthDeploymentTier.PROD, 7L);
        }

    @Test
    void nonAdministratorsCannotCreateUpdateOrDeleteSystems() throws Exception {
        for (String roleCode : List.of("MONITORING", "TESTER")) {
            var principal = user(principalWithRole(roleCode));
            mockMvc.perform(post("/api/health/monitors").with(principal)
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"tier":"UAT","name":"New Svc","host":"192.168.1.10","port":8081,"environment":"UAT"}
                                    """))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/health/monitors/uat/7").with(principal)
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"tier":"UAT","name":"Svc","host":"192.168.1.10","port":8081,"environment":"UAT"}
                                    """))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/health/monitors/uat/7/update").with(principal)
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"tier":"UAT","name":"Svc","host":"192.168.1.10","port":8081,"environment":"UAT"}
                                    """))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/health/monitors/uat/7").with(principal))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/health/monitors/uat/7/delete").with(principal))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(registrationService);
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
