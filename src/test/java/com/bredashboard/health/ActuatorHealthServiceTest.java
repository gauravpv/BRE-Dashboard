package com.bredashboard.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bredashboard.health.config.HealthProperties;
import com.bredashboard.health.domain.HealthStatus;
import com.bredashboard.health.service.ActuatorHealthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorHealthServiceTest {

    private ActuatorHealthService service;

    @BeforeEach
    void setUp() {
        service = new ActuatorHealthService(new ObjectMapper(), new HealthProperties());
    }

    @Test
    void parseStatus_up() throws Exception {
        assertThat(service.parseStatus("{\"status\":\"UP\"}")).isEqualTo(HealthStatus.UP);
    }

    @Test
    void parseStatus_down() throws Exception {
        assertThat(service.parseStatus("{\"status\":\"DOWN\"}")).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void resolveProbePath_normalizesActuatorRoot() {
        assertThat(ActuatorHealthService.resolveProbePath("/actuator")).isEqualTo("/actuator/health");
    }
}
