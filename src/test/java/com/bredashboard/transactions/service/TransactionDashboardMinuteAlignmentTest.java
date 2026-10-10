package com.bredashboard.transactions.service;

import com.bredashboard.transactions.dto.LiveOverviewView;
import com.bredashboard.transactions.dto.OtpHitsView;
import com.bredashboard.transactions.dto.TransactionDashboardView;
import com.bredashboard.transactions.dto.TransactionDashboardView.TransactionMetrics;
import com.bredashboard.transactions.dto.TransactionDashboardView.VolumePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionDashboardMinuteAlignmentTest {

    @Test
    void alignsAllStreamsToLatestObservedMinuteAndFillsMissingBuckets() {
        OtpHitsView.OtpSegment market = new OtpHitsView.OtpSegment("139908", "Flipkart", 1, 1, 0, 100, 100);
        OtpHitsView.OtpSegment otpTotal = new OtpHitsView.OtpSegment("", "Total", 1, 1, 0, 100, 100);
        OtpHitsView autoOtp = new OtpHitsView(Instant.now(), "test", true, null, LocalDate.now(), otpTotal,
                List.of(market), List.of(new OtpHitsView.OtpMinuteRow("00:00", otpTotal, List.of(market))));
        OtpHitsView eligibility = new OtpHitsView(Instant.now(), "test", true, null, LocalDate.now(), otpTotal,
                List.of(market), List.of(new OtpHitsView.OtpMinuteRow("00:01", otpTotal, List.of(market))));
        VolumePoint authPoint = new VolumePoint("23:59", 150, 125, 112, 89.6,
                23, 23, 100, 11, 11, 100, 0, 0, 0, 0, false, 112);
        TransactionDashboardView auth = new TransactionDashboardView(
                Instant.now(), "test", true, null, TransactionMetrics.empty(), TransactionMetrics.empty(),
                TransactionDashboardView.MetricDelta.none(), List.of(), List.of(), List.of(authPoint), List.of(),
                List.of(), TransactionDashboardView.MinuteChart.empty(),
                TransactionDashboardView.LatencySummary.empty());

        LiveOverviewView aligned = TransactionDashboardService.alignOverviewMinutes(
                new LiveOverviewView(Instant.now(), auth, autoOtp, eligibility, null));

        List<String> expectedLabels = List.of("00:01", "00:00", "23:59", "23:58", "23:57", "23:56",
                "23:55", "23:54", "23:53", "23:52");
        assertThat(aligned.auth().minuteRows()).extracting(VolumePoint::label).containsExactlyElementsOf(expectedLabels);
        assertThat(aligned.autoOtp().minuteRows()).extracting(OtpHitsView.OtpMinuteRow::label)
                .containsExactlyElementsOf(expectedLabels);
        assertThat(aligned.ecomEligibility().minuteRows()).extracting(OtpHitsView.OtpMinuteRow::label)
                .containsExactlyElementsOf(expectedLabels);
        assertThat(aligned.auth().minuteRows().get(0).totalHits()).isZero();
        assertThat(aligned.auth().minuteRows().get(2).totalHits()).isEqualTo(150);
        assertThat(aligned.autoOtp().minuteRows().get(1).total().hits()).isEqualTo(1);
        assertThat(aligned.ecomEligibility().minuteRows().get(0).total().hits()).isEqualTo(1);
    }
}