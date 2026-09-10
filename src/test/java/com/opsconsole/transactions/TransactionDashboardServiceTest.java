package com.opsconsole.transactions;

import com.opsconsole.transactions.config.TransactionDashboardProperties;
import com.opsconsole.transactions.dto.TransactionDashboardView;
import com.opsconsole.transactions.service.TransactionDashboardService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionDashboardServiceTest {

    @Test
    void mockDashboardProvidesAllReportingWindows() {
        TransactionDashboardProperties properties = new TransactionDashboardProperties();
        properties.setMockMode(true);
        TransactionDashboardService service = new TransactionDashboardService(properties);

        TransactionDashboardView dashboard = service.loadDashboard();

        assertThat(dashboard.available()).isTrue();
        assertThat(dashboard.minuteSeries()).hasSize(15);
        assertThat(dashboard.hourlySeries()).hasSize(5);
        assertThat(dashboard.responseTimeBuckets()).isNotEmpty();
        assertThat(dashboard.last15Minutes().totalHits()).isPositive();
        assertThat(dashboard.last15Minutes().flipkartHits()).isPositive();
        assertThat(dashboard.last15Minutes().amazonHits()).isPositive();
        assertThat(dashboard.last15Minutes().approvalRate()).isBetween(0.0, 100.0);

        // Tables render newest first, while the chart keeps chronological order.
        assertThat(dashboard.minuteRows().getFirst()).isEqualTo(dashboard.minuteSeries().getLast());
        assertThat(dashboard.hourlyRows().getFirst()).isEqualTo(dashboard.hourlySeries().getLast());
        assertThat(dashboard.minuteChart().points()).hasSize(15);
        assertThat(dashboard.minuteChart().peak()).isNotNull();
        assertThat(dashboard.journey()).hasSize(4);
        assertThat(dashboard.marketplaces()).hasSize(3);
        assertThat(dashboard.delta().available()).isTrue();
        assertThat(dashboard.latencySummary().under300Percent()).isBetween(0.0, 100.0);
    }

    @Test
    void liveDashboardReportsMissingConfigurationWithoutFailingPage() {
        TransactionDashboardProperties properties = new TransactionDashboardProperties();
        properties.setMockMode(false);
        TransactionDashboardService service = new TransactionDashboardService(properties);

        TransactionDashboardView dashboard = service.loadDashboard();

        assertThat(dashboard.available()).isFalse();
        assertThat(dashboard.errorMessage()).contains("URL");
    }
}
