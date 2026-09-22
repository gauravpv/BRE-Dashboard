package com.bredashboard.transactions.service;

import com.bredashboard.transactions.config.TransactionDashboardProperties;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class TransactionDashboardService {

    private static final Logger log = LoggerFactory.getLogger(TransactionDashboardService.class);
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private static final int MINUTE_WINDOW = 15;
    private static final int HOUR_WINDOW = 5;
    /** Latency buckets are 100ms wide; anything at or beyond this index is grouped as an overflow bucket. */
    private static final long LATENCY_OVERFLOW_BUCKET = 6;

    private final TransactionDashboardProperties properties;
    private final DataSource reportingDataSource;

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
            try (Connection connection = openConnection()) {
                // Both series are over-fetched by one window/row so the view can show movement.
                List<VolumePoint> minutes = readVolumePoints(connection, minuteQuery(minuteView), true);
                List<VolumePoint> hours = readVolumePoints(connection, hourlyQuery(hourlyView), false);
                Map<Long, Long> latency = readLatencyCounts(connection, otpTable);
                long eligibilityChecks = readEligibilityCount(connection, otpTable);
                return build("Live MySQL", minutes, hours, buildLatencyBuckets(latency), eligibilityChecks);
            }
        } catch (SQLException | IllegalArgumentException ex) {
            log.warn("Unable to load transaction dashboard: {}", ex.getMessage());
            return TransactionDashboardView.unavailable(
                    "Live MySQL",
                    "Transaction analytics are temporarily unavailable. Check the database configuration and view access."
            );
        }
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
                """.formatted(view, MINUTE_WINDOW * 2);
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

    private List<VolumePoint> readVolumePoints(Connection connection, String sql, boolean minuteLevel)
            throws SQLException {
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
                        number(rs, "DECLINED")
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
            long eligibilityChecks
    ) {
        List<VolumePoint> currentMinutes = tail(minutes, MINUTE_WINDOW);
        List<VolumePoint> previousMinutes = precedingWindow(minutes, MINUTE_WINDOW);
        TransactionMetrics current = summarize(currentMinutes);
        TransactionMetrics previous = summarize(previousMinutes);
        List<VolumePoint> minuteSeries = withVolumePercent(currentMinutes);
        List<VolumePoint> hourlySeries = withChange(tail(withVolumePercent(hours), HOUR_WINDOW), hours);

        return new TransactionDashboardView(
                Instant.now(),
                source,
                true,
                null,
                current,
                delta(current, previous, !previousMinutes.isEmpty()),
                journey(current, eligibilityChecks),
                marketplaces(current),
                minuteSeries,
                hourlySeries,
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
                ),
                new MarketplacePerformance(
                        "Other e-commerce", "All remaining online dealers", "other",
                        m.otherEcomHits(), m.otherEcomApproved(),
                        rate(m.otherEcomApproved(), m.otherEcomHits()),
                        rate(m.otherEcomHits(), m.ecomHits())
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
            long declined
    ) {
        return new VolumePoint(
                label, total, ecom, approved, rate(approved, ecom),
                fk, fkApproved, rate(fkApproved, fk),
                az, azApproved, rate(azApproved, az),
                voidCount, declined, 0, 0, false
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
                p.voidCount(), p.declinedCount(), volumePercent, changePercent, hasChange
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

    private static long number(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? 0 : value.longValue();
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
