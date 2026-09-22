package com.bredashboard.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.BreUserPrincipal;
import com.bredashboard.auth.repository.AppUserRepository;
import com.bredashboard.auth.service.AuthDataInitializer;
import com.bredashboard.auth.service.RoleAdminService;
@SpringBootTest
@AutoConfigureMockMvc
class RoleAdminApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createUser_asAdmin_returnsCreated() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        var body = new RoleAdminService.CreateUserRequest(
                "API User",
                "api-created-" + System.currentTimeMillis() + "@bredashboard.local",
                null,
                AuthDataInitializer.CODE_MONITORING,
                "ApiPass@123",
                null,
                true
        );

        mockMvc.perform(post("/api/admin/users")
                        .with(user(BreUserPrincipal.fromUser(admin)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(body.email()));
    }

    @Test
    void deleteUser_asAdmin_returnsNoContent() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        var body = new RoleAdminService.CreateUserRequest(
                "Delete Me",
                "delete-me-" + System.currentTimeMillis() + "@bredashboard.local",
                null,
                AuthDataInitializer.CODE_MONITORING,
                "ApiPass@123",
                null,
                true
        );
        String json = objectMapper.writeValueAsString(body);
        String created = mockMvc.perform(post("/api/admin/users")
                        .with(user(BreUserPrincipal.fromUser(admin)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long userId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/admin/users/" + userId)
                        .with(user(BreUserPrincipal.fromUser(admin)))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
