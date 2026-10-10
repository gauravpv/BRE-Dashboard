package com.bredashboard.transactions.dto;

import java.time.Instant;
import java.util.List;

public record TransactionDashboardView(
        Instant generatedAt,
        String sourceLabel,
        boolean available,
        String errorMessage,
        TransactionMetrics totalMetrics,
        TransactionMetrics last15Minutes,
        MetricDelta delta,
        List<JourneyStage> journey,
        List<MarketplacePerformance> marketplaces,
        List<VolumePoint> minuteRows,
        List<VolumePoint> hourlyRows,
        List<ResponseTimeBucket> responseTimeBuckets,
        MinuteChart minuteChart,
        LatencySummary latencySummary
) {

    public static TransactionDashboardView unavailable(String sourceLabel, String message) {
        return new TransactionDashboardView(
                Instant.now(),
                sourceLabel,
                false,
                message,
                TransactionMetrics.empty(),
                TransactionMetrics.empty(),
                MetricDelta.none(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                MinuteChart.empty(),
                LatencySummary.empty()
        );
    }

    public record TransactionMetrics(
            long totalHits,
            long ecomHits,
            long ecomApproved,
            double approvalRate,
            long flipkartHits,
            long flipkartApproved,
            double flipkartApprovalRate,
            long amazonHits,
            long amazonApproved,
            double amazonApprovalRate,
            long voidCount,
            long declinedCount,
            long approvedCount
    ) {
        public TransactionMetrics(long totalHits, long ecomHits, long ecomApproved, double approvalRate,
                                  long flipkartHits, long flipkartApproved, double flipkartApprovalRate,
                                  long amazonHits, long amazonApproved, double amazonApprovalRate,
                                  long voidCount, long declinedCount) {
            this(totalHits, ecomHits, ecomApproved, approvalRate, flipkartHits, flipkartApproved,
                    flipkartApprovalRate, amazonHits, amazonApproved, amazonApprovalRate,
                    voidCount, declinedCount, 0);
        }

        public static TransactionMetrics empty() {
            return new TransactionMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        /** Approved transactions across every product, not only e-commerce. */
        public TransactionMetrics withApprovedCount(long approved) {
            return new TransactionMetrics(totalHits, ecomHits, ecomApproved, approvalRate, flipkartHits,
                    flipkartApproved, flipkartApprovalRate, amazonHits, amazonApproved, amazonApprovalRate,
                    voidCount, declinedCount, approved);
        }

        public long exceptions() {
            return voidCount + declinedCount;
        }

        public long otherEcomHits() {
            return Math.max(0, ecomHits - flipkartHits - amazonHits);
        }

        public long otherEcomApproved() {
            return Math.max(0, ecomApproved - flipkartApproved - amazonApproved);
        }
    }

    /** Period-over-period movement against the preceding window of the same length. */
    public record MetricDelta(
            boolean available,
            double totalHitsPercent,
            double ecomHitsPercent,
            double approvalRatePoints,
            double exceptionsPercent
    ) {
        public static MetricDelta none() {
            return new MetricDelta(false, 0, 0, 0, 0);
        }
    }

    /** One step of the EMI Card journey, from eligibility check through to approval. */
    public record JourneyStage(
            String label,
            String caption,
            String icon,
            long value,
            double conversionPercent,
            boolean showConversion,
            String conversionCaption,
            double relativeWidth
    ) {
    }

    public record MarketplacePerformance(
            String name,
            String caption,
            String accent,
            long hits,
            long approved,
            double approvalRate,
            double shareOfEcom
    ) {
    }

    public record VolumePoint(
            String label,
            long totalHits,
            long ecomHits,
            long ecomApproved,
            double approvalRate,
            long flipkartHits,
            long flipkartApproved,
            double flipkartApprovalRate,
            long amazonHits,
            long amazonApproved,
            double amazonApprovalRate,
            long voidCount,
            long declinedCount,
            double volumePercent,
            double changePercent,
            boolean hasChange,
            long totalApproved
    ) {
        public VolumePoint(String label, long totalHits, long ecomHits, long ecomApproved, double approvalRate,
                           long flipkartHits, long flipkartApproved, double flipkartApprovalRate,
                           long amazonHits, long amazonApproved, double amazonApprovalRate,
                           long voidCount, long declinedCount,
                           double volumePercent, double changePercent, boolean hasChange) {
            this(label, totalHits, ecomHits, ecomApproved, approvalRate, flipkartHits, flipkartApproved,
                    flipkartApprovalRate, amazonHits, amazonApproved, amazonApprovalRate, voidCount, declinedCount,
                    volumePercent, changePercent, hasChange, 0);
        }
    }

    public record ResponseTimeBucket(
            String rangeLabel,
            String shortLabel,
            long upperBoundMs,
            long transactionCount,
            double percentage,
            double cumulativePercentage,
            double relativeHeight
    ) {
    }

    /** Pre-computed SVG geometry for the minute-level volume trend (0-100 view box). */
    public record MinuteChart(
            List<ChartPoint> points,
            String areaPath,
            String linePath,
            String approvedPath,
            long maxY,
            List<String> yAxisLabels,
            ChartPoint peak
    ) {
        public static MinuteChart empty() {
            return new MinuteChart(List.of(), "", "", "", 0, List.of(), null);
        }
    }

    public record ChartPoint(
            String label,
            double x,
            double y,
            double approvedY,
            long totalHits,
            long ecomApproved,
            double approvalRate
    ) {
    }

    public record LatencySummary(
            long totalCalls,
            String medianRange,
            String p95Range,
            String dominantRange,
            double dominantPercent,
            double under300Percent,
            String healthLabel,
            String healthTone
    ) {
        public static LatencySummary empty() {
            return new LatencySummary(0, "—", "—", "—", 0, 0, "No data", "neutral");
        }
    }
}
