package com.bredashboard.transactions;

import com.bredashboard.transactions.config.TransactionDashboardProperties;
import com.bredashboard.transactions.dto.TransactionDashboardView;
import com.bredashboard.transactions.service.TransactionDashboardService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionDashboardServiceTest {

    @Test
    void liveDashboardReportsMissingConfigurationWithoutFailingPage() {
        TransactionDashboardProperties properties = new TransactionDashboardProperties();
        TransactionDashboardService service = new TransactionDashboardService(properties, null);

        TransactionDashboardView dashboard = service.loadDashboard();

        assertThat(dashboard.available()).isFalse();
        assertThat(dashboard.errorMessage()).contains("TXN_DB_URL");
    }
}
