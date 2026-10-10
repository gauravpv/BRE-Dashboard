package com.bredashboard.transactions.dto;

import java.time.Instant;

public record LiveOverviewView(
        Instant generatedAt,
        TransactionDashboardView auth,
        OtpHitsView autoOtp,
        OtpHitsView ecomEligibility,
        LoanAverageView loan
) {
}
