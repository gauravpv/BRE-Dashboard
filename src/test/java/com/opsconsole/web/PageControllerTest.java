package com.opsconsole.web;

import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.repository.AppUserRepository;
import com.opsconsole.auth.domain.OpsUserPrincipal;
import com.opsconsole.admin.repository.ManagedServiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
@SpringBootTest
@AutoConfigureMockMvc
class PageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ManagedServiceRepository managedServiceRepository;

    @Test
    void dashboard() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        mockMvc.perform(get("/").with(user(OpsUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("activeNav", "dashboard"))
                .andExpect(model().attributeExists("summary"))
                .andExpect(model().attributeExists("servicesUpChart"))
                .andExpect(model().attributeExists("apiSuccess"))
                .andExpect(model().attributeExists("responseTimeChart"))
                .andExpect(model().attributeExists("activities"))
                .andExpect(model().attributeExists("recentIncidents"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Total Services")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Health Rate")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(" of ")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(" up")));
    }

    @Test
    void systemAdmin_rendersSeededServices() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        mockMvc.perform(get("/admin").with(user(OpsUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("system-admin"))
                .andExpect(model().attributeExists("servers"))
                .andExpect(model().attributeExists("selectedService"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"serverSelect\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("payment-api")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Recent Admin Actions")));
    }

    @Test
    void systemAdmin_rendersWithActionLogRows() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        Long serviceId = managedServiceRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/admin/services/" + serviceId + "/start")
                        .with(user(OpsUserPrincipal.fromUser(admin)))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin").with(user(OpsUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Service Start")));
    }

    @Test
    void developerUtils_rendersPage() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        mockMvc.perform(get("/dev-utils").with(user(OpsUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("developer-utils"))
                .andExpect(model().attribute("activeNav", "dev-utils"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Developer Utils")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("developer-utils.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("developer-utils.css")));
    }
}
