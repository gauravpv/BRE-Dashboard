package com.bredashboard.health;

import com.bredashboard.health.config.HealthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ModelHubYamlBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(HealthProperties.class)
    static class TestConfig {
    }

    @Test
    void bindsModelHubUnderHealthPrefix() {
        runner.withPropertyValues(
                        "bredashboard.health.model-hub.enabled=true",
                        "bredashboard.health.model-hub.uat.base-url=https://example-uat.test",
                        "bredashboard.health.model-hub.prod.base-url=https://example-prod.test")
                .run(context -> {
                    HealthProperties props = context.getBean(HealthProperties.class);
                    assertThat(props.getHealth().getModelHub().isEnabled()).isTrue();
                    assertThat(props.getHealth().getModelHub().getUat().getBaseUrl()).isEqualTo("https://example-uat.test");
                    assertThat(props.getHealth().getModelHub().getProd().getBaseUrl()).isEqualTo("https://example-prod.test");
                });
    }

    @Test
    void bindsModelHubOAuthFields() {
        runner.withPropertyValues(
                        "bredashboard.health.model-hub.oauth.token-url=https://login.example.test/token",
                        "bredashboard.health.model-hub.oauth.client-id=test-client",
                        "bredashboard.health.model-hub.oauth.grant-type=password",
                        "bredashboard.health.model-hub.oauth.username=hub-user",
                        "bredashboard.health.model-hub.oauth.password=hub-secret",
                        "bredashboard.health.model-hub.oauth.scope=openid profile api://test/default")
                .run(context -> {
                    HealthProperties.OAuth oauth = context.getBean(HealthProperties.class)
                            .getHealth().getModelHub().getOauth();
                    assertThat(oauth.getTokenUrl()).isEqualTo("https://login.example.test/token");
                    assertThat(oauth.getClientId()).isEqualTo("test-client");
                    assertThat(oauth.getGrantType()).isEqualTo("password");
                    assertThat(oauth.getUsername()).isEqualTo("hub-user");
                    assertThat(oauth.getPassword()).isEqualTo("hub-secret");
                    assertThat(oauth.getScope()).isEqualTo("openid profile api://test/default");
                });
    }

    @Test
    void bindsAdditionalModelHubBaseUrls() {
        runner.withPropertyValues(
                        "bredashboard.health.model-hub.uat.base-url=https://emicardbre.bajajfinserv.in",
                        "bredashboard.health.model-hub.uat.base-urls[0]=https://bfldaasbre-uat.bajajfinserv.in",
                        "bredashboard.health.model-hub.prod.base-url=https://emicardbre-prod.bajajfinserv.in",
                        "bredashboard.health.model-hub.prod.base-urls[0]=https://bfldaasbre-prod.bajajfinserv.in")
                .run(context -> {
                    HealthProperties props = context.getBean(HealthProperties.class);
                    assertThat(props.getHealth().getModelHub().getUat().resolvedBaseUrls()).containsExactly(
                            "https://emicardbre.bajajfinserv.in",
                            "https://bfldaasbre-uat.bajajfinserv.in");
                    assertThat(props.getHealth().getModelHub().getProd().resolvedBaseUrls()).containsExactly(
                            "https://emicardbre-prod.bajajfinserv.in",
                            "https://bfldaasbre-prod.bajajfinserv.in");
                });
    }
}
