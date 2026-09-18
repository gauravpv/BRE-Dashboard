package com.opsconsole.transactions;

import com.opsconsole.transactions.config.TransactionDashboardProperties;
import com.opsconsole.transactions.dto.TransactionDashboardView;
import com.opsconsole.transactions.service.TransactionDashboardService;
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
