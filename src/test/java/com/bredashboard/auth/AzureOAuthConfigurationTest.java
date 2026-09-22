package com.bredashboard.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "bredashboard.auth.azure.client-id=test-dashboard-client",
        "bredashboard.auth.azure.client-secret=test-dashboard-secret",
        "bredashboard.auth.azure.tenant-id=710de1d3-2901-4647-89e7-3b01f1c2806d"
})
@AutoConfigureMockMvc
class AzureOAuthConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void microsoftButton_startsTheAzureAuthorizationCodeFlow() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/azure")));

        mockMvc.perform(get("/oauth2/authorization/azure"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location",
                        startsWith("https://login.microsoftonline.com/710de1d3-2901-4647-89e7-3b01f1c2806d/oauth2/v2.0/authorize?")
                ))
                .andExpect(header().string(
                        "Location",
                        containsString("redirect_uri=http://localhost/login/oauth2/code/azure")
                ));
    }
}
