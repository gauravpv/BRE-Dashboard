package com.bredashboard.transactions.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ChannelHitsView(
        Instant generatedAt,
        String sourceLabel,
        boolean available,
        String errorMessage,
        LocalDate transactionDate,
        long totalHits,
        long totalApproved,
        double approvalRate,
        List<ChannelStat> channels
) {

    public static ChannelHitsView unavailable(String sourceLabel, String message) {
        return new ChannelHitsView(
                Instant.now(), sourceLabel, false, message, LocalDate.now(), 0, 0, 0, List.of());
    }

    public record ChannelStat(
            String code,
            String name,
            long hits,
            long approved,
            double approvalRate,
            double sharePercent
    ) {
    }
}
