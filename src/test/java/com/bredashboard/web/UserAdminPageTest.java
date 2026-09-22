package com.bredashboard.web;

import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.repository.AppUserRepository;
import com.bredashboard.auth.domain.BreUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest
@AutoConfigureMockMvc
class UserAdminPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void usersPage_renders() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        mockMvc.perform(get("/users").with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"userSearch\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"usersPaginationBar\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(">Azure AD ID</th>"))));
    }
}
