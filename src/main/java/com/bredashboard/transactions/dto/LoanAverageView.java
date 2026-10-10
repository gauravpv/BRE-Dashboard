package com.bredashboard.transactions.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LoanAverageView(
        Instant generatedAt,
        String sourceLabel,
        boolean available,
        String errorMessage,
        LocalDate transactionDate,
        long totalCount,
        long totalAmount,
        double overallAverage,
        List<DealerLoanStat> dealers
) {

    public static LoanAverageView unavailable(String sourceLabel, String message) {
        return new LoanAverageView(
                Instant.now(), sourceLabel, false, message, LocalDate.now(), 0, 0, 0, List.of());
    }

    public record DealerLoanStat(
            String code,
            String name,
            long count,
            long totalAmount,
            double averageAmount,
            double sharePercent,
            LoanBand below5k,
            LoanBand from5k
    ) {
    }

    public record LoanBand(long count, long totalAmount, double averageAmount) {
    }
}
