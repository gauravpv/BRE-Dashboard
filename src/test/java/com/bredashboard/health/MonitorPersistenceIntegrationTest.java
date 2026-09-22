package com.bredashboard.health;

import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.BreUserPrincipal;
import com.bredashboard.auth.repository.AppUserRepository;
import com.bredashboard.health.domain.MonitoredHost;
import com.bredashboard.health.repository.MonitoredHostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MonitorPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MonitoredHostRepository repository;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void registerMonitor_persistsToDatabaseAndListsViaApi() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        long before = repository.count();

        mockMvc.perform(post("/api/health/monitors")
                        .with(user(BreUserPrincipal.fromUser(admin)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tier":"UAT","name":"Integration Test API","host":"10.99.88.77","port":9099,"environment":"UAT","region":"Test Region"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").isNumber());

        assertThat(repository.count()).isEqualTo(before + 1);

        MonitoredHost saved = repository.findAll().stream()
                .filter(host -> "10.99.88.77".equals(host.getHost()) && host.getPort() == 9099)
                .findFirst()
                .orElseThrow();
        assertThat(saved.getName()).isEqualTo("Integration Test API");
        assertThat(saved.getEnvironment()).isEqualTo("UAT");
        assertThat(saved.getRegion()).isEqualTo("Test Region");

        mockMvc.perform(get("/api/health/monitors").with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.host=='10.99.88.77' && @.port==9099)].name").value("Integration Test API"));
    }
}
