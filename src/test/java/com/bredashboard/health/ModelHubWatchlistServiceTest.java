package com.bredashboard.health;

import com.bredashboard.health.domain.HealthStatus;
import com.bredashboard.health.domain.MonitoredHost;
import com.bredashboard.health.domain.SystemHealthView;
import com.bredashboard.health.service.ModelHubWatchlistService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelHubWatchlistServiceTest {

    private ModelHubWatchlistService service;
    private Instant fetchedAt;

    @BeforeEach
    void setUp() {
        service = new ModelHubWatchlistService();
        fetchedAt = Instant.parse("2026-08-26T10:00:00Z");
    }

    @Test
    void evaluateWatchlist_marksMatchingInstanceUp() {
        MonitoredHost host = new MonitoredHost(
                "AUTH BRE",
                "10.48.129.199",
                9010,
                "UAT",
                "BRE AUTH",
                "/actuator/health",
                null
        );

        List<SystemHealthView> instances = List.of(
                SystemHealthView.fromModelHub(
                        99L,
                        "ACTICO Execution Server",
                        "10.48.129.199",
                        9010,
                        "UAT",
                        "BRE AUTH",
                        HealthStatus.UP,
                        "UP",
                        fetchedAt,
                        "http://10.48.129.199:9010",
                        "bre_auth_uat",
                        492L,
                        0L,
                        "10.2.0",
                        "ACTICO",
                        fetchedAt.plusSeconds(3600),
                        "UAT"
                )
        );

        List<SystemHealthView> results = service.evaluateUatWatchlist(
                List.of(host),
                instances,
                Map.of("bre_auth_uat", "BRE AUTH"),
                fetchedAt,
                "UAT"
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(HealthStatus.UP);
        assertThat(results.get(0).name()).isEqualTo("AUTH BRE");
        assertThat(results.get(0).requests200()).isEqualTo(492L);
        assertThat(results.get(0).environmentId()).isEqualTo("bre_auth_uat");
        assertThat(results.get(0).region()).isEqualTo("BRE AUTH");
    }

    @Test
    void evaluateWatchlist_marksMissingInstanceDown() {
        MonitoredHost host = new MonitoredHost(
                "Missing Host",
                "10.0.0.99",
                9010,
                "UAT",
                "BRE AUTH",
                "/actuator/health",
                null
        );

        List<SystemHealthView> results = service.evaluateUatWatchlist(List.of(host), List.of(), Map.of(), fetchedAt, "UAT");

        assertThat(results.get(0).status()).isEqualTo(HealthStatus.DOWN);
        assertThat(results.get(0).errorMessage()).isEqualTo("System Down");
        assertThat(results.get(0).region()).isEqualTo("BRE AUTH");
    }

        @Test
        void unavailableWatchlist_keepsRegisteredHostDetailsAndError() {
                MonitoredHost host = org.mockito.Mockito.mock(MonitoredHost.class);
                org.mockito.Mockito.when(host.getId()).thenReturn(17L);
                org.mockito.Mockito.when(host.getName()).thenReturn("Billing API");
                org.mockito.Mockito.when(host.getHost()).thenReturn("10.0.0.8");
                org.mockito.Mockito.when(host.getPort()).thenReturn(9090);
                org.mockito.Mockito.when(host.getEnvironment()).thenReturn("UAT");
                org.mockito.Mockito.when(host.getRegion()).thenReturn("US-East");

                List<SystemHealthView> results = service.unavailableUatWatchlist(
                                List.of(host), fetchedAt, "UAT", "Model Hub unavailable"
                );

                assertThat(results).singleElement().satisfies(system -> {
                        assertThat(system.id()).isEqualTo(17L);
                        assertThat(system.name()).isEqualTo("Billing API");
                        assertThat(system.status()).isEqualTo(HealthStatus.DOWN);
                        assertThat(system.errorMessage()).isEqualTo("Model Hub unavailable");
                });
        }

    @Test
    void evaluateWatchlist_matchesByHostPortWithoutStoredEnvironmentId() {
        MonitoredHost host = new MonitoredHost(
                "AUTH BRE",
                "10.48.129.199",
                9010,
                "UAT",
                "BRE AUTH",
                "/actuator/health",
                null
        );

        List<SystemHealthView> instances = List.of(
                SystemHealthView.fromModelHub(
                        99L,
                        "ACTICO Execution Server",
                        "10.48.129.199",
                        9010,
                        "UAT",
                        "BRE AUTH",
                        HealthStatus.UP,
                        "UP",
                        fetchedAt,
                        "http://10.48.129.199:9010",
                        "bre_auth_uat",
                        492L,
                        0L,
                        "10.2.0",
                        "ACTICO",
                        fetchedAt.plusSeconds(3600),
                        "UAT"
                )
        );

        List<SystemHealthView> results = service.evaluateUatWatchlist(List.of(host), instances, Map.of(), fetchedAt, "UAT");

        assertThat(results.get(0).status()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void resolveRegion_usesStoredGroupName() {
        MonitoredHost host = new MonitoredHost(
                "AUTH BRE",
                "10.48.129.199",
                9010,
                "UAT",
                "BRE POD",
                "/actuator/health",
                null
        );

        String region = ModelHubWatchlistService.resolveRegion(host, "bre_auth_uat", Map.of("bre_auth_uat", "BRE AUTH"));

        assertThat(region).isEqualTo("BRE POD");
    }
}

