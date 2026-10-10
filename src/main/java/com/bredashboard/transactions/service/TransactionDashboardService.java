package com.bredashboard.transactions.service;

import com.bredashboard.transactions.config.TransactionDashboardProperties;
import com.bredashboard.transactions.dto.ChannelHitsView;
import com.bredashboard.transactions.dto.LiveOverviewView;
import com.bredashboard.transactions.dto.LoanAverageView;
import com.bredashboard.transactions.dto.OtpHitsView;
import com.bredashboard.transactions.dto.ResponseTimeDistributionView;
import com.bredashboard.transactions.dto.TransactionDashboardView;
import com.bredashboard.transactions.dto.TransactionDashboardView.ChartPoint;
import com.bredashboard.transactions.dto.TransactionDashboardView.JourneyStage;
import com.bredashboard.transactions.dto.TransactionDashboardView.LatencySummary;
import com.bredashboard.transactions.dto.TransactionDashboardView.MarketplacePerformance;
import com.bredashboard.transactions.dto.TransactionDashboardView.MetricDelta;
import com.bredashboard.transactions.dto.TransactionDashboardView.MinuteChart;
import com.bredashboard.transactions.dto.TransactionDashboardView.ResponseTimeBucket;
import com.bredashboard.transactions.dto.TransactionDashboardView.TransactionMetrics;
import com.bredashboard.transactions.dto.TransactionDashboardView.VolumePoint;
import com.bredashboard.transactions.dto.OtpHitsView.OtpMinuteRow;
import com.bredashboard.transactions.dto.OtpHitsView.OtpSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TransactionDashboardService {

    private static final Logger log = LoggerFactory.getLogger(TransactionDashboardService.class);
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private static final int MINUTE_WINDOW = 15;
    private static final int HOURLY_TREND_MINUTE_WINDOW = 10;
    // Wider than the window so data lag or quiet minutes still yield a full set of latest rows.
    private static final int MINUTE_LOOKBACK = 30;
    private static final int HOUR_WINDOW = 5;
    private static final int HOURLY_TREND_HOUR_WINDOW = 10;
    private static final DateTimeFormatter MINUTE_LABEL_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CUTOFF_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Exclusive upper bound: start of the current (still incomplete) minute.
    private static final String CURRENT_MINUTE_SQL = "DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:00')";
    // A source with no rows for this long is treated as idle rather than lagging.
    private static final int SOURCE_IDLE_MINUTES = 15;
    private static final String ECOM_PRODUCT_SET = "('ECF', 'ERF', 'VECF', 'VERF', 'PECF', 'PERF', 'PVECF', 'PVERF')";
    /** Latency buckets are 100ms wide; anything at or beyond this index is grouped as an overflow bucket. */
    private static final long LATENCY_OVERFLOW_BUCKET = 6;
    private static final List<String> RESPONSE_TIME_RANGES = List.of(
            "0-50 MS", "50-100 MS", "100-150 MS", "150-200 MS", "200-300 MS", "300+ MS");
    private static final List<String> RESPONSE_TIME_REQ_TYPES = List.of(
            ResponseTimeDistributionView.ECOMELGB, ResponseTimeDistributionView.AUTOTP);
    private static final List<String> DAILY_COUNT_REQ_TYPES = List.of(
            ResponseTimeDistributionView.ECOMELGB,
            ResponseTimeDistributionView.AUTOTP,
            ResponseTimeDistributionView.ELGBLT,
            ResponseTimeDistributionView.BRESRCH,
            ResponseTimeDistributionView.MKPELGB);
    private static final String RESPONSE_TIME_BUCKET_SQL = """
            CASE
                WHEN TIMESTAMPDIFF(MICROSECOND, API_REQUEST_START_TIME, API_REQUEST_END_TIME) / 1000 < 50 THEN 0
                WHEN TIMESTAMPDIFF(MICROSECOND, API_REQUEST_START_TIME, API_REQUEST_END_TIME) / 1000 < 100 THEN 1
                WHEN TIMESTAMPDIFF(MICROSECOND, API_REQUEST_START_TIME, API_REQUEST_END_TIME) / 1000 < 150 THEN 2
                WHEN TIMESTAMPDIFF(MICROSECOND, API_REQUEST_START_TIME, API_REQUEST_END_TIME) / 1000 < 200 THEN 3
                WHEN TIMESTAMPDIFF(MICROSECOND, API_REQUEST_START_TIME, API_REQUEST_END_TIME) / 1000 < 300 THEN 4
                ELSE 5
            END""";

    // PERTNER_EDC_CHANNEL code -> display name, in display order; other codes are not shown.
    private static final Map<String, String> CHANNEL_NAMES = channelNames();
    // DEALER_CODE_ETAILER code -> marketplace name, in display order.
    private static final Map<String, String> LOAN_DEALER_NAMES = Map.of("139908", "Flipkart", "195298", "Amazon");
    private static final List<String> LOAN_DEALER_ORDER = List.of("139908", "195298");
    private static final int LOAN_BAND_THRESHOLD = 5000;
    private static final String OTP_REQ_TYPE = ResponseTimeDistributionView.AUTOTP;
    private static final String ECOM_ELIGIBILITY_REQ_TYPE = ResponseTimeDistributionView.ECOMELGB;
    private static final List<String> AUTH_OVERVIEW_COLUMNS = List.of(
            "TOTAL AUTH HITS", "TOTAL ECOM HITS", "ECOM APPROVED", "FK HITS", "FK APPROVED",
            "AZ HITS", "AZ APPROVED", "VOID", "DECLINED", "TOTAL APPROVED");

    private final TransactionDashboardProperties properties;
    private final DataSource reportingDataSource;
    private final Object overviewLock = new Object();
    private OverviewSnapshot overviewSnapshot;

    private record OverviewSnapshot(LocalDateTime cutoff, LiveOverviewView view) {
    }

    public TransactionDashboardService(
            TransactionDashboardProperties properties,
            @Autowired(required = false) @Qualifier("transactionReportingDataSource") DataSource reportingDataSource
    ) {
        this.properties = properties;
        this.reportingDataSource = reportingDataSource;
    }

    public TransactionDashboardView loadDashboard() {
        if (!StringUtils.hasText(properties.getJdbcUrl()) && reportingDataSource == null) {
            return TransactionDashboardView.unavailable(
                    "MySQL",
                    "Transaction analytics are unavailable. Set TXN_DB_URL, TXN_DB_USERNAME, and TXN_DB_PASSWORD."
            );
        }

        try {
            String minuteView = qualified(properties.getMinuteView());
            String hourlyView = qualified(properties.getHourlyView());
            String otpTable = qualified(properties.getOtpTable());
            String transactionTable = qualified("transaction_details");
                // These reads use independent views/tables and connections, so run them together.
                CompletableFuture<List<VolumePoint>> minutes = readAsync(connection ->
                    readVolumePoints(connection, minuteQuery(minuteView), true));
                CompletableFuture<List<VolumePoint>> hours = readAsync(connection ->
                    readVolumePoints(connection, hourlyQuery(hourlyView), false));
                CompletableFuture<TransactionMetrics> totalMetrics = readAsync(connection ->
                    readDayTotalMetrics(connection, transactionTable, CURRENT_MINUTE_SQL));
                CompletableFuture<Long> eligibilityChecks = readAsync(connection ->
                    readEligibilityCount(connection, otpTable));

                return build(
                    "Live MySQL",
                    minutes.join(),
                    hours.join(),
                    List.of(),
                    eligibilityChecks.join(),
                    totalMetrics.join()
                );
            } catch (CompletionException | IllegalArgumentException ex) {
            log.warn("Unable to load transaction dashboard: {}", ex.getMessage());
            return TransactionDashboardView.unavailable(
                    "Live MySQL",
                    "Transaction analytics are temporarily unavailable. Check the database configuration and view access."
            );
        }
    }

    public TransactionDashboardView loadHourlyTrend() {
        return loadHourlyTrend(false);
    }

    public TransactionDashboardView loadHourlyTrend(boolean showHours) {
        return loadHourlyTrend(showHours, CURRENT_MINUTE_SQL);
    }

    private TransactionDashboardView loadHourlyTrend(boolean showHours, String upperBound) {
        if (!StringUtils.hasText(properties.getJdbcUrl()) && reportingDataSource == null) {
            return TransactionDashboardView.unavailable(
                    "MySQL",
                    "Transaction analytics are unavailable. Set TXN_DB_URL, TXN_DB_USERNAME, and TXN_DB_PASSWORD."
            );
        }

        try (Connection connection = openConnection()) {
            String transactionTable = qualified("transaction_details");
            TransactionMetrics dayTotals = readDayTotalMetrics(connection, transactionTable, upperBound);
            if (showHours) {
                List<VolumePoint> hours = readVolumePoints(
                        connection, hourlyTrendHourQuery(transactionTable, upperBound), false);
                return build("Live MySQL", List.of(), hours, List.of(), 0, dayTotals, HOURLY_TREND_HOUR_WINDOW);
            }
            List<VolumePoint> minutes = readVolumePoints(
                    connection, hourlyTrendMinuteQuery(transactionTable, upperBound), true, true);
            return build("Live MySQL", minutes, List.of(), List.of(), 0, dayTotals);
        } catch (SQLException | IllegalArgumentException ex) {
            log.warn("Unable to load hourly trend: {}", ex.getMessage());
            return TransactionDashboardView.unavailable(
                    "Live MySQL",
                    "Transaction analytics are temporarily unavailable. Check the database configuration and view access."
            );
        }
    }

    public ResponseTimeDistributionView loadResponseTimeDistribution() {
        if (!StringUtils.hasText(properties.getJdbcUrl()) && reportingDataSource == null) {
            return ResponseTimeDistributionView.unavailable(
                    "MySQL",
                    "Transaction analytics are unavailable. Set TXN_DB_URL, TXN_DB_USERNAME, and TXN_DB_PASSWORD."
            );
        }

        try {
            String otpTable = qualified(properties.getOtpTable());
            CompletableFuture<DayCounts> day = readAsync(connection -> readDayCounts(connection, otpTable));
            CompletableFuture<RecentCounts> recent = readAsync(connection -> readRecentCounts(connection, otpTable));
            DayCounts dayCounts = day.join();
            RecentCounts recentCounts = recent.join();

            List<ResponseTimeDistributionView.DailySummary> daily = new ArrayList<>();
            List<ResponseTimeDistributionView.RecentPanel> panels = new ArrayList<>();
            for (String reqType : DAILY_COUNT_REQ_TYPES) {
                long[] dayBuckets = dayCounts.counts().get(reqType);
                long dayTotal = sum(dayBuckets);
                List<ResponseTimeDistributionView.Bucket> buckets = new ArrayList<>();
                for (int i = 0; i < RESPONSE_TIME_RANGES.size(); i++) {
                    buckets.add(new ResponseTimeDistributionView.Bucket(
                            RESPONSE_TIME_RANGES.get(i), dayBuckets[i], percentOf(dayBuckets[i], dayTotal)));
                }
                daily.add(new ResponseTimeDistributionView.DailySummary(reqType, dayTotal, buckets));
            }

            for (String reqType : RESPONSE_TIME_REQ_TYPES) {
                long[] last5 = recentCounts.last5().get(reqType);
                long[] last15 = recentCounts.last15().get(reqType);
                long[] last60 = recentCounts.last60().get(reqType);
                long last5Total = sum(last5);
                long last15Total = sum(last15);
                long last60Total = sum(last60);
                List<ResponseTimeDistributionView.RecentRow> rows = new ArrayList<>();
                for (int i = 0; i < RESPONSE_TIME_RANGES.size(); i++) {
                    rows.add(new ResponseTimeDistributionView.RecentRow(
                            RESPONSE_TIME_RANGES.get(i),
                            last5[i], percentOf(last5[i], last5Total),
                            last15[i], percentOf(last15[i], last15Total),
                            last60[i], percentOf(last60[i], last60Total)));
                }
                panels.add(new ResponseTimeDistributionView.RecentPanel(
                    reqType, last5Total, last15Total, last60Total, rows));
            }
            return new ResponseTimeDistributionView(
                    Instant.now(), "Live MySQL", true, null, dayCounts.date(), daily, panels);
        } catch (CompletionException | IllegalArgumentException ex) {
            log.warn("Unable to load response time distribution: {}",
                    ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            return ResponseTimeDistributionView.unavailable(
                    "Live MySQL",
                    "Transaction analytics are temporarily unavailable. Check the database configuration and view access."
            );
        }
    }

    public ChannelHitsView loadChannelHits() {
        if (!StringUtils.hasText(properties.getJdbcUrl()) && reportingDataSource == null) {
            return ChannelHitsView.unavailable(
                    "MySQL",
                    "Transaction analytics are unavailable. Set TXN_DB_URL, TXN_DB_USERNAME, and TXN_DB_PASSWORD."
            );
        }

        try (Connection connection = openConnection()) {
            Map<String, long[]> counts = new LinkedHashMap<>();
            for (String code : CHANNEL_NAMES.keySet()) {
                counts.put(code, new long[2]);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(channelHitsQuery(qualified("transaction_details")))) {
                while (rs.next()) {
                    String code = rs.getString("channel");
                    long[] values = code == null ? null : counts.get(code.trim());
                    if (values != null) {
                        values[0] += number(rs, "hits");
                        values[1] += number(rs, "approved");
                    }
                }
            }

            long totalHits = 0;
            long totalApproved = 0;
            for (long[] values : counts.values()) {
                totalHits += values[0];
                totalApproved += values[1];
            }
            List<ChannelHitsView.ChannelStat> channels = new ArrayList<>();
            for (Map.Entry<String, long[]> entry : counts.entrySet()) {
                long hits = entry.getValue()[0];
                long approved = entry.getValue()[1];
                channels.add(new ChannelHitsView.ChannelStat(
                        entry.getKey(), CHANNEL_NAMES.get(entry.getKey()), hits, approved,
                        percentOf(approved, hits), percentOf(hits, totalHits)));
            }
            return new ChannelHitsView(Instant.now(), "Live MySQL", true, null, LocalDate.now(),
                    totalHits, totalApproved, percentOf(totalApproved, totalHits), channels);
        } catch (SQLException | IllegalArgumentException ex) {
            log.warn("Unable to load channel hits: {}", ex.getMessage());
            return ChannelHitsView.unavailable(
                    "Live MySQL",
                    "Transaction analytics are temporarily unavailable. Check the database configuration and view access."
            );
        }
    }

    public LoanAverageView loadLoanAverage() {
        return loadLoanAverage(CURRENT_MINUTE_SQL);
    }

    private LoanAverageView loadLoanAverage(String upperBound) {
        if (!StringUtils.hasText(properties.getJdbcUrl()) && reportingDataSource == null) {
            return LoanAverageView.unavailable(
                    "MySQL",
                    "Transaction analytics are unavailable. Set TXN_DB_URL, TXN_DB_USERNAME, and TXN_DB_PASSWORD."
            );
        }

        try (Connection connection = openConnection()) {
            Map<String, long[]> totals = new LinkedHashMap<>();
            for (String code : LOAN_DEALER_ORDER) {
                totals.put(code, new long[6]);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         loanAverageQuery(qualified("transaction_details"), upperBound))) {
                while (rs.next()) {
                    long[] values = totals.get(Long.toString(number(rs, "dealer_code")));
                    if (values != null) {
                        values[0] += number(rs, "txn_count");
                        values[1] += number(rs, "total_amount");
                        values[2] += number(rs, "below_count");
                        values[3] += number(rs, "below_amount");
                        values[4] += number(rs, "above_count");
                        values[5] += number(rs, "above_amount");
                    }
                }
            }
            return loanView(totals);
        } catch (SQLException | IllegalArgumentException ex) {
            log.warn("Unable to load loan averages: {}", ex.getMessage());
            return LoanAverageView.unavailable(
                    "Live MySQL",
                    "Transaction analytics are temporarily unavailable. Check the database configuration and view access."
            );
        }
    }

    // Per dealer: {count, amount, belowCount, belowAmount, aboveCount, aboveAmount}.
    private static LoanAverageView loanView(Map<String, long[]> totals) {
        long totalCount = 0;
        long totalAmount = 0;
        for (long[] values : totals.values()) {
            totalCount += values[0];
            totalAmount += values[1];
        }
        List<LoanAverageView.DealerLoanStat> dealers = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : totals.entrySet()) {
            long[] v = entry.getValue();
            dealers.add(new LoanAverageView.DealerLoanStat(
                    entry.getKey(), LOAN_DEALER_NAMES.get(entry.getKey()), v[0], v[1],
                    averageOf(v[1], v[0]), percentOf(v[1], totalAmount),
                    new LoanAverageView.LoanBand(v[2], v[3], averageOf(v[3], v[2])),
                    new LoanAverageView.LoanBand(v[4], v[5], averageOf(v[5], v[4]))));
        }
        return new LoanAverageView(Instant.now(), "Live MySQL", true, null, LocalDate.now(),
                totalCount, totalAmount, averageOf(totalAmount, totalCount), dealers);
    }

    private static BigDecimal amount(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString().trim());
    }

    private String loanAverageQuery(String table, String upperBound) {
        return """
                SELECT DEALER_CODE_ETAILER AS dealer_code,
                       COUNT(*) AS txn_count,
                       COALESCE(SUM(NET_LOAN_AMOUNT), 0) AS total_amount,
                       SUM(CASE WHEN NET_LOAN_AMOUNT < %4$d THEN 1 ELSE 0 END) AS below_count,
                       COALESCE(SUM(CASE WHEN NET_LOAN_AMOUNT < %4$d THEN NET_LOAN_AMOUNT END), 0) AS below_amount,
                       SUM(CASE WHEN NET_LOAN_AMOUNT >= %4$d THEN 1 ELSE 0 END) AS above_count,
                       COALESCE(SUM(CASE WHEN NET_LOAN_AMOUNT >= %4$d THEN NET_LOAN_AMOUNT END), 0) AS above_amount
                FROM %1$s
                WHERE AUTH_REQUEST_DATE_TIME >= CURDATE()
                  AND AUTH_REQUEST_DATE_TIME < %5$s
                  AND FRUAD_GEN_REMARK = ''
                  AND TRANSACTION_STATUS = 'APPROVED'
                  AND PRODUCT IN %2$s
                  AND DEALER_CODE_ETAILER IN (%3$s)
                GROUP BY DEALER_CODE_ETAILER
                """.formatted(table, ECOM_PRODUCT_SET, String.join(", ", LOAN_DEALER_ORDER), LOAN_BAND_THRESHOLD,
                upperBound);
    }

    public LiveOverviewView loadOverview() {
        LocalDateTime cutoff = resolveSharedCutoff();
        if (cutoff == null) {
            return loadOverview(CURRENT_MINUTE_SQL);
        }
        // One snapshot per cutoff minute, served to every user; the cutoff never moves backwards within a day.
        synchronized (overviewLock) {
            OverviewSnapshot snapshot = overviewSnapshot;
            if (snapshot != null
                    && snapshot.cutoff().toLocalDate().equals(cutoff.toLocalDate())
                    && !snapshot.cutoff().isBefore(cutoff)) {
                return snapshot.view();
            }
            LiveOverviewView view = loadOverview("'" + cutoff.format(CUTOFF_FORMAT) + "'");
            if (isComplete(view)) {
                overviewSnapshot = new OverviewSnapshot(cutoff, view);
            }
            return view;
        }
    }

    private static boolean isComplete(LiveOverviewView view) {
        return view.auth().available() && view.autoOtp().available()
                && view.ecomEligibility().available() && view.loan().available();
    }

    private LiveOverviewView loadOverview(String cutoff) {
        if (!StringUtils.hasText(properties.getJdbcUrl()) && reportingDataSource == null) {
            String message = "Transaction analytics are unavailable. Set TXN_DB_URL, TXN_DB_USERNAME, and TXN_DB_PASSWORD.";
            return new LiveOverviewView(Instant.now(),
                    TransactionDashboardView.unavailable("MySQL", message),
                    OtpHitsView.unavailable("MySQL", message),
                    OtpHitsView.unavailable("MySQL", message),
                    LoanAverageView.unavailable("MySQL", message));
        }
        CompletableFuture<AuthOverview> authAndLoan = readAsync(connection -> readAuthOverview(connection, cutoff));
        CompletableFuture<Map<String, OtpHitsView>> otp = readAsync(connection -> readOtpOverview(connection, cutoff));

        String unavailable = "Transaction analytics are temporarily unavailable. Check the database configuration and view access.";
        TransactionDashboardView auth;
        LoanAverageView loan;
        try {
            AuthOverview result = authAndLoan.join();
            auth = result.auth();
            loan = result.loan();
        } catch (CompletionException ex) {
            log.warn("Unable to load overview auth/loan: {}", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            auth = TransactionDashboardView.unavailable("Live MySQL", unavailable);
            loan = LoanAverageView.unavailable("Live MySQL", unavailable);
        }
        OtpHitsView autoOtp;
        OtpHitsView eligibility;
        try {
            Map<String, OtpHitsView> views = otp.join();
            autoOtp = views.get(OTP_REQ_TYPE);
            eligibility = views.get(ECOM_ELIGIBILITY_REQ_TYPE);
        } catch (CompletionException ex) {
            log.warn("Unable to load overview OTP: {}", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            autoOtp = OtpHitsView.unavailable("Live MySQL", unavailable);
            eligibility = OtpHitsView.unavailable("Live MySQL", unavailable);
        }
        return alignOverviewMinutes(new LiveOverviewView(Instant.now(), auth, autoOtp, eligibility, loan));
    }

    private record AuthOverview(TransactionDashboardView auth, LoanAverageView loan) {
    }

    // Each overview query buckets today's rows into the last N minutes (one row each) plus a single 'older' row.
    private static String overviewBucketSql(String cutoff) {
        return """
                CASE WHEN AUTH_REQUEST_DATE_TIME >= CAST(%s AS DATETIME) - INTERVAL %d MINUTE
                     THEN DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%Y-%%m-%%d %%H:%%i') ELSE 'older' END"""
                .formatted(cutoff, HOURLY_TREND_MINUTE_WINDOW);
    }

    private static String minuteLabel(String bucket) {
        return bucket.length() < 16 ? bucket : bucket.substring(11, 16);
    }

    private String authOverviewQuery(String table, String cutoff) {
        String loanColumns = LOAN_DEALER_ORDER.stream()
                .map(code -> {
                    String approved = "TRANSACTION_STATUS = 'APPROVED' AND PRODUCT IN %s AND DEALER_CODE_ETAILER = %s"
                            .formatted(ECOM_PRODUCT_SET, code);
                    return """
                            SUM(CASE WHEN %1$s THEN 1 ELSE 0 END) AS l%2$s_count,
                            COALESCE(SUM(CASE WHEN %1$s THEN NET_LOAN_AMOUNT END), 0) AS l%2$s_amount,
                            SUM(CASE WHEN %1$s AND NET_LOAN_AMOUNT < %3$d THEN 1 ELSE 0 END) AS l%2$s_below_count,
                            COALESCE(SUM(CASE WHEN %1$s AND NET_LOAN_AMOUNT < %3$d THEN NET_LOAN_AMOUNT END), 0) AS l%2$s_below_amount,
                            SUM(CASE WHEN %1$s AND NET_LOAN_AMOUNT >= %3$d THEN 1 ELSE 0 END) AS l%2$s_above_count,
                            COALESCE(SUM(CASE WHEN %1$s AND NET_LOAN_AMOUNT >= %3$d THEN NET_LOAN_AMOUNT END), 0) AS l%2$s_above_amount"""
                            .formatted(approved, code, LOAN_BAND_THRESHOLD);
                })
                .collect(Collectors.joining(",\n"));
        return """
                SELECT %3$s AS bucket,
                       COUNT(*) AS `TOTAL AUTH HITS`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'APPROVED' THEN 1 ELSE 0 END) AS `TOTAL APPROVED`,
                       SUM(CASE WHEN PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `TOTAL ECOM HITS`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'APPROVED' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `ECOM APPROVED`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '139908' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `FK HITS`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '139908' AND TRANSACTION_STATUS = 'APPROVED'
                                         AND PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `FK APPROVED`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '195298' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `AZ HITS`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '195298' AND TRANSACTION_STATUS = 'APPROVED'
                                         AND PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `AZ APPROVED`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'VOID' THEN 1 ELSE 0 END) AS `VOID`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'DECLINED' THEN 1 ELSE 0 END) AS `DECLINED`,
                %5$s
                FROM %1$s
                WHERE AUTH_REQUEST_DATE_TIME >= CURDATE()
                  AND AUTH_REQUEST_DATE_TIME < %4$s
                  AND FRUAD_GEN_REMARK = ''
                GROUP BY bucket
                """.formatted(table, ECOM_PRODUCT_SET, overviewBucketSql(cutoff), cutoff, loanColumns);
    }

    private AuthOverview readAuthOverview(Connection connection, String cutoff) throws SQLException {
        long[] day = new long[AUTH_OVERVIEW_COLUMNS.size()];
        Map<String, long[]> loanCounts = new LinkedHashMap<>();
        Map<String, BigDecimal[]> loanAmounts = new LinkedHashMap<>();
        for (String code : LOAN_DEALER_ORDER) {
            loanCounts.put(code, new long[3]);
            loanAmounts.put(code, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }
        Map<String, VolumePoint> minutes = new TreeMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(authOverviewQuery(qualified("transaction_details"), cutoff))) {
            while (rs.next()) {
                long[] row = new long[AUTH_OVERVIEW_COLUMNS.size()];
                for (int i = 0; i < row.length; i++) {
                    row[i] = number(rs, AUTH_OVERVIEW_COLUMNS.get(i));
                    day[i] += row[i];
                }
                for (String code : LOAN_DEALER_ORDER) {
                    long[] counts = loanCounts.get(code);
                    BigDecimal[] amounts = loanAmounts.get(code);
                    String prefix = "l" + code;
                    counts[0] += number(rs, prefix + "_count");
                    counts[1] += number(rs, prefix + "_below_count");
                    counts[2] += number(rs, prefix + "_above_count");
                    amounts[0] = amounts[0].add(amount(rs, prefix + "_amount"));
                    amounts[1] = amounts[1].add(amount(rs, prefix + "_below_amount"));
                    amounts[2] = amounts[2].add(amount(rs, prefix + "_above_amount"));
                }
                String bucket = rs.getString("bucket");
                if (bucket != null && !"older".equals(bucket)) {
                    minutes.put(bucket, point(minuteLabel(bucket), row[0], row[1], row[2], row[3], row[4],
                            row[5], row[6], row[7], row[8], row[9]));
                }
            }
        }
        TransactionMetrics dayTotals = new TransactionMetrics(
                day[0], day[1], day[2], percentOf(day[2], day[1]),
                day[3], day[4], percentOf(day[4], day[3]),
                day[5], day[6], percentOf(day[6], day[5]),
                day[7], day[8], day[9]);
        TransactionDashboardView auth = build("Live MySQL", new ArrayList<>(minutes.values()), List.of(), List.of(), 0, dayTotals);

        Map<String, long[]> loanTotals = new LinkedHashMap<>();
        for (String code : LOAN_DEALER_ORDER) {
            long[] counts = loanCounts.get(code);
            BigDecimal[] amounts = loanAmounts.get(code);
            loanTotals.put(code, new long[] {counts[0], amounts[0].longValue(), counts[1], amounts[1].longValue(),
                    counts[2], amounts[2].longValue()});
        }
        return new AuthOverview(auth, loanView(loanTotals));
    }

    private String otpOverviewQuery(String table, String cutoff) {
        return """
                SELECT REQ_TYPE AS req_type,
                       %3$s AS bucket,
                %1$s
                FROM %2$s
                WHERE AUTH_REQUEST_DATE_TIME >= CURDATE()
                  AND AUTH_REQUEST_DATE_TIME < %4$s
                  AND REQ_TYPE IN ('%5$s', '%6$s')
                  AND PRODUCT IN %7$s
                GROUP BY REQ_TYPE, bucket
                """.formatted(otpCountColumns(), table, overviewBucketSql(cutoff), cutoff,
                OTP_REQ_TYPE, ECOM_ELIGIBILITY_REQ_TYPE, ECOM_PRODUCT_SET);
    }

    private Map<String, OtpHitsView> readOtpOverview(Connection connection, String cutoff) throws SQLException {
        Map<String, long[]> totals = new LinkedHashMap<>();
        Map<String, Map<String, long[]>> dealers = new LinkedHashMap<>();
        Map<String, TreeMap<String, OtpMinuteRow>> minutes = new LinkedHashMap<>();
        for (String reqType : List.of(OTP_REQ_TYPE, ECOM_ELIGIBILITY_REQ_TYPE)) {
            totals.put(reqType, new long[3]);
            dealers.put(reqType, emptyDealerCounts());
            minutes.put(reqType, new TreeMap<>(Collections.reverseOrder()));
        }
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(otpOverviewQuery(qualified(properties.getOtpTable()), cutoff))) {
            while (rs.next()) {
                String reqType = rs.getString("req_type");
                long[] total = reqType == null ? null : totals.get(reqType.trim());
                if (total == null) {
                    continue;
                }
                reqType = reqType.trim();
                total[0] += number(rs, "total_hits");
                total[1] += number(rs, "total_approved");
                total[2] += number(rs, "total_declined");
                for (Map.Entry<String, long[]> entry : dealers.get(reqType).entrySet()) {
                    String prefix = "d" + entry.getKey();
                    entry.getValue()[0] += number(rs, prefix + "_hits");
                    entry.getValue()[1] += number(rs, prefix + "_approved");
                    entry.getValue()[2] += number(rs, prefix + "_declined");
                }
                String bucket = rs.getString("bucket");
                if (bucket != null && !"older".equals(bucket)) {
                    OtpCounts counts = readOtpCounts(rs);
                    minutes.get(reqType).put(bucket,
                            new OtpMinuteRow(minuteLabel(bucket), counts.total(), counts.marketplaces()));
                }
            }
        }
        Map<String, OtpHitsView> views = new LinkedHashMap<>();
        for (String reqType : totals.keySet()) {
            long[] total = totals.get(reqType);
            OtpCounts day = otpCounts(total[0], total[1], total[2], dealers.get(reqType));
            views.put(reqType, new OtpHitsView(Instant.now(), "Live MySQL", true, null, LocalDate.now(),
                    day.total(), day.marketplaces(), List.copyOf(minutes.get(reqType).values())));
        }
        return views;
    }

    /**
     * The auth and OTP tables are loaded at different speeds, so every overview section is cut at the
     * start of the newest minute that the slowest source has reached; that minute itself may still be loading.
     */
    private LocalDateTime resolveSharedCutoff() {
        if (!StringUtils.hasText(properties.getJdbcUrl()) && reportingDataSource == null) {
            return null;
        }
        String latestRow = """
                COALESCE((SELECT MAX(AUTH_REQUEST_DATE_TIME) FROM %s
                          WHERE AUTH_REQUEST_DATE_TIME >= NOW() - INTERVAL %d MINUTE
                            AND AUTH_REQUEST_DATE_TIME < NOW()), NOW())""";
        String sql = "SELECT DATE_FORMAT(LEAST(%s, %s), '%%Y-%%m-%%d %%H:%%i:00') AS cutoff".formatted(
                latestRow.formatted(qualified("transaction_details"), SOURCE_IDLE_MINUTES),
                latestRow.formatted(qualified(properties.getOtpTable()), SOURCE_IDLE_MINUTES));
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            String value = rs.next() ? rs.getString("cutoff") : null;
            return value == null ? null : LocalDateTime.parse(value, CUTOFF_FORMAT);
        } catch (SQLException | RuntimeException ex) {
            log.warn("Unable to resolve shared overview cutoff: {}", ex.getMessage());
            return null;
        }
    }

    static LiveOverviewView alignOverviewMinutes(LiveOverviewView overview) {
        List<String> labels = sharedMinuteLabels(overview);
        if (labels.isEmpty()) {
            return overview;
        }
        return new LiveOverviewView(
                overview.generatedAt(),
                alignAuthMinutes(overview.auth(), labels),
                alignOtpMinutes(overview.autoOtp(), labels),
                alignOtpMinutes(overview.ecomEligibility(), labels),
                overview.loan());
    }

    private static List<String> sharedMinuteLabels(LiveOverviewView overview) {
        List<LocalTime> observed = new ArrayList<>();
        overview.auth().minuteRows().stream().map(VolumePoint::label).map(LocalTime::parse).forEach(observed::add);
        overview.autoOtp().minuteRows().stream().map(OtpMinuteRow::label).map(LocalTime::parse).forEach(observed::add);
        overview.ecomEligibility().minuteRows().stream().map(OtpMinuteRow::label).map(LocalTime::parse).forEach(observed::add);
        List<LocalTime> sorted = observed.stream().distinct().sorted().toList();
        if (sorted.isEmpty()) {
            return List.of();
        }

        int latestIndex = 0;
        int largestGap = -1;
        for (int index = 0; index < sorted.size(); index++) {
            int currentMinute = sorted.get(index).getHour() * 60 + sorted.get(index).getMinute();
            LocalTime next = sorted.get((index + 1) % sorted.size());
            int nextMinute = next.getHour() * 60 + next.getMinute();
            int gap = Math.floorMod(nextMinute - currentMinute, 24 * 60);
            if (gap > largestGap) {
                largestGap = gap;
                latestIndex = index;
            }
        }

        LocalTime latest = sorted.get(latestIndex);
        List<String> labels = new ArrayList<>(HOURLY_TREND_MINUTE_WINDOW);
        for (int offset = 0; offset < HOURLY_TREND_MINUTE_WINDOW; offset++) {
            labels.add(latest.minusMinutes(offset).format(MINUTE_LABEL_FORMAT));
        }
        return List.copyOf(labels);
    }

    private static TransactionDashboardView alignAuthMinutes(TransactionDashboardView view, List<String> labels) {
        if (!view.available()) {
            return view;
        }
        Map<String, VolumePoint> rowsByLabel = view.minuteRows().stream()
                .collect(Collectors.toMap(VolumePoint::label, row -> row));
        List<VolumePoint> aligned = labels.stream().map(label -> rowsByLabel.getOrDefault(label,
                new VolumePoint(label, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0))).toList();
        return new TransactionDashboardView(view.generatedAt(), view.sourceLabel(), view.available(),
                view.errorMessage(), view.totalMetrics(), view.last15Minutes(), view.delta(), view.journey(),
                view.marketplaces(), aligned, view.hourlyRows(), view.responseTimeBuckets(), view.minuteChart(),
                view.latencySummary());
    }

    private static OtpHitsView alignOtpMinutes(OtpHitsView view, List<String> labels) {
        if (!view.available()) {
            return view;
        }
        Map<String, OtpMinuteRow> rowsByLabel = view.minuteRows().stream()
                .collect(Collectors.toMap(OtpMinuteRow::label, row -> row));
        List<OtpMinuteRow> aligned = labels.stream().map(label -> rowsByLabel.getOrDefault(label,
                new OtpMinuteRow(label,
                        new OtpSegment("", "Total", 0, 0, 0, 0, 0),
                        view.marketplaces().stream()
                                .map(market -> new OtpSegment(market.code(), market.name(), 0, 0, 0, 0, 0))
                                .toList()))).toList();
        return new OtpHitsView(view.generatedAt(), view.sourceLabel(), view.available(), view.errorMessage(),
                view.transactionDate(), view.total(), view.marketplaces(), aligned);
    }

    public OtpHitsView loadOtpHits() {
        return loadOtpRequests(OTP_REQ_TYPE, "OTP hits", CURRENT_MINUTE_SQL);
    }

    public OtpHitsView loadEcomEligibility() {
        return loadOtpRequests(ECOM_ELIGIBILITY_REQ_TYPE, "e-com eligibility", CURRENT_MINUTE_SQL);
    }

    private OtpHitsView loadOtpRequests(String reqType, String logLabel, String upperBound) {
        if (!StringUtils.hasText(properties.getJdbcUrl()) && reportingDataSource == null) {
            return OtpHitsView.unavailable(
                    "MySQL",
                    "Transaction analytics are unavailable. Set TXN_DB_URL, TXN_DB_USERNAME, and TXN_DB_PASSWORD."
            );
        }

        try (Connection connection = openConnection()) {
            String table = qualified(properties.getOtpTable());
            OtpCounts today = null;
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(otpHitsQuery(table, reqType, upperBound))) {
                if (rs.next()) {
                    today = readOtpCounts(rs);
                }
            }
            List<OtpHitsView.OtpMinuteRow> minuteRows = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(otpMinuteQuery(table, reqType, upperBound))) {
                while (rs.next()) {
                    String key = rs.getString("minute_key");
                    OtpCounts counts = readOtpCounts(rs);
                    minuteRows.add(new OtpHitsView.OtpMinuteRow(
                            key == null || key.length() < 16 ? String.valueOf(key) : key.substring(11, 16),
                            counts.total(), counts.marketplaces()));
                }
            }
            if (today == null) {
                today = otpCounts(0, 0, 0, emptyDealerCounts());
            }
            return new OtpHitsView(Instant.now(), "Live MySQL", true, null, LocalDate.now(),
                    today.total(), today.marketplaces(), minuteRows);
        } catch (SQLException | IllegalArgumentException ex) {
            log.warn("Unable to load {}: {}", logLabel, ex.getMessage());
            return OtpHitsView.unavailable(
                    "Live MySQL",
                    "Transaction analytics are temporarily unavailable. Check the database configuration and view access."
            );
        }
    }

    private record OtpCounts(OtpHitsView.OtpSegment total, List<OtpHitsView.OtpSegment> marketplaces) {
    }

    private static Map<String, long[]> emptyDealerCounts() {
        Map<String, long[]> dealerCounts = new LinkedHashMap<>();
        for (String code : LOAN_DEALER_ORDER) {
            dealerCounts.put(code, new long[3]);
        }
        return dealerCounts;
    }

    private static OtpCounts readOtpCounts(ResultSet rs) throws SQLException {
        Map<String, long[]> dealerCounts = emptyDealerCounts();
        for (Map.Entry<String, long[]> entry : dealerCounts.entrySet()) {
            String prefix = "d" + entry.getKey();
            entry.getValue()[0] = number(rs, prefix + "_hits");
            entry.getValue()[1] = number(rs, prefix + "_approved");
            entry.getValue()[2] = number(rs, prefix + "_declined");
        }
        return otpCounts(number(rs, "total_hits"), number(rs, "total_approved"), number(rs, "total_declined"),
                dealerCounts);
    }

    private static OtpCounts otpCounts(long hits, long approved, long declined, Map<String, long[]> dealerCounts) {
        List<OtpHitsView.OtpSegment> marketplaces = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : dealerCounts.entrySet()) {
            long[] values = entry.getValue();
            marketplaces.add(new OtpHitsView.OtpSegment(
                    entry.getKey(), LOAN_DEALER_NAMES.get(entry.getKey()), values[0], values[1], values[2],
                    percentOf(values[1], values[0]), percentOf(values[0], hits)));
        }
        OtpHitsView.OtpSegment total = new OtpHitsView.OtpSegment(
                "", "Total", hits, approved, declined, percentOf(approved, hits), hits == 0 ? 0 : 100);
        return new OtpCounts(total, marketplaces);
    }

    private static String otpCountColumns() {
        String dealerColumns = LOAN_DEALER_ORDER.stream()
                .map(code -> """
                        SUM(CASE WHEN DEALER_CODE_ETAILER = %1$s THEN 1 ELSE 0 END) AS d%1$s_hits,
                        SUM(CASE WHEN DEALER_CODE_ETAILER = %1$s AND TRANSACTION_STATUS = 'APPROVED' THEN 1 ELSE 0 END) AS d%1$s_approved,
                        SUM(CASE WHEN DEALER_CODE_ETAILER = %1$s AND TRANSACTION_STATUS = 'DECLINED' THEN 1 ELSE 0 END) AS d%1$s_declined""".formatted(code))
                .collect(Collectors.joining(",\n"));
        return """
                COUNT(*) AS total_hits,
                SUM(CASE WHEN TRANSACTION_STATUS = 'APPROVED' THEN 1 ELSE 0 END) AS total_approved,
                SUM(CASE WHEN TRANSACTION_STATUS = 'DECLINED' THEN 1 ELSE 0 END) AS total_declined,
                """ + dealerColumns;
    }

    // The OTP table has no FRUAD_GEN_REMARK column, so that filter is intentionally absent.
    private String otpHitsQuery(String table, String reqType, String upperBound) {
        return """
                SELECT %s
                FROM %s
                WHERE AUTH_REQUEST_DATE_TIME >= CURDATE()
                  AND AUTH_REQUEST_DATE_TIME < %s
                  AND REQ_TYPE = '%s'
                  AND PRODUCT IN %s
                """.formatted(otpCountColumns(), table, upperBound, reqType, ECOM_PRODUCT_SET);
    }

    private String otpMinuteQuery(String table, String reqType, String upperBound) {
        return """
                SELECT DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%Y-%%m-%%d %%H:%%i') AS minute_key,
                %s
                FROM %s
                WHERE AUTH_REQUEST_DATE_TIME >= NOW() - INTERVAL %d MINUTE
                  AND AUTH_REQUEST_DATE_TIME >= CURDATE()
                  AND AUTH_REQUEST_DATE_TIME < %s
                  AND REQ_TYPE = '%s'
                  AND PRODUCT IN %s
                GROUP BY minute_key
                ORDER BY minute_key DESC
                LIMIT %d
                """.formatted(otpCountColumns(), table, MINUTE_LOOKBACK, upperBound, reqType, ECOM_PRODUCT_SET,
                HOURLY_TREND_MINUTE_WINDOW);
    }

    private static double averageOf(long amount, long count) {
        if (count == 0) {
            return 0;
        }
        return BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static Map<String, String> channelNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("61", "SFPOSRINV");
        names.put("5", "ECOM ECF");
        names.put("20", "ISDPOSRINV");
        names.put("7", "WEB-OTP");
        names.put("13", "IRCTC");
        names.put("28", "PAYTECH");
        names.put("10", "ECOM REMI");
        names.put("16", "CIA");
        names.put("27", "QR");
        return Collections.unmodifiableMap(names);
    }

    private String channelHitsQuery(String table) {
        String codes = CHANNEL_NAMES.keySet().stream()
                .map(code -> "'" + code + "'")
                .collect(Collectors.joining(", "));
        return """
                SELECT PERTNER_EDC_CHANNEL AS channel,
                       COUNT(*) AS hits,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'APPROVED' THEN 1 ELSE 0 END) AS approved
                FROM %s
                WHERE AUTH_REQUEST_DATE_TIME >= CURDATE()
                  AND AUTH_REQUEST_DATE_TIME < DATE_FORMAT(NOW(), '%%Y-%%m-%%d %%H:%%i:00')
                  AND FRUAD_GEN_REMARK = ''
                  AND PERTNER_EDC_CHANNEL IN (%s)
                GROUP BY PERTNER_EDC_CHANNEL
                """.formatted(table, codes);
    }

    private record DayCounts(LocalDate date, Map<String, long[]> counts) {
    }

    private record RecentCounts(Map<String, long[]> last5, Map<String, long[]> last15, Map<String, long[]> last60) {
    }

    private DayCounts readDayCounts(Connection connection, String table) throws SQLException {
        Map<String, long[]> counts = emptyCounts(DAILY_COUNT_REQ_TYPES);
        LocalDate date = LocalDate.now();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(responseTimeDayQuery(table))) {
            while (rs.next()) {
                // Read as text so the driver's time zone handling cannot shift the DATE value.
                String day = rs.getString("transaction_date");
                if (day != null) {
                    date = LocalDate.parse(day.substring(0, 10));
                }
                long[] buckets = counts.get(rs.getString("req_type"));
                int bucket = rs.getInt("bucket");
                if (buckets != null && bucket >= 0 && bucket < buckets.length) {
                    buckets[bucket] = number(rs, "transaction_count");
                }
            }
        }
        return new DayCounts(date, counts);
    }

    private RecentCounts readRecentCounts(Connection connection, String table) throws SQLException {
        Map<String, long[]> last5 = emptyResponseTimeCounts();
        Map<String, long[]> last15 = emptyResponseTimeCounts();
        Map<String, long[]> last60 = emptyResponseTimeCounts();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(responseTimeRecentQuery(table))) {
            while (rs.next()) {
                String reqType = rs.getString("req_type");
                int bucket = rs.getInt("bucket");
                if (!last60.containsKey(reqType) || bucket < 0 || bucket >= RESPONSE_TIME_RANGES.size()) {
                    continue;
                }
                last5.get(reqType)[bucket] = number(rs, "last_5_count");
                last15.get(reqType)[bucket] = number(rs, "last_15_count");
                last60.get(reqType)[bucket] = number(rs, "last_60_count");
            }
        }
        return new RecentCounts(last5, last15, last60);
    }

    private static Map<String, long[]> emptyResponseTimeCounts() {
        return emptyCounts(RESPONSE_TIME_REQ_TYPES);
    }

    private static Map<String, long[]> emptyCounts(List<String> reqTypes) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        for (String reqType : reqTypes) {
            counts.put(reqType, new long[RESPONSE_TIME_RANGES.size()]);
        }
        return counts;
    }

    private static long sum(long[] values) {
        long total = 0;
        for (long value : values) {
            total += value;
        }
        return total;
    }

    private static double percentOf(long count, long total) {
        if (total == 0) {
            return 0;
        }
        return BigDecimal.valueOf(count * 100)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static String responseTimeReqTypesSql() {
        return RESPONSE_TIME_REQ_TYPES.stream().map(type -> "'" + type + "'").collect(Collectors.joining(", "));
    }

    private String dailyCountReqTypesSql() {
        return DAILY_COUNT_REQ_TYPES.stream().map(type -> "'" + type + "'").collect(Collectors.joining(", "));
    }

    private String responseTimeDayQuery(String table) {
        return """
                SELECT
                    REQ_TYPE AS req_type,
                    DATE_FORMAT(CURDATE(), '%%Y-%%m-%%d') AS transaction_date,
                    %2$s AS bucket,
                    COUNT(*) AS transaction_count
                FROM %1$s
                WHERE AUTH_REQUEST_DATE_TIME >= CURDATE()
                  AND AUTH_REQUEST_DATE_TIME < ADDDATE(CURDATE(), 1)
                  AND REQ_TYPE IN (%3$s)
                GROUP BY REQ_TYPE, bucket
                """.formatted(table, RESPONSE_TIME_BUCKET_SQL, dailyCountReqTypesSql());
    }

    private String responseTimeRecentQuery(String table) {
        return """
                SELECT
                    REQ_TYPE AS req_type,
                    %2$s AS bucket,
                    SUM(AUTH_REQUEST_DATE_TIME >= NOW() - INTERVAL 5 MINUTE) AS last_5_count,
                    SUM(AUTH_REQUEST_DATE_TIME >= NOW() - INTERVAL 15 MINUTE) AS last_15_count,
                    COUNT(*) AS last_60_count
                FROM %1$s
                WHERE AUTH_REQUEST_DATE_TIME >= NOW() - INTERVAL 60 MINUTE
                  AND AUTH_REQUEST_DATE_TIME < NOW()
                  AND REQ_TYPE IN (%3$s)
                GROUP BY REQ_TYPE, bucket
                """.formatted(table, RESPONSE_TIME_BUCKET_SQL, responseTimeReqTypesSql());
    }

    private <T> CompletableFuture<T> readAsync(SqlRead<T> query) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = openConnection()) {
                return query.read(connection);
            } catch (SQLException ex) {
                throw new CompletionException(ex);
            }
        });
    }

    private interface SqlRead<T> {
        T read(Connection connection) throws SQLException;
    }

    public int refreshSeconds() {
        return properties.getRefreshSeconds();
    }

    private Connection openConnection() throws SQLException {
        if (reportingDataSource != null) {
            return reportingDataSource.getConnection();
        }
        return DriverManager.getConnection(properties.getJdbcUrl(), properties.getUsername(), properties.getPassword());
    }

    private String minuteQuery(String view) {
        return minuteQuery(view, MINUTE_WINDOW * 2);
    }

    private String minuteQuery(String view, int limit) {
        return """
                SELECT `TXN DATE`, `HRS`, `MINS`, `TOTAL AUTH HITS`, `TOTAL ECOM HITS`,
                       `ECOM APPROVED`, `FK HITS`, `FK APPROVED`, `AZ HITS`, `AZ APPROVED`,
                       `VOID`, `DECLINED`
                FROM %s
                WHERE `TXN DATE` IS NOT NULL
                  AND `HRS` <> 'TOTAL'
                  AND `MINS` <> 'TOTAL'
                ORDER BY STR_TO_DATE(CONCAT(`TXN DATE`, ' ', `HRS`, ':', `MINS`), '%%Y-%%m-%%d %%H:%%i') DESC
                LIMIT %d
                """.formatted(view, limit);
    }

    private String hourlyQuery(String view) {
        return """
                SELECT `DATE`, `HRS`, NULL AS `MINS`, `TOTAL AUTH HITS`, `TOTAL ECOM HITS`,
                       `ECOM APPROVED`, `FK HITS`, `FK APPROVED`, `AZ HITS`, `AZ APPROVED`,
                       `VOID`, `DECLINED`
                FROM %s
                WHERE `DATE` IS NOT NULL
                  AND `HRS` <> 'TOTAL'
                ORDER BY STR_TO_DATE(CONCAT(`DATE`, ' ', `HRS`, ':00'), '%%Y-%%m-%%d %%H:%%i') DESC
                LIMIT %d
                """.formatted(view, HOUR_WINDOW + 1);
    }

    private String hourlyTrendMinuteQuery(String table, String upperBound) {
        return """
                SELECT DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%Y-%%m-%%d') AS `TXN DATE`,
                       DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%H') AS `HRS`,
                       DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%i') AS `MINS`,
                       COUNT(*) AS `TOTAL AUTH HITS`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'APPROVED' THEN 1 ELSE 0 END) AS `TOTAL APPROVED`,
                       SUM(CASE WHEN PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `TOTAL ECOM HITS`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'APPROVED' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `ECOM APPROVED`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '139908' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `FK HITS`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '139908' AND TRANSACTION_STATUS = 'APPROVED'
                                         AND PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `FK APPROVED`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '195298' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `AZ HITS`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '195298' AND TRANSACTION_STATUS = 'APPROVED'
                                         AND PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `AZ APPROVED`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'VOID' THEN 1 ELSE 0 END) AS `VOID`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'DECLINED' THEN 1 ELSE 0 END) AS `DECLINED`
                FROM %1$s
                WHERE AUTH_REQUEST_DATE_TIME >= NOW() - INTERVAL %3$d MINUTE
                  AND AUTH_REQUEST_DATE_TIME >= CURDATE()
                  AND AUTH_REQUEST_DATE_TIME < %5$s
                  AND FRUAD_GEN_REMARK = ''
                GROUP BY DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%Y-%%m-%%d'),
                         DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%H'),
                         DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%i')
                ORDER BY `TXN DATE` DESC, `HRS` DESC, `MINS` DESC
                LIMIT %4$d
                """.formatted(table, ECOM_PRODUCT_SET,
                MINUTE_LOOKBACK, HOURLY_TREND_MINUTE_WINDOW, upperBound);
    }

    private String hourlyTrendHourQuery(String table, String upperBound) {
        return """
                SELECT DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%Y-%%m-%%d') AS `DATE`,
                       DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%H') AS `HRS`,
                       NULL AS `MINS`,
                       COUNT(*) AS `TOTAL AUTH HITS`,
                       SUM(CASE WHEN PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `TOTAL ECOM HITS`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'APPROVED' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `ECOM APPROVED`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '139908' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `FK HITS`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '139908' AND TRANSACTION_STATUS = 'APPROVED'
                                         AND PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `FK APPROVED`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '195298' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `AZ HITS`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '195298' AND TRANSACTION_STATUS = 'APPROVED'
                                         AND PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `AZ APPROVED`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'VOID' THEN 1 ELSE 0 END) AS `VOID`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'DECLINED' THEN 1 ELSE 0 END) AS `DECLINED`
                FROM %1$s
                WHERE AUTH_REQUEST_DATE_TIME >= DATE_SUB(
                          DATE_FORMAT(NOW(), '%%Y-%%m-%%d %%H:00:00'), INTERVAL %3$d HOUR)
                  AND AUTH_REQUEST_DATE_TIME >= CURDATE()
                  AND AUTH_REQUEST_DATE_TIME < %5$s
                  AND FRUAD_GEN_REMARK = ''
                GROUP BY DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%Y-%%m-%%d'),
                         DATE_FORMAT(AUTH_REQUEST_DATE_TIME, '%%H')
                ORDER BY `DATE` DESC, `HRS` DESC
                LIMIT %4$d
                """.formatted(table, ECOM_PRODUCT_SET, HOURLY_TREND_HOUR_WINDOW, HOURLY_TREND_HOUR_WINDOW + 1,
                upperBound);
    }

    // Read from the base table: the summary view's TOTAL row lags behind (missing right after midnight).
    private TransactionMetrics readDayTotalMetrics(Connection connection, String table, String upperBound)
            throws SQLException {
        String sql = """
                SELECT COUNT(*) AS `TOTAL AUTH HITS`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'APPROVED' THEN 1 ELSE 0 END) AS `APPROVED`,
                       SUM(CASE WHEN PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `TOTAL ECOM HITS`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'APPROVED' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `ECOM APPROVED`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '139908' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `FK HITS`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '139908' AND TRANSACTION_STATUS = 'APPROVED'
                                         AND PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `FK APPROVED`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '195298' AND PRODUCT IN %2$s
                                THEN 1 ELSE 0 END) AS `AZ HITS`,
                       SUM(CASE WHEN DEALER_CODE_ETAILER = '195298' AND TRANSACTION_STATUS = 'APPROVED'
                                         AND PRODUCT IN %2$s THEN 1 ELSE 0 END) AS `AZ APPROVED`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'VOID' THEN 1 ELSE 0 END) AS `VOID`,
                       SUM(CASE WHEN TRANSACTION_STATUS = 'DECLINED' THEN 1 ELSE 0 END) AS `DECLINED`
                FROM %1$s
                WHERE AUTH_REQUEST_DATE_TIME >= CURDATE()
                  AND AUTH_REQUEST_DATE_TIME < %3$s
                  AND FRUAD_GEN_REMARK = ''
                """.formatted(table, ECOM_PRODUCT_SET, upperBound);
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) {
                return TransactionMetrics.empty();
            }
            long ecomHits = number(rs, "TOTAL ECOM HITS");
            long ecomApproved = number(rs, "ECOM APPROVED");
            long flipkartHits = number(rs, "FK HITS");
            long flipkartApproved = number(rs, "FK APPROVED");
            long amazonHits = number(rs, "AZ HITS");
            long amazonApproved = number(rs, "AZ APPROVED");
            return new TransactionMetrics(
                    number(rs, "TOTAL AUTH HITS"),
                    ecomHits,
                    ecomApproved,
                    percentOf(ecomApproved, ecomHits),
                    flipkartHits,
                    flipkartApproved,
                    percentOf(flipkartApproved, flipkartHits),
                    amazonHits,
                    amazonApproved,
                    percentOf(amazonApproved, amazonHits),
                    number(rs, "VOID"),
                    number(rs, "DECLINED"),
                    number(rs, "APPROVED"));
        }
    }

    private List<VolumePoint> readVolumePoints(Connection connection, String sql, boolean minuteLevel)
            throws SQLException {
        return readVolumePoints(connection, sql, minuteLevel, false);
    }

    private List<VolumePoint> readVolumePoints(Connection connection, String sql, boolean minuteLevel,
                                               boolean hasTotalApproved) throws SQLException {
        List<VolumePoint> points = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String date = rs.getString(1);
                String hour = rs.getString(2);
                String minute = rs.getString(3);
                String label = minuteLevel ? hour + ":" + minute : date + " " + hour + ":00";
                points.add(point(
                        label,
                        number(rs, "TOTAL AUTH HITS"),
                        number(rs, "TOTAL ECOM HITS"),
                        number(rs, "ECOM APPROVED"),
                        number(rs, "FK HITS"),
                        number(rs, "FK APPROVED"),
                        number(rs, "AZ HITS"),
                        number(rs, "AZ APPROVED"),
                        number(rs, "VOID"),
                        number(rs, "DECLINED"),
                        hasTotalApproved ? number(rs, "TOTAL APPROVED") : 0
                ));
            }
        }
        Collections.reverse(points);
        return points;
    }

    private long readEligibilityCount(Connection connection, String table) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS eligibility_checks
                FROM %s
                WHERE AUTH_REQUEST_DATE_TIME >= NOW() - INTERVAL %d MINUTE
                  AND REQ_TYPE = 'AUTOTP'
                """.formatted(table, MINUTE_WINDOW);
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getLong("eligibility_checks") : 0;
        }
    }

    private Map<Long, Long> readLatencyCounts(Connection connection, String table) throws SQLException {
        String sql = """
                SELECT FLOOR(TIMESTAMPDIFF(MICROSECOND, API_REQUEST_START_TIME, API_REQUEST_END_TIME) / 100000)
                           AS latency_bucket,
                       COUNT(*) AS transaction_count
                FROM %s
                WHERE AUTH_REQUEST_DATE_TIME >= NOW() - INTERVAL 1 HOUR
                  AND AUTH_REQUEST_DATE_TIME < NOW()
                  AND REQ_TYPE = 'AUTOTP'
                  AND API_REQUEST_START_TIME IS NOT NULL
                  AND API_REQUEST_END_TIME IS NOT NULL
                GROUP BY latency_bucket
                HAVING latency_bucket >= 0
                ORDER BY latency_bucket
                """.formatted(table);
        Map<Long, Long> counts = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                long bucket = Math.min(rs.getLong("latency_bucket"), LATENCY_OVERFLOW_BUCKET);
                counts.merge(bucket, rs.getLong("transaction_count"), Long::sum);
            }
        }
        return counts;
    }

    private TransactionDashboardView build(
            String source,
            List<VolumePoint> minutes,
            List<VolumePoint> hours,
            List<ResponseTimeBucket> latency,
                long eligibilityChecks,
                TransactionMetrics totalMetrics
    ) {
        return build(source, minutes, hours, latency, eligibilityChecks, totalMetrics, HOUR_WINDOW);
    }

    private TransactionDashboardView build(
            String source,
            List<VolumePoint> minutes,
            List<VolumePoint> hours,
            List<ResponseTimeBucket> latency,
            long eligibilityChecks,
            TransactionMetrics totalMetrics,
            int hourWindow
    ) {
        List<VolumePoint> currentMinutes = tail(minutes, MINUTE_WINDOW);
        List<VolumePoint> previousMinutes = precedingWindow(minutes, MINUTE_WINDOW);
        TransactionMetrics current = summarize(currentMinutes);
        TransactionMetrics previous = summarize(previousMinutes);
        List<VolumePoint> minuteSeries = withVolumePercent(currentMinutes);
        List<VolumePoint> hourlySeries = withChange(tail(withVolumePercent(hours), hourWindow), hours);

        return new TransactionDashboardView(
                Instant.now(),
                source,
                true,
                null,
                totalMetrics,
                current,
                delta(current, previous, !previousMinutes.isEmpty()),
                journey(current, eligibilityChecks),
                marketplaces(current),
                newestFirst(minuteSeries),
                newestFirst(hourlySeries),
                latency,
                buildMinuteChart(currentMinutes),
                summarizeLatency(latency)
        );
    }

    private List<JourneyStage> journey(TransactionMetrics m, long eligibilityChecks) {
        List<JourneyStage> stages = new ArrayList<>();
        stages.add(new JourneyStage(
                "Eligibility checks",
                "OTP requests before a purchase",
                "verified_user",
                eligibilityChecks,
                0,
                false,
                null,
                0
        ));
        stages.add(new JourneyStage(
                "Authorization attempts",
                "EMI Card transactions received",
                "point_of_sale",
                m.totalHits(),
                rate(m.totalHits(), eligibilityChecks),
                eligibilityChecks > 0,
                "of eligibility checks",
                0
        ));
        stages.add(new JourneyStage(
                "E-commerce attempts",
                "Online marketplace transactions",
                "shopping_cart",
                m.ecomHits(),
                rate(m.ecomHits(), m.totalHits()),
                m.totalHits() > 0,
                "of all authorizations",
                0
        ));
        stages.add(new JourneyStage(
                "Approved",
                "E-commerce transactions approved",
                "task_alt",
                m.ecomApproved(),
                m.approvalRate(),
                m.ecomHits() > 0,
                "of e-commerce attempts",
                0
        ));

        long widest = stages.stream().mapToLong(JourneyStage::value).max().orElse(0);
        return stages.stream()
                .map(stage -> new JourneyStage(
                        stage.label(), stage.caption(), stage.icon(), stage.value(),
                        stage.conversionPercent(), stage.showConversion(), stage.conversionCaption(),
                        rate(stage.value(), widest)
                ))
                .toList();
    }

    private List<MarketplacePerformance> marketplaces(TransactionMetrics m) {
        return List.of(
                new MarketplacePerformance(
                        "Flipkart", "Dealer code 139908", "flipkart",
                        m.flipkartHits(), m.flipkartApproved(), m.flipkartApprovalRate(),
                        rate(m.flipkartHits(), m.ecomHits())
                ),
                new MarketplacePerformance(
                        "Amazon", "Dealer code 195298", "amazon",
                        m.amazonHits(), m.amazonApproved(), m.amazonApprovalRate(),
                        rate(m.amazonHits(), m.ecomHits())
                )
        );
    }

    private MetricDelta delta(TransactionMetrics current, TransactionMetrics previous, boolean hasPrevious) {
        if (!hasPrevious) {
            return MetricDelta.none();
        }
        return new MetricDelta(
                true,
                change(current.totalHits(), previous.totalHits()),
                change(current.ecomHits(), previous.ecomHits()),
                round(current.approvalRate() - previous.approvalRate()),
                change(current.exceptions(), previous.exceptions())
        );
    }

    private List<ResponseTimeBucket> buildLatencyBuckets(Map<Long, Long> counts) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return List.of();
        }
        long peak = counts.values().stream().mapToLong(Long::longValue).max().orElse(1);
        List<ResponseTimeBucket> buckets = new ArrayList<>();
        double cumulative = 0;
        for (long index = 0; index <= LATENCY_OVERFLOW_BUCKET; index++) {
            long count = counts.getOrDefault(index, 0L);
            double percentage = rate(count, total);
            cumulative = Math.min(100, cumulative + percentage);
            boolean overflow = index == LATENCY_OVERFLOW_BUCKET;
            buckets.add(new ResponseTimeBucket(
                    overflow ? "%d ms and above".formatted(index * 100)
                            : "%d–%d ms".formatted(index * 100, (index + 1) * 100),
                    overflow ? "%d+".formatted(index * 100) : Long.toString((index + 1) * 100),
                    overflow ? Long.MAX_VALUE : (index + 1) * 100,
                    count,
                    percentage,
                    cumulative,
                    rate(count, peak)
            ));
        }
        return buckets;
    }

    private LatencySummary summarizeLatency(List<ResponseTimeBucket> buckets) {
        if (buckets.isEmpty()) {
            return LatencySummary.empty();
        }
        long total = buckets.stream().mapToLong(ResponseTimeBucket::transactionCount).sum();
        ResponseTimeBucket dominant = buckets.stream()
                .max((a, b) -> Long.compare(a.transactionCount(), b.transactionCount()))
                .orElseThrow();
        double under300 = round(buckets.stream()
                .filter(bucket -> bucket.upperBoundMs() <= 300)
                .mapToDouble(ResponseTimeBucket::percentage)
                .sum());
        String tone = under300 >= 90 ? "good" : under300 >= 75 ? "warn" : "bad";
        String label = switch (tone) {
            case "good" -> "Healthy";
            case "warn" -> "Watch";
            default -> "Degraded";
        };
        return new LatencySummary(
                total,
                percentileRange(buckets, 50),
                percentileRange(buckets, 95),
                dominant.rangeLabel(),
                dominant.percentage(),
                under300,
                label,
                tone
        );
    }

    private String percentileRange(List<ResponseTimeBucket> buckets, double percentile) {
        return buckets.stream()
                .filter(bucket -> bucket.transactionCount() > 0 && bucket.cumulativePercentage() >= percentile)
                .findFirst()
                .map(ResponseTimeBucket::rangeLabel)
                .orElse(buckets.get(buckets.size() - 1).rangeLabel());
    }

    private MinuteChart buildMinuteChart(List<VolumePoint> minutes) {
        if (minutes.isEmpty()) {
            return MinuteChart.empty();
        }
        long maxY = niceCeiling(minutes.stream().mapToLong(VolumePoint::totalHits).max().orElse(0));
        int lastIndex = minutes.size() - 1;
        List<ChartPoint> points = new ArrayList<>(minutes.size());
        for (int i = 0; i < minutes.size(); i++) {
            VolumePoint minute = minutes.get(i);
            double x = lastIndex == 0 ? 50 : (i * 100.0) / lastIndex;
            points.add(new ChartPoint(
                    minute.label(),
                    round(x),
                    plotY(minute.totalHits(), maxY),
                    plotY(minute.ecomApproved(), maxY),
                    minute.totalHits(),
                    minute.ecomApproved(),
                    minute.approvalRate()
            ));
        }

        StringBuilder line = new StringBuilder();
        StringBuilder approved = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            ChartPoint point = points.get(i);
            line.append(i == 0 ? "M " : " L ").append(point.x()).append(' ').append(point.y());
            approved.append(i == 0 ? "M " : " L ").append(point.x()).append(' ').append(point.approvedY());
        }
        String areaPath = "M %s 100 L".formatted(points.get(0).x())
                + line.substring(1)
                + " L %s 100 Z".formatted(points.get(points.size() - 1).x());
        ChartPoint peak = points.stream()
                .max((a, b) -> Long.compare(a.totalHits(), b.totalHits()))
                .orElse(null);

        return new MinuteChart(
                points,
                areaPath,
                line.toString(),
                approved.toString(),
                maxY,
                List.of(formatCompact(maxY), formatCompact(maxY / 2), "0"),
                peak
        );
    }

    private TransactionMetrics summarize(List<VolumePoint> points) {
        long ecom = points.stream().mapToLong(VolumePoint::ecomHits).sum();
        long approved = points.stream().mapToLong(VolumePoint::ecomApproved).sum();
        long fk = points.stream().mapToLong(VolumePoint::flipkartHits).sum();
        long fkApproved = points.stream().mapToLong(VolumePoint::flipkartApproved).sum();
        long az = points.stream().mapToLong(VolumePoint::amazonHits).sum();
        long azApproved = points.stream().mapToLong(VolumePoint::amazonApproved).sum();
        return new TransactionMetrics(
                points.stream().mapToLong(VolumePoint::totalHits).sum(),
                ecom, approved, rate(approved, ecom),
                fk, fkApproved, rate(fkApproved, fk),
                az, azApproved, rate(azApproved, az),
                points.stream().mapToLong(VolumePoint::voidCount).sum(),
                points.stream().mapToLong(VolumePoint::declinedCount).sum()
        );
    }

    private VolumePoint point(
            String label,
            long total,
            long ecom,
            long approved,
            long fk,
            long fkApproved,
            long az,
            long azApproved,
            long voidCount,
            long declined,
            long totalApproved
    ) {
        return new VolumePoint(
                label, total, ecom, approved, rate(approved, ecom),
                fk, fkApproved, rate(fkApproved, fk),
                az, azApproved, rate(azApproved, az),
                voidCount, declined, 0, 0, false, totalApproved
        );
    }

    private List<VolumePoint> withVolumePercent(List<VolumePoint> points) {
        long max = points.stream().mapToLong(VolumePoint::totalHits).max().orElse(1);
        return points.stream()
                .map(p -> copy(p, rate(p.totalHits(), max), p.changePercent(), p.hasChange()))
                .toList();
    }

    /** Adds movement against the row immediately before each displayed row. */
    private List<VolumePoint> withChange(List<VolumePoint> displayed, List<VolumePoint> allRows) {
        Map<String, Long> previousByLabel = new LinkedHashMap<>();
        for (int i = 1; i < allRows.size(); i++) {
            previousByLabel.put(allRows.get(i).label(), allRows.get(i - 1).totalHits());
        }
        return displayed.stream()
                .map(p -> {
                    Long previous = previousByLabel.get(p.label());
                    boolean hasChange = previous != null && previous > 0;
                    return copy(p, p.volumePercent(), hasChange ? change(p.totalHits(), previous) : 0, hasChange);
                })
                .toList();
    }

    private VolumePoint copy(VolumePoint p, double volumePercent, double changePercent, boolean hasChange) {
        return new VolumePoint(
                p.label(), p.totalHits(), p.ecomHits(), p.ecomApproved(), p.approvalRate(),
                p.flipkartHits(), p.flipkartApproved(), p.flipkartApprovalRate(),
                p.amazonHits(), p.amazonApproved(), p.amazonApprovalRate(),
                p.voidCount(), p.declinedCount(), volumePercent, changePercent, hasChange, p.totalApproved()
        );
    }

    private static <T> List<T> newestFirst(List<T> values) {
        List<T> reversed = new ArrayList<>(values);
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static <T> List<T> tail(List<T> values, int size) {
        return values.size() <= size ? List.copyOf(values) : List.copyOf(values.subList(values.size() - size, values.size()));
    }

    private static <T> List<T> precedingWindow(List<T> values, int size) {
        if (values.size() <= size) {
            return List.of();
        }
        int end = values.size() - size;
        return List.copyOf(values.subList(Math.max(0, end - size), end));
    }

    private String qualified(String objectName) {
        String schema = properties.getSchema();
        if (!SQL_IDENTIFIER.matcher(schema).matches() || !SQL_IDENTIFIER.matcher(objectName).matches()) {
            throw new IllegalArgumentException("Invalid transaction database identifier");
        }
        return "`" + schema + "`.`" + objectName + "`";
    }

    /** Maps a value onto the 0-100 view box; {@link #niceCeiling} already supplies the headroom. */
    private static double plotY(long value, long maxY) {
        if (maxY <= 0) {
            return 100;
        }
        return round(100 - (Math.min(value, maxY) * 100.0 / maxY));
    }

    private static long niceCeiling(long value) {
        if (value <= 10) {
            return 10;
        }
        long magnitude = (long) Math.pow(10, Math.floor(Math.log10(value)));
        long step = Math.max(1, magnitude / 2);
        return ((value + step - 1) / step) * step;
    }

    private static String formatCompact(long value) {
        if (value >= 1_000_000) {
            return "%.1fM".formatted(value / 1_000_000.0);
        }
        if (value >= 1_000) {
            return "%.1fk".formatted(value / 1_000.0);
        }
        return Long.toString(value);
    }

    static long number(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return new BigDecimal(normalizeNumericText(value)).longValue();
        } catch (NumberFormatException ex) {
            throw new SQLException("Invalid numeric value for " + column + ": " + value, ex);
        }
    }

    static double decimal(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(normalizeNumericText(value));
        } catch (NumberFormatException ex) {
            throw new SQLException("Invalid decimal value for " + column + ": " + value, ex);
        }
    }

    private static String normalizeNumericText(Object value) {
        return value.toString().trim().replace(",", "").replace("%", "");
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : round(numerator * 100.0 / denominator);
    }

    private static double change(long current, long previous) {
        return previous == 0 ? 0 : round((current - previous) * 100.0 / previous);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
