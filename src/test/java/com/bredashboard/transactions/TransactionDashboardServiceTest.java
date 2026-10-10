package com.bredashboard.transactions;

import com.bredashboard.transactions.config.TransactionDashboardProperties;
import com.bredashboard.transactions.dto.ChannelHitsView;
import com.bredashboard.transactions.dto.LiveOverviewView;
import com.bredashboard.transactions.dto.LoanAverageView;
import com.bredashboard.transactions.dto.OtpHitsView;
import com.bredashboard.transactions.dto.ResponseTimeDistributionView;
import com.bredashboard.transactions.dto.TransactionDashboardView;
import com.bredashboard.transactions.service.TransactionDashboardService;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionDashboardServiceTest {

    @Test
    void liveDashboardReportsMissingConfigurationWithoutFailingPage() {
        TransactionDashboardProperties properties = new TransactionDashboardProperties();
        TransactionDashboardService service = new TransactionDashboardService(properties, null);

        TransactionDashboardView dashboard = service.loadDashboard();

        assertThat(dashboard.available()).isFalse();
        assertThat(dashboard.errorMessage()).contains("TXN_DB_URL");
    }

    @Test
    void liveDashboardRunsIndependentQueriesInParallel() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        AtomicInteger connectionCount = new AtomicInteger();
        List<String> queries = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier queriesStarted = new CyclicBarrier(4);
        when(dataSource.getConnection()).thenAnswer(call -> {
            connectionCount.incrementAndGet();
            Connection connection = mock(Connection.class);
            Statement statement = mock(Statement.class);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenAnswer(query -> {
                queries.add(query.getArgument(0));
                queriesStarted.await(5, TimeUnit.SECONDS);
                return mock(ResultSet.class);
            });
            return connection;
        });

        TransactionDashboardView dashboard = new TransactionDashboardService(
                new TransactionDashboardProperties(), dataSource).loadDashboard();

        assertThat(dashboard.available()).isTrue();
        assertThat(connectionCount.get()).isEqualTo(4);
        assertThat(queries).hasSize(4)
            .noneMatch(sql -> sql.contains("API_REQUEST_START_TIME"));
    }

    @Test
    void hourlyTrendQueriesOnlyRequestedWindowFromBaseTable() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        AtomicInteger connectionCount = new AtomicInteger();
        List<String> queries = Collections.synchronizedList(new ArrayList<>());
        ResultSet minuteRows = mock(ResultSet.class);
        ResultSet hourRows = mock(ResultSet.class);
        ResultSet totalRows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenAnswer(call -> {
            connectionCount.incrementAndGet();
            Connection connection = mock(Connection.class);
            Statement statement = mock(Statement.class);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenAnswer(query -> {
                String sql = query.getArgument(0);
                if (sql.contains("AS `APPROVED`")) {
                    assertThat(sql).contains("`bre_underwriting`.`transaction_details`")
                            .contains("AUTH_REQUEST_DATE_TIME >= CURDATE()")
                            .doesNotContain("`HRS` = 'TOTAL'");
                    return totalRows;
                }
                queries.add(sql);
                return sql.contains("DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%i') AS `MINS`")
                        ? minuteRows : hourRows;
            });
            return connection;
        });
        when(minuteRows.next()).thenReturn(true, false);
        when(hourRows.next()).thenReturn(true, false);
        when(totalRows.next()).thenReturn(true);
        when(totalRows.getObject(anyString())).thenAnswer(call -> switch (call.getArgument(0, String.class)) {
            case "TOTAL AUTH HITS" -> 900L;
            case "APPROVED" -> 850L;
            case "ECOM APPROVED" -> 700L;
            case "VOID" -> 12L;
            default -> 0L;
        });
        when(minuteRows.getString(2)).thenReturn("17");
        when(minuteRows.getString(3)).thenReturn("48");
        when(hourRows.getString(1)).thenReturn("2026-10-01");
        when(hourRows.getString(2)).thenReturn("17");
        when(minuteRows.getObject(anyString())).thenAnswer(call -> switch (call.getArgument(0, String.class)) {
            case "TOTAL APPROVED" -> 9L;
            case "VOID" -> 2L;
            case "DECLINED" -> 3L;
            default -> 0L;
        });
        when(hourRows.getObject(anyString())).thenAnswer(call -> switch (call.getArgument(0, String.class)) {
            case "VOID" -> 4L;
            case "DECLINED" -> 5L;
            default -> 0L;
        });

        TransactionDashboardView dashboard = new TransactionDashboardService(
                new TransactionDashboardProperties(), dataSource).loadHourlyTrend();

        assertThat(dashboard.available()).isTrue();
        assertThat(dashboard.minuteRows()).hasSize(1);
        assertThat(dashboard.minuteRows().get(0).voidCount()).isEqualTo(2);
        assertThat(dashboard.minuteRows().get(0).declinedCount()).isEqualTo(3);
        assertThat(dashboard.minuteRows().get(0).totalApproved()).isEqualTo(9);
        assertThat(dashboard.hourlyRows()).isEmpty();
        assertThat(dashboard.totalMetrics().totalHits()).isEqualTo(900);
        assertThat(dashboard.totalMetrics().ecomApproved()).isEqualTo(700);
        assertThat(dashboard.totalMetrics().approvedCount()).isEqualTo(850);
        assertThat(dashboard.totalMetrics().voidCount()).isEqualTo(12);
        assertThat(connectionCount.get()).isEqualTo(1);
        assertThat(queries).singleElement().satisfies(sql -> assertThat(sql)
            .contains("`bre_underwriting`.`transaction_details`")
            .contains("AUTH_REQUEST_DATE_TIME >= NOW() - INTERVAL 30 MINUTE")
            .contains("AND AUTH_REQUEST_DATE_TIME >= CURDATE()")
            .contains("FRUAD_GEN_REMARK = ''")
            .contains("DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%i') AS `MINS`")
            .contains("LIMIT 10")
            .doesNotContain("festival_summary_detailed_mins_new"));

        queries.clear();
        TransactionDashboardView hours = new TransactionDashboardService(
            new TransactionDashboardProperties(), dataSource).loadHourlyTrend(true);

        assertThat(hours.available()).isTrue();
        assertThat(hours.minuteRows()).isEmpty();
        assertThat(hours.hourlyRows()).hasSize(1);
        assertThat(hours.hourlyRows().get(0).voidCount()).isEqualTo(4);
        assertThat(hours.hourlyRows().get(0).declinedCount()).isEqualTo(5);
        assertThat(hours.totalMetrics().totalHits()).isEqualTo(900);
        assertThat(connectionCount.get()).isEqualTo(2);
        assertThat(queries).singleElement().satisfies(sql -> assertThat(sql)
            .contains("`bre_underwriting`.`transaction_details`")
            .contains("INTERVAL 10 HOUR")
            .contains("AND AUTH_REQUEST_DATE_TIME >= CURDATE()")
            .contains("FRUAD_GEN_REMARK = ''")
                .contains("DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%H')")
            .contains("LIMIT 11")
            .doesNotContain("festival_summary_detailed_new"));
    }

    @Test
    void hourlyTrendReportsMissingConfigurationWithoutFailingPage() {
        TransactionDashboardView dashboard = new TransactionDashboardService(
                new TransactionDashboardProperties(), null).loadHourlyTrend();

        assertThat(dashboard.available()).isFalse();
        assertThat(dashboard.errorMessage()).contains("TXN_DB_URL");
    }

    @Test
    void responseTimeDistributionLoadsDailyTotalsAndRecentWindowsForBothRequestTypes() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        List<String> queries = Collections.synchronizedList(new ArrayList<>());
        ResultSet dayRows = mock(ResultSet.class);
        ResultSet recentRows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenAnswer(call -> {
            Connection connection = mock(Connection.class);
            Statement statement = mock(Statement.class);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenAnswer(query -> {
                String sql = query.getArgument(0);
                queries.add(sql);
                return sql.contains("CURDATE()") ? dayRows : recentRows;
            });
            return connection;
        });
        when(dayRows.next()).thenReturn(true, true, true, true, true, true, false);
        when(dayRows.getString("transaction_date")).thenReturn("2026-10-05");
        when(dayRows.getString("req_type")).thenReturn(
            "ECOMELGB", "ECOMELGB", "AUTOTP", "ELGBLT", "BRESRCH", "MKPELGB");
        when(dayRows.getInt("bucket")).thenReturn(0, 5, 0, 0, 0, 0);
        when(dayRows.getObject("transaction_count")).thenReturn(36991L, 347L, 100L, 11L, 22L, 33L);
        when(recentRows.next()).thenReturn(true, true, false);
        when(recentRows.getString("req_type")).thenReturn("ECOMELGB", "AUTOTP");
        when(recentRows.getInt("bucket")).thenReturn(0, 2);
        when(recentRows.getObject("last_5_count")).thenReturn(4L, 0L);
        when(recentRows.getObject("last_15_count")).thenReturn(new BigDecimal("10"), new BigDecimal("0"));
        when(recentRows.getObject("last_60_count")).thenReturn(40L, 5L);

        ResponseTimeDistributionView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), dataSource).loadResponseTimeDistribution();

        assertThat(view.available()).isTrue();
        assertThat(view.transactionDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(view.ecomDay().totalCount()).isEqualTo(37338);
        assertThat(view.ecomDay().buckets()).extracting(ResponseTimeDistributionView.Bucket::rangeLabel)
                .containsExactly("0-50 MS", "50-100 MS", "100-150 MS", "150-200 MS", "200-300 MS", "300+ MS");
        assertThat(view.ecomDay().buckets().get(0).percentage()).isEqualTo(99.07);
        assertThat(view.ecomDay().buckets().get(1).transactionCount()).isZero();
        assertThat(view.ecomDay().buckets().get(5).percentage()).isEqualTo(0.93);
        assertThat(view.autotpDay().totalCount()).isEqualTo(100);
        assertThat(view.elgbltDay().totalCount()).isEqualTo(11);
        assertThat(view.bresrchDay().totalCount()).isEqualTo(22);
        assertThat(view.mkpelgbDay().totalCount()).isEqualTo(33);

        assertThat(view.recent()).extracting(ResponseTimeDistributionView.RecentPanel::reqType)
                .containsExactly("ECOMELGB", "AUTOTP");
        ResponseTimeDistributionView.RecentPanel ecom = view.recent().get(0);
        assertThat(ecom.title()).isEqualTo("ECOM Eligibility");
        assertThat(ecom.last5Total()).isEqualTo(4);
        assertThat(ecom.last15Total()).isEqualTo(10);
        assertThat(ecom.last60Total()).isEqualTo(40);
        assertThat(ecom.rows()).hasSize(6);
        assertThat(ecom.rows().get(0).last15Count()).isEqualTo(10);
        assertThat(ecom.rows().get(0).last15Percentage()).isEqualTo(100.0);
        assertThat(ecom.rows().get(0).last60Count()).isEqualTo(40);
        assertThat(ecom.rows().get(0).last60Percentage()).isEqualTo(100.0);
        ResponseTimeDistributionView.RecentPanel autotp = view.recent().get(1);
        assertThat(autotp.title()).isEqualTo("AUTH OTP");
        assertThat(autotp.last5Total()).isZero();
        assertThat(autotp.last15Total()).isZero();
        assertThat(autotp.rows().get(2).last60Count()).isEqualTo(5);

        assertThat(queries).hasSize(2)
            .allSatisfy(sql -> assertThat(sql)
                .contains("`bre_underwriting`.`transaction_details_srcreq_otp`")
                .doesNotContain("2025-12-30"))
            .anySatisfy(sql -> assertThat(sql)
                .contains("AUTH_REQUEST_DATE_TIME >= CURDATE()")
                .contains("REQ_TYPE IN ('ECOMELGB', 'AUTOTP', 'ELGBLT', 'BRESRCH', 'MKPELGB')"))
            .anySatisfy(sql -> assertThat(sql)
                .contains("INTERVAL 60 MINUTE")
                .contains("INTERVAL 15 MINUTE")
                .contains("INTERVAL 5 MINUTE")
                .contains("REQ_TYPE IN ('ECOMELGB', 'AUTOTP')"));
    }

    @Test
    void slowToneFlagsWindowsAboveOneAndTwoPercent() {
        ResponseTimeDistributionView.RecentPanel panel = new ResponseTimeDistributionView.RecentPanel(
                "ECOMELGB", 100, 1000, 1000, List.of(
            new ResponseTimeDistributionView.RecentRow("0-50 MS", 97, 0, 990, 0, 985, 0),
            new ResponseTimeDistributionView.RecentRow("200-300 MS", 2, 0, 5, 0, 10, 0),
            new ResponseTimeDistributionView.RecentRow("300+ MS", 1, 0, 5, 0, 5, 0)));

        assertThat(panel.slowTone(5)).isEqualTo("alert");
        assertThat(panel.slowTone(15)).isEqualTo("ok");
        assertThat(panel.slowTone(60)).isEqualTo("warn");
    }

    @Test
    void responseTimeDistributionReportsMissingConfigurationWithoutFailingPage() {
        ResponseTimeDistributionView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), null).loadResponseTimeDistribution();

        assertThat(view.available()).isFalse();
        assertThat(view.errorMessage()).contains("TXN_DB_URL");
    }

    @Test
    void channelHitsMapsCodesToNamesAndDropsUnknownChannels() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        List<String> queries = Collections.synchronizedList(new ArrayList<>());
        ResultSet rows = mock(ResultSet.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenAnswer(query -> {
            queries.add(query.getArgument(0));
            return rows;
        });
        when(rows.next()).thenReturn(true, true, true, false);
        when(rows.getString("channel")).thenReturn("5", " 61 ", "99");
        when(rows.getObject("hits")).thenReturn(300L, 100L, 50L);
        when(rows.getObject("approved")).thenReturn(new BigDecimal("270"), new BigDecimal("80"), new BigDecimal("50"));

        ChannelHitsView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), dataSource).loadChannelHits();

        assertThat(view.available()).isTrue();
        assertThat(view.totalHits()).isEqualTo(400);
        assertThat(view.totalApproved()).isEqualTo(350);
        assertThat(view.approvalRate()).isEqualTo(87.5);
        assertThat(view.channels()).extracting(ChannelHitsView.ChannelStat::name)
                .containsExactly("SFPOSRINV", "ECOM ECF", "ISDPOSRINV", "WEB-OTP", "IRCTC", "PAYTECH", "ECOM REMI", "CIA", "QR");
        ChannelHitsView.ChannelStat ecom = view.channels().get(1);
        assertThat(ecom.hits()).isEqualTo(300);
        assertThat(ecom.approved()).isEqualTo(270);
        assertThat(ecom.approvalRate()).isEqualTo(90.0);
        assertThat(ecom.sharePercent()).isEqualTo(75.0);
        assertThat(view.channels().get(0).approvalRate()).isEqualTo(80.0);
        assertThat(view.channels().get(2).hits()).isZero();
        assertThat(queries).singleElement().satisfies(sql -> assertThat(sql)
                .contains("`bre_underwriting`.`transaction_details`")
                .contains("PERTNER_EDC_CHANNEL IN ('61', '5', '20', '7', '13', '28', '10', '16', '27')")
                .contains("AUTH_REQUEST_DATE_TIME >= CURDATE()")
                .contains("FRUAD_GEN_REMARK = ''")
                .contains("TRANSACTION_STATUS = 'APPROVED'"));
    }

    @Test
    void channelHitsReportsMissingConfigurationWithoutFailingPage() {
        ChannelHitsView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), null).loadChannelHits();

        assertThat(view.available()).isFalse();
        assertThat(view.errorMessage()).contains("TXN_DB_URL");
    }

    @Test
    void loanAverageComputesPerDealerAndWeightedOverallAverage() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        List<String> queries = Collections.synchronizedList(new ArrayList<>());
        ResultSet rows = mock(ResultSet.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenAnswer(query -> {
            queries.add(query.getArgument(0));
            return rows;
        });
        when(rows.next()).thenReturn(true, false);
        when(rows.getObject("dealer_code")).thenReturn(new BigDecimal("139908"));
        when(rows.getObject("txn_count")).thenReturn(3L);
        when(rows.getObject("total_amount")).thenReturn(new BigDecimal("100000"));
        when(rows.getObject("below_count")).thenReturn(new BigDecimal("2"));
        when(rows.getObject("below_amount")).thenReturn(new BigDecimal("7000"));
        when(rows.getObject("above_count")).thenReturn(new BigDecimal("1"));
        when(rows.getObject("above_amount")).thenReturn(new BigDecimal("93000"));

        LoanAverageView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), dataSource).loadLoanAverage();

        assertThat(view.available()).isTrue();
        assertThat(view.totalCount()).isEqualTo(3);
        assertThat(view.totalAmount()).isEqualTo(100000);
        assertThat(view.overallAverage()).isEqualTo(33333.33);
        assertThat(view.dealers()).extracting(LoanAverageView.DealerLoanStat::name)
                .containsExactly("Flipkart", "Amazon");
        LoanAverageView.DealerLoanStat flipkart = view.dealers().get(0);
        assertThat(flipkart.averageAmount()).isEqualTo(33333.33);
        assertThat(flipkart.sharePercent()).isEqualTo(100.0);
        assertThat(flipkart.below5k().count()).isEqualTo(2);
        assertThat(flipkart.below5k().averageAmount()).isEqualTo(3500.0);
        assertThat(flipkart.from5k().averageAmount()).isEqualTo(93000.0);
        assertThat(view.dealers().get(1).below5k().averageAmount()).isZero();
        assertThat(view.dealers().get(1).count()).isZero();
        assertThat(view.dealers().get(1).averageAmount()).isZero();
        assertThat(queries).singleElement().satisfies(sql -> assertThat(sql)
                .contains("`bre_underwriting`.`transaction_details`")
                .contains("SUM(NET_LOAN_AMOUNT)")
                .contains("NET_LOAN_AMOUNT < 5000")
                .contains("NET_LOAN_AMOUNT >= 5000")
                .contains("DEALER_CODE_ETAILER IN (139908, 195298)")
                .contains("AUTH_REQUEST_DATE_TIME >= CURDATE()")
                .contains("FRUAD_GEN_REMARK = ''")
                .contains("TRANSACTION_STATUS = 'APPROVED'")
                .contains("PRODUCT IN ('ECF', 'ERF', 'VECF', 'VERF', 'PECF', 'PERF', 'PVECF', 'PVERF')"));
    }

    @Test
    void loanAverageReportsDatabaseFailureWithoutFailingPage() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("down"));

        LoanAverageView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), dataSource).loadLoanAverage();

        assertThat(view.available()).isFalse();
        assertThat(view.dealers()).isEmpty();
    }

    @Test
    void otpHitsReadsTotalsAndMarketplaceSplitFromOtpTable() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        List<String> queries = Collections.synchronizedList(new ArrayList<>());
        ResultSet rows = mock(ResultSet.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        ResultSet minuteRows = mock(ResultSet.class);
        when(statement.executeQuery(anyString())).thenAnswer(query -> {
            String sql = query.getArgument(0);
            queries.add(sql);
            return sql.contains("minute_key") ? minuteRows : rows;
        });
        when(minuteRows.next()).thenReturn(true, false);
        when(minuteRows.getString("minute_key")).thenReturn("2026-10-08 14:07");
        when(minuteRows.getObject("total_hits")).thenReturn(10L);
        when(minuteRows.getObject("total_approved")).thenReturn(new BigDecimal("9"));
        when(minuteRows.getObject("d139908_hits")).thenReturn(new BigDecimal("4"));
        when(minuteRows.getObject("d139908_approved")).thenReturn(new BigDecimal("2"));
        when(rows.next()).thenReturn(true, false);
        when(rows.getObject("total_hits")).thenReturn(1000L);
        when(rows.getObject("total_approved")).thenReturn(new BigDecimal("900"));
        when(rows.getObject("total_declined")).thenReturn(new BigDecimal("80"));
        when(rows.getObject("d139908_hits")).thenReturn(new BigDecimal("600"));
        when(rows.getObject("d139908_approved")).thenReturn(new BigDecimal("480"));
        when(rows.getObject("d139908_declined")).thenReturn(new BigDecimal("100"));
        when(rows.getObject("d195298_hits")).thenReturn(new BigDecimal("300"));
        when(rows.getObject("d195298_approved")).thenReturn(new BigDecimal("285"));
        when(rows.getObject("d195298_declined")).thenReturn(null);

        OtpHitsView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), dataSource).loadOtpHits();

        assertThat(view.available()).isTrue();
        assertThat(view.total().hits()).isEqualTo(1000);
        assertThat(view.total().approved()).isEqualTo(900);
        assertThat(view.total().declined()).isEqualTo(80);
        assertThat(view.total().approvalRate()).isEqualTo(90.0);
        assertThat(view.marketplaces()).extracting(OtpHitsView.OtpSegment::name)
                .containsExactly("Flipkart", "Amazon");
        OtpHitsView.OtpSegment flipkart = view.marketplaces().get(0);
        assertThat(flipkart.approvalRate()).isEqualTo(80.0);
        assertThat(flipkart.sharePercent()).isEqualTo(60.0);
        OtpHitsView.OtpSegment amazon = view.marketplaces().get(1);
        assertThat(amazon.approvalRate()).isEqualTo(95.0);
        assertThat(amazon.declined()).isZero();
        assertThat(view.minuteRows()).singleElement().satisfies(row -> {
            assertThat(row.label()).isEqualTo("14:07");
            assertThat(row.total().approvalRate()).isEqualTo(90.0);
            assertThat(row.marketplaces().get(0).approvalRate()).isEqualTo(50.0);
            assertThat(row.marketplaces().get(1).hits()).isZero();
        });
        assertThat(queries).hasSize(2);
        assertThat(queries.get(1))
                .contains("AUTH_REQUEST_DATE_TIME >= NOW() - INTERVAL 30 MINUTE")
                .contains("AND AUTH_REQUEST_DATE_TIME >= CURDATE()")
                .contains("LIMIT 10")
                .contains("GROUP BY minute_key")
                .contains("REQ_TYPE = 'AUTOTP'");
        assertThat(queries.get(0)).satisfies(sql -> assertThat(sql)
                .contains("`bre_underwriting`.`transaction_details_srcreq_otp`")
                .contains("REQ_TYPE = 'AUTOTP'")
                .contains("PRODUCT IN ('ECF', 'ERF', 'VECF', 'VERF', 'PECF', 'PERF', 'PVECF', 'PVERF')")
                .contains("AUTH_REQUEST_DATE_TIME >= CURDATE()")
                .contains("DEALER_CODE_ETAILER = 139908")
                .contains("DEALER_CODE_ETAILER = 195298")
                .contains("TRANSACTION_STATUS = 'DECLINED'")
                .doesNotContain("FRUAD_GEN_REMARK"));
    }

    @Test
    void otpHitsReportsMissingConfigurationWithoutFailingPage() {
        OtpHitsView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), null).loadOtpHits();

        assertThat(view.available()).isFalse();
        assertThat(view.errorMessage()).contains("TXN_DB_URL");
    }

    @Test
    void ecomEligibilityQueriesEcomelgbRequests() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        List<String> queries = Collections.synchronizedList(new ArrayList<>());
        ResultSet rows = mock(ResultSet.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenAnswer(query -> {
            queries.add(query.getArgument(0));
            return rows;
        });
        when(rows.next()).thenReturn(true, false, false);
        when(rows.getObject("total_hits")).thenReturn(200L);
        when(rows.getObject("total_approved")).thenReturn(new BigDecimal("150"));
        when(rows.getObject("d139908_hits")).thenReturn(new BigDecimal("100"));
        when(rows.getObject("d139908_approved")).thenReturn(new BigDecimal("90"));

        OtpHitsView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), dataSource).loadEcomEligibility();

        assertThat(view.available()).isTrue();
        assertThat(view.total().approvalRate()).isEqualTo(75.0);
        assertThat(view.marketplaces().get(0).approvalRate()).isEqualTo(90.0);
        assertThat(view.minuteRows()).isEmpty();
        assertThat(queries).hasSize(2).allSatisfy(sql -> assertThat(sql)
                .contains("REQ_TYPE = 'ECOMELGB'")
                .doesNotContain("AUTOTP"));
    }

    @Test
    void liveOverviewReadsEachTableOnceAndSumsBucketsIntoDayTotals() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        List<String> queries = Collections.synchronizedList(new ArrayList<>());
        ResultSet cutoffRows = mock(ResultSet.class);
        ResultSet authRows = mock(ResultSet.class);
        ResultSet otpRows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenAnswer(call -> {
            Connection connection = mock(Connection.class);
            Statement statement = mock(Statement.class);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenAnswer(query -> {
                String sql = query.getArgument(0);
                queries.add(sql);
                if (sql.contains("AS cutoff")) {
                    return cutoffRows;
                }
                return sql.contains("transaction_details_srcreq_otp") ? otpRows : authRows;
            });
            return connection;
        });
        when(cutoffRows.next()).thenReturn(true);
        when(cutoffRows.getString("cutoff")).thenReturn("2026-10-09 00:05:00");
        when(authRows.next()).thenReturn(true, true, false);
        when(authRows.getString("bucket")).thenReturn("older", "2026-10-09 00:04");
        when(authRows.getObject(anyString())).thenAnswer(call -> switch (call.getArgument(0, String.class)) {
            case "TOTAL AUTH HITS" -> 50L;
            case "FK HITS" -> 30L;
            case "l139908_count" -> 4L;
            case "l139908_amount" -> new BigDecimal("1000.60");
            default -> 0L;
        });
        when(otpRows.next()).thenReturn(true, true, true, false);
        when(otpRows.getString("req_type")).thenReturn("AUTOTP", "AUTOTP", "ECOMELGB");
        when(otpRows.getString("bucket")).thenReturn("older", "2026-10-09 00:04", "older");
        when(otpRows.getObject(anyString())).thenAnswer(call ->
                "total_hits".equals(call.getArgument(0, String.class)) ? 10L : 0L);

        LiveOverviewView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), dataSource).loadOverview();

        assertThat(queries).hasSize(3);
        assertThat(queries).filteredOn(sql -> !sql.contains("AS cutoff")).allSatisfy(sql -> assertThat(sql)
                .contains("AUTH_REQUEST_DATE_TIME < '2026-10-09 00:05:00'")
                .contains("CAST('2026-10-09 00:05:00' AS DATETIME) - INTERVAL 10 MINUTE")
                .contains("GROUP BY"));
        assertThat(view.auth().totalMetrics().totalHits()).isEqualTo(100);
        assertThat(view.auth().totalMetrics().flipkartHits()).isEqualTo(60);
        assertThat(view.auth().minuteRows()).extracting(row -> row.label()).first().isEqualTo("00:04");
        assertThat(view.loan().dealers().get(0).count()).isEqualTo(8);
        assertThat(view.loan().dealers().get(0).totalAmount()).isEqualTo(2001);
        assertThat(view.autoOtp().total().hits()).isEqualTo(20);
        assertThat(view.ecomEligibility().total().hits()).isEqualTo(10);
    }

    @Test
    void liveOverviewKeepsSectionsIndependentWhenUnconfigured() {
        LiveOverviewView view = new TransactionDashboardService(
                new TransactionDashboardProperties(), null).loadOverview();

        assertThat(view.auth().available()).isFalse();
        assertThat(view.autoOtp().available()).isFalse();
        assertThat(view.ecomEligibility().available()).isFalse();
        assertThat(view.loan().available()).isFalse();
    }
}
