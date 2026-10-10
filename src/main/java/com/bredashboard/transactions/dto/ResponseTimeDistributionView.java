package com.bredashboard.transactions.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record ResponseTimeDistributionView(
        Instant generatedAt,
        String sourceLabel,
        boolean available,
        String errorMessage,
        LocalDate transactionDate,
        List<DailySummary> daily,
        List<RecentPanel> recent
) {

    public static final String ECOMELGB = "ECOMELGB";
    public static final String AUTOTP = "AUTOTP";
    public static final String ELGBLT = "ELGBLT";
    public static final String BRESRCH = "BRESRCH";
    public static final String MKPELGB = "MKPELGB";
    private static final Set<String> SLOW_RANGES = Set.of("200-300 MS", "300+ MS");

    public static ResponseTimeDistributionView unavailable(String sourceLabel, String message) {
        return new ResponseTimeDistributionView(
                Instant.now(), sourceLabel, false, message, LocalDate.now(), List.of(), List.of());
    }

    public DailySummary ecomDay() {
        return day(ECOMELGB);
    }

    public DailySummary autotpDay() {
        return day(AUTOTP);
    }

    public DailySummary elgbltDay() {
        return day(ELGBLT);
    }

    public DailySummary bresrchDay() {
        return day(BRESRCH);
    }

    public DailySummary mkpelgbDay() {
        return day(MKPELGB);
    }

    private DailySummary day(String reqType) {
        return daily.stream()
                .filter(summary -> summary.reqType().equals(reqType))
                .findFirst()
                .orElse(new DailySummary(reqType, 0, List.of()));
    }

    public record DailySummary(String reqType, long totalCount, List<Bucket> buckets) {
    }

    public record Bucket(String rangeLabel, long transactionCount, double percentage) {
    }

    public record RecentPanel(
            String reqType,
            long last5Total,
            long last15Total,
            long last60Total,
            List<RecentRow> rows
    ) {

        public String title() {
            return switch (reqType) {
                case ECOMELGB -> "ECOM Eligibility";
                case AUTOTP -> "AUTH OTP";
                default -> reqType;
            };
        }

        /** Share of 200 ms+ transactions in the given window (5, 15 or 60 minutes). */
        public double slowPercentage(int window) {
            long total = switch (window) {
                case 5 -> last5Total;
                case 15 -> last15Total;
                default -> last60Total;
            };
            if (total == 0) {
                return 0;
            }
            long slow = rows.stream().filter(RecentRow::slow).mapToLong(row -> row.count(window)).sum();
            return slow * 100.0 / total;
        }

        public String slowTone(int window) {
            double percentage = slowPercentage(window);
            if (percentage > 2) {
                return "alert";
            }
            return percentage > 1 ? "warn" : "ok";
        }
    }

    public record RecentRow(
            String rangeLabel,
            long last5Count,
            double last5Percentage,
            long last15Count,
            double last15Percentage,
            long last60Count,
            double last60Percentage
    ) {

        public boolean slow() {
            return SLOW_RANGES.contains(rangeLabel);
        }

        long count(int window) {
            return switch (window) {
                case 5 -> last5Count;
                case 15 -> last15Count;
                default -> last60Count;
            };
        }
    }
}
