package com.bredashboard.transactions.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OtpHitsView(
        Instant generatedAt,
        String sourceLabel,
        boolean available,
        String errorMessage,
        LocalDate transactionDate,
        OtpSegment total,
        List<OtpSegment> marketplaces,
        List<OtpMinuteRow> minuteRows
) {

    public static OtpHitsView unavailable(String sourceLabel, String message) {
        return new OtpHitsView(Instant.now(), sourceLabel, false, message, LocalDate.now(),
                new OtpSegment("", "Total", 0, 0, 0, 0, 0), List.of(), List.of());
    }

    public record OtpMinuteRow(String label, OtpSegment total, List<OtpSegment> marketplaces) {
    }

    public record OtpSegment(
            String code,
            String name,
            long hits,
            long approved,
            long declined,
            double approvalRate,
            double sharePercent
    ) {
    }
}
