package com.opsconsole.admin;

import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.repository.AppUserRepository;
import com.opsconsole.auth.domain.OpsUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.opsconsole.admin.repository.ManagedServiceRepository;
@SpringBootTest
@AutoConfigureMockMvc
class ServiceAdminApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ManagedServiceRepository managedServiceRepository;

    @Test
    void listServers_asAdmin_returnsSeededServers() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();

        mockMvc.perform(get("/api/admin/servers")
                        .with(user(OpsUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("dev-linux-01"))
                .andExpect(jsonPath("$[0].services[0].name").exists());
    }

    @Test
    void startService_asAdmin_returnsSuccess() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        Long serviceId = managedServiceRepository.findByEnabledTrueOrderByCategoryAscNameAsc().get(0).getId();

        mockMvc.perform(post("/api/admin/services/" + serviceId + "/start")
                        .with(user(OpsUserPrincipal.fromUser(admin)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listServers_asMonitoringUser_returnsForbidden() throws Exception {
        AppUser viewer = userRepository.findByAzureAdId("dev-monitoring").orElseThrow();

        mockMvc.perform(get("/api/admin/servers")
                        .with(user(OpsUserPrincipal.fromUser(viewer))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("System Admin access required"));
    }

    @Test
    void getProperties_asAdmin_returnsContent() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        Long serviceId = managedServiceRepository.findByEnabledTrueOrderByCategoryAscNameAsc().get(0).getId();

        mockMvc.perform(get("/api/admin/services/" + serviceId + "/properties")
                        .with(user(OpsUserPrincipal.fromUser(admin)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }
}
