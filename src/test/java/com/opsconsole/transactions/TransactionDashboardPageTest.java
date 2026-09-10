package com.opsconsole.transactions;

import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.domain.OpsUserPrincipal;
import com.opsconsole.auth.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionDashboardPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void administratorCanViewTransactionDashboard() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();

        mockMvc.perform(get("/transactions").with(user(OpsUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-dashboard"))
                .andExpect(model().attribute("activeNav", "transactions"))
                .andExpect(model().attributeExists("dashboard", "refreshSeconds"))
                .andExpect(content().string(containsString("Customer journey")))
                .andExpect(content().string(containsString("Eligibility checks")))
                .andExpect(content().string(containsString("Authorization volume")))
                .andExpect(content().string(containsString("Flipkart")))
                .andExpect(content().string(containsString("Amazon")))
                .andExpect(content().string(containsString("Eligibility check speed")));
    }
}
