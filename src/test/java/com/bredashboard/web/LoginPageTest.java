package com.bredashboard.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoginPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPage_renders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign in to BRE Dashboard")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Email Address")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("classList.add('light')")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("stitch-theme.js"))));
    }

    @Test
    void formLogin_withSeedAdmin() throws Exception {
        mockMvc.perform(post("/login/process")
                        .param("email", "admin@bredashboard.local")
                        .param("password", "Admin@123"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void loginPage_includesCsrfField() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    void loginPage_explainsPendingAdministratorApproval() throws Exception {
        mockMvc.perform(get("/login").param("pending", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "administrator must assign your role and approve the account"
                )));
    }
}
