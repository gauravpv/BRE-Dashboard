package com.opsconsole.health;

import com.opsconsole.health.config.HealthProperties;
import com.opsconsole.health.domain.HealthDeploymentTier;
import com.opsconsole.health.domain.HealthStatus;
import com.opsconsole.health.domain.SystemHealthView;
import com.opsconsole.health.service.ModelHubHealthService;
import com.opsconsole.health.service.ModelHubOAuthTokenClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ModelHubHealthServiceTest {

    private ModelHubHealthService service;

    @BeforeEach
    void setUp() {
        HealthProperties properties = new HealthProperties();
        properties.getHealth().getModelHub().setEnabled(true);
        properties.getHealth().getModelHub().getUat().setBaseUrl("https://example.test");
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        service = new ModelHubHealthService(objectMapper, properties, new ModelHubOAuthTokenClient(objectMapper, properties));
    }

    @Test
    void parseEnvironments_extractsEnvironmentIds() throws IOException {
        String body = readFixture("modelhub/environments.json");
        var environments = service.parseEnvironments(body);

        assertThat(environments).hasSize(2);
        assertThat(environments.get(0).environmentId()).isEqualTo("bre_auth_uat");
        assertThat(environments.get(0).tag()).isEqualTo("BRE AUTH");
    }

    @Test
    void parseInstances_mapsMetricsAndStatus() throws IOException {
        String body = readFixture("modelhub/instances-bre_auth_uat.json");
        var environment = new ModelHubHealthService.EnvironmentEntry("bre_auth_uat", "BRE AUTH UAT", "BRE AUTH");
        Instant fetchedAt = Instant.parse("2026-08-26T10:00:00Z");

        var views = service.parseInstances(body, environment, fetchedAt, HealthDeploymentTier.UAT);

        assertThat(views).hasSize(2);

        SystemHealthView up = views.get(0);
        assertThat(up.status()).isEqualTo(HealthStatus.UP);
        assertThat(up.serviceUrl()).isEqualTo("http://10.48.129.199:9010");
        assertThat(up.host()).isEqualTo("10.48.129.199");
        assertThat(up.port()).isEqualTo(9010);
        assertThat(up.requests200()).isEqualTo(492L);
        assertThat(up.requests500()).isZero();
        assertThat(up.environmentId()).isEqualTo("bre_auth_uat");
        assertThat(up.appVersion()).isEqualTo("10.2.0");

        SystemHealthView down = views.get(1);
        assertThat(down.status()).isEqualTo(HealthStatus.DOWN);
        assertThat(down.requests200()).isEqualTo(3638L);
        assertThat(down.requests500()).isEqualTo(11L);
    }

    @Test
    void fetchAll_whenDisabled_returnsEmpty() {
        HealthProperties properties = new HealthProperties();
        properties.getHealth().getModelHub().setEnabled(false);
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ModelHubHealthService disabled = new ModelHubHealthService(
                objectMapper, properties, new ModelHubOAuthTokenClient(objectMapper, properties));

        assertThat(disabled.fetchAll(HealthDeploymentTier.UAT)).isEmpty();
    }

    private static String readFixture(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
