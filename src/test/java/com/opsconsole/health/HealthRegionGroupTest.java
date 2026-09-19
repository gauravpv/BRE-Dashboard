package com.opsconsole.health;

import com.opsconsole.health.domain.HealthRegionGroup;
import com.opsconsole.health.domain.HealthStatus;
import com.opsconsole.health.domain.SystemHealthView;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HealthRegionGroupTest {

    @Test
    void fromSystems_groupsByRegionAndSorts() {
        List<SystemHealthView> systems = List.of(
                modelHubView("BRE-AA-MFA", "BRE AA"),
                modelHubView("BRE-AA-POD", "BRE AA"),
                modelHubView("BRE-POD-1", "BRE POD"),
                modelHubView("BRE-AUTH-1", "BRE AUTH")
        );

        List<HealthRegionGroup> groups = HealthRegionGroup.fromSystems(systems);

        assertThat(groups).hasSize(3);
        assertThat(groups.get(0).region()).isEqualTo("BRE AA");
        assertThat(groups.get(0).systems()).hasSize(2);
        assertThat(groups.get(1).region()).isEqualTo("BRE AUTH");
        assertThat(groups.get(2).region()).isEqualTo("BRE POD");
    }

    private static SystemHealthView modelHubView(String environmentId, String region) {
        return SystemHealthView.fromModelHub(
                1L,
                "ACTICO Execution Server",
                "10.0.0.1",
                9010,
                "UAT",
                region,
                HealthStatus.UP,
                "UP",
                Instant.parse("2026-08-27T13:00:00Z"),
                "http://10.0.0.1:9010",
                environmentId,
                100L,
                0L,
                "10.2.0",
                "ACTICO",
                Instant.parse("2026-08-27T14:00:00Z"),
                "UAT"
        );
    }
}
