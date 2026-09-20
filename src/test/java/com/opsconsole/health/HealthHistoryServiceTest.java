package com.opsconsole.health;

import com.opsconsole.health.repository.HealthSnapshotRepository;
import com.opsconsole.health.service.HealthHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class HealthHistoryServiceTest {

    @Autowired
    private HealthHistoryService history;

    @Autowired
    private HealthSnapshotRepository repository;

    @BeforeEach
    void clearSnapshots() {
        repository.deleteAll();
    }

    @Test
    void chartLast24Hours_usesRecordedSnapshots() {
        Instant base = Instant.now().minusSeconds(300);
        history.record(4, 2, base);
        history.record(4, 3, base.plusSeconds(60));
        history.record(4, 4, base.plusSeconds(120));

        var chart = history.chartLast24Hours(4, 4);

        assertThat(chart.points()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(chart.maxY()).isEqualTo(4);
        assertThat(chart.currentUp()).isEqualTo(4);
        assertThat(chart.linePath()).contains("M");
        assertThat(chart.areaPath()).contains("Z");
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void chartLast24Hours_defaultsWhenEmpty() {
        var chart = history.chartLast24Hours(3, 2);

        assertThat(chart.points()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(chart.points().get(chart.points().size() - 1).upCount()).isEqualTo(2);
        assertThat(chart.xAxisLabels()).hasSize(5);
    }

    @Test
    void responseTimeChartLast24Hours_usesRecordedSnapshots() {
        Instant base = Instant.now().minusSeconds(300);
        history.record(4, 4, base, 100);
        history.record(4, 4, base.plusSeconds(60), 150);
        history.record(4, 4, base.plusSeconds(120), 200);

        var chart = history.responseTimeChartLast24Hours(200);

        assertThat(chart.bars()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(chart.currentAvgMs()).isEqualTo(200);
        assertThat(chart.maxMs()).isGreaterThanOrEqualTo(200);
    }

    @Test
    void responseTimeChartLast24Hours_defaultsWhenEmpty() {
        var chart = history.responseTimeChartLast24Hours(80);

        assertThat(chart.bars()).hasSize(1);
        assertThat(chart.bars().get(0).avgMs()).isEqualTo(80);
    }
}
