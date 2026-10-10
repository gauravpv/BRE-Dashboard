package com.bredashboard.transactions;

import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.BreUserPrincipal;
import com.bredashboard.auth.repository.AppUserRepository;
import com.bredashboard.transactions.dto.ChannelHitsView;
import com.bredashboard.transactions.dto.LiveOverviewView;
import com.bredashboard.transactions.dto.LoanAverageView;
import com.bredashboard.transactions.dto.OtpHitsView;
import com.bredashboard.transactions.dto.ResponseTimeDistributionView;
import com.bredashboard.transactions.dto.TransactionDashboardView;
import com.bredashboard.transactions.dto.TransactionDashboardView.TransactionMetrics;
import com.bredashboard.transactions.dto.TransactionDashboardView.VolumePoint;
import com.bredashboard.transactions.service.TransactionDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionDashboardPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @MockitoSpyBean
    private TransactionDashboardService dashboardService;

    @Test
    void transactionAnalyserPageIsUnavailable() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();

        mockMvc.perform(get("/transactions").with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void hourlyTrendWaitsForDataBeforeRenderingPage() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        doReturn(TransactionDashboardView.unavailable("MySQL", "Report temporarily unavailable"))
                .when(dashboardService).loadHourlyTrend(false);

        mockMvc.perform(get("/transactions/hourly").with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("hourly-trend"))
                .andExpect(content().string(containsString("Report temporarily unavailable")))
                .andExpect(content().string(not(containsString("Loading report"))))
                .andExpect(content().string(containsString("data-report-url=")));

        verify(dashboardService).loadHourlyTrend(false);
    }

            @Test
            void administratorCanSwitchHourlyTrendBreakdownTabs() throws Exception {
            AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
            VolumePoint minute = new VolumePoint("17:48", 47, 6, 5, 83.33,
                3, 2, 66.67, 1, 1, 100, 7, 8, 100, 0, false);
            VolumePoint hour = new VolumePoint("2026-10-01 17:00", 121, 10, 9, 90,
                4, 4, 100, 2, 2, 100, 11, 12, 100, 0, false);
            TransactionDashboardView minuteDashboard = new TransactionDashboardView(
                Instant.now(), "Live MySQL", true, null, TransactionMetrics.empty(),
                TransactionMetrics.empty(), TransactionDashboardView.MetricDelta.none(),
                List.of(), List.of(), List.of(minute), List.of(), List.of(),
                TransactionDashboardView.MinuteChart.empty(), TransactionDashboardView.LatencySummary.empty());
            TransactionDashboardView hourDashboard = new TransactionDashboardView(
                Instant.now(), "Live MySQL", true, null, TransactionMetrics.empty(),
                TransactionMetrics.empty(), TransactionDashboardView.MetricDelta.none(),
                List.of(), List.of(), List.of(), List.of(hour), List.of(),
                TransactionDashboardView.MinuteChart.empty(), TransactionDashboardView.LatencySummary.empty());
            doReturn(minuteDashboard).when(dashboardService).loadHourlyTrend(false);
            doReturn(hourDashboard).when(dashboardService).loadHourlyTrend(true);

            mockMvc.perform(get("/transactions/hourly").param("fragment", "true")
                    .with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("hourly-trend :: live-shell"))
                .andExpect(model().attribute("activeNav", "hourly-trend"))
                .andExpect(content().string(containsString("Minute detail")))
                .andExpect(content().string(containsString("Last 10 hours")))
                .andExpect(content().string(containsString("data-txn-refresh")))
                .andExpect(content().string(not(containsString("Authorization hits"))))
                .andExpect(content().string(not(containsString("Latest daily total"))))
                .andExpect(content().string(containsString("<th>Void</th>")))
                .andExpect(content().string(not(containsString("<th>Declined</th>"))))
                .andExpect(content().string(containsString("data-label=\"Void\">7</td>")))
                .andExpect(content().string(not(containsString("data-label=\"Declined\""))))
                .andExpect(content().string(not(containsString("data-label=\"Void\">11</td>"))))
                .andExpect(content().string(containsString("Auth Transactions")))
                .andExpect(content().string(containsString("data-live-shell")))
                .andExpect(content().string(containsString("txn-table--grid")))
                .andExpect(content().string(containsString("/js/live-refresh.js")))
                .andExpect(content().string(not(containsString("Daily total"))))
                .andExpect(content().string(not(containsString("Marketplaces</h2>"))));

            mockMvc.perform(get("/transactions/hourly").param("view", "hour").param("fragment", "true")
                    .with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("hourly-trend :: live-shell"))
                .andExpect(model().attribute("showHours", true))
                .andExpect(content().string(containsString("data-label=\"Void\">11</td>")))
                .andExpect(content().string(not(containsString("data-label=\"Declined\""))))
                .andExpect(content().string(not(containsString("<th>Declined</th>"))))
                .andExpect(content().string(not(containsString("data-label=\"Void\">7</td>"))));
            }

            @Test
            void hourlyTrendUsesTransactionAnalyticsPermission() throws Exception {
            AppUser monitoring = userRepository.findByAzureAdId("dev-monitoring").orElseThrow();
            AppUser tester = userRepository.findByAzureAdId("dev-tester").orElseThrow();

            mockMvc.perform(get("/transactions/hourly").with(user(BreUserPrincipal.fromUser(monitoring))))
                .andExpect(status().isOk());
            mockMvc.perform(get("/transactions/hourly").with(user(BreUserPrincipal.fromUser(tester))))
                .andExpect(status().is3xxRedirection());
            }

    @Test
    void administratorCanViewResponseTimeDistribution() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        ResponseTimeDistributionView distribution = new ResponseTimeDistributionView(
                Instant.now(), "Live MySQL", true, null, LocalDate.of(2026, 10, 5),
                List.of(
                        new ResponseTimeDistributionView.DailySummary("ECOMELGB", 37338, List.of(
                                new ResponseTimeDistributionView.Bucket("0-50 MS", 36991, 99.07),
                                new ResponseTimeDistributionView.Bucket("300+ MS", 347, 0.93))),
                        new ResponseTimeDistributionView.DailySummary("AUTOTP", 12345, List.of()),
                        new ResponseTimeDistributionView.DailySummary("ELGBLT", 678, List.of()),
                        new ResponseTimeDistributionView.DailySummary("BRESRCH", 901, List.of()),
                        new ResponseTimeDistributionView.DailySummary("MKPELGB", 234, List.of())),
                List.of(
                        new ResponseTimeDistributionView.RecentPanel("ECOMELGB", 201, 812, 3301, List.of(
                        new ResponseTimeDistributionView.RecentRow(
                            "0-50 MS", 200, 99.50, 800, 98.52, 3250, 98.46))),
                        new ResponseTimeDistributionView.RecentPanel("AUTOTP", 0, 0, 0, List.of())));
        doReturn(distribution).when(dashboardService).loadResponseTimeDistribution();

        mockMvc.perform(get("/transactions/response-time").param("fragment", "true")
                .with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
            .andExpect(view().name("response-time :: live-shell"))
                .andExpect(model().attribute("activeNav", "response-time"))
                .andExpect(content().string(containsString("37,338")))
                .andExpect(content().string(containsString("12,345")))
                .andExpect(content().string(containsString("678")))
                .andExpect(content().string(containsString("901")))
                .andExpect(content().string(containsString("234")))
                .andExpect(content().string(containsString("ELGBLT requests")))
                .andExpect(content().string(containsString("BRESRCH requests")))
                .andExpect(content().string(containsString("MKPELGB requests")))
            .andExpect(content().string(containsString("Last 15 min")))
            .andExpect(content().string(containsString("Last 60 min")))
            .andExpect(content().string(containsString("Count")))
            .andExpect(content().string(containsString("Percent")))
            .andExpect(content().string(containsString("3,250")))
            .andExpect(content().string(containsString("99.50%")))
            .andExpect(content().string(containsString("98.52%")))
            .andExpect(content().string(not(containsString("data-rt-bar"))))
            .andExpect(content().string(containsString("98.46%")))
                .andExpect(content().string(containsString("ECOM Eligibility")))
                .andExpect(content().string(containsString("AUTH OTP")))
                .andExpect(content().string(containsString("Last 5 min")))
                .andExpect(content().string(not(containsString("response time</h2>"))))
                .andExpect(content().string(not(containsString("Daily distribution"))))
                .andExpect(content().string(containsString("Last 5 min percent")))
                .andExpect(content().string(containsString("data-live-shell")))
                .andExpect(content().string(containsString("No AUTH OTP transactions in the last 60 minutes.")))
                .andExpect(content().string(containsString("Daily response time distribution")));
    }

    @Test
    void responseTimeWaitsForDataBeforeRenderingPage() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        doReturn(ResponseTimeDistributionView.unavailable("MySQL", "Report temporarily unavailable"))
                .when(dashboardService).loadResponseTimeDistribution();

        mockMvc.perform(get("/transactions/response-time").with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("response-time"))
                .andExpect(content().string(containsString("Report temporarily unavailable")))
                .andExpect(content().string(not(containsString("Loading report"))))
                .andExpect(content().string(containsString("data-report-url=")));

        verify(dashboardService).loadResponseTimeDistribution();
    }

    @Test
    void responseTimeUsesTransactionAnalyticsPermission() throws Exception {
        AppUser monitoring = userRepository.findByAzureAdId("dev-monitoring").orElseThrow();
        AppUser tester = userRepository.findByAzureAdId("dev-tester").orElseThrow();

        mockMvc.perform(get("/transactions/response-time").with(user(BreUserPrincipal.fromUser(monitoring))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/transactions/response-time").with(user(BreUserPrincipal.fromUser(tester))))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void administratorCanViewChannelHits() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        ChannelHitsView channels = new ChannelHitsView(
                Instant.now(), "Live MySQL", true, null, LocalDate.of(2026, 10, 7), 12345, 11000, 89.1,
                List.of(
                        new ChannelHitsView.ChannelStat("5", "ECOM ECF", 12000, 10800, 90.0, 97.21),
                        new ChannelHitsView.ChannelStat("27", "QR", 345, 200, 57.97, 2.79)));
        doReturn(channels).when(dashboardService).loadChannelHits();

        mockMvc.perform(get("/transactions/channels").param("fragment", "true")
                        .with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("channel-hits :: live-shell"))
                .andExpect(model().attribute("activeNav", "channel-hits"))
                .andExpect(content().string(containsString("12,345")))
                .andExpect(content().string(containsString("ECOM ECF")))
                .andExpect(content().string(containsString("10,800")))
                .andExpect(content().string(containsString("57.97%")))
                .andExpect(content().string(containsString("txn-rate-pill--warn")))
                .andExpect(content().string(containsString("data-live-shell")));

        mockMvc.perform(get("/transactions/channels").with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("channel-hits"))
                .andExpect(content().string(containsString("Channel Wise Hits")));
    }

    @Test
    void channelHitsUsesTransactionAnalyticsPermission() throws Exception {
        AppUser monitoring = userRepository.findByAzureAdId("dev-monitoring").orElseThrow();
        AppUser tester = userRepository.findByAzureAdId("dev-tester").orElseThrow();
        doReturn(ChannelHitsView.unavailable("MySQL", "Report temporarily unavailable"))
                .when(dashboardService).loadChannelHits();

        mockMvc.perform(get("/transactions/channels").with(user(BreUserPrincipal.fromUser(monitoring))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Report temporarily unavailable")));
        mockMvc.perform(get("/transactions/channels").with(user(BreUserPrincipal.fromUser(tester))))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void administratorCanViewLoanAverage() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        LoanAverageView loans = new LoanAverageView(
                Instant.now(), "Live MySQL", true, null, LocalDate.of(2026, 10, 8), 5, 250000, 50000.0,
                List.of(
                        new LoanAverageView.DealerLoanStat("139908", "Flipkart", 3, 180000, 60000.0, 72.0,
                                new LoanAverageView.LoanBand(1, 4200, 4200.0),
                                new LoanAverageView.LoanBand(2, 175800, 87900.0)),
                        new LoanAverageView.DealerLoanStat("195298", "Amazon", 2, 70000, 35000.0, 28.0,
                                new LoanAverageView.LoanBand(0, 0, 0),
                                new LoanAverageView.LoanBand(2, 70000, 35000.0))));
        doReturn(loans).when(dashboardService).loadLoanAverage();

        mockMvc.perform(get("/transactions/loan-average").param("fragment", "true")
                        .with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("loan-average :: live-shell"))
                .andExpect(model().attribute("activeNav", "loan-average"))
                .andExpect(content().string(containsString("Flipkart")))
                .andExpect(content().string(containsString("Amazon")))
                .andExpect(content().string(containsString("60,000")))
                .andExpect(content().string(containsString("87,900")))
                .andExpect(content().string(containsString("4,200")))
                .andExpect(content().string(containsString("250,000")));

        mockMvc.perform(get("/transactions/loan-average").with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("loan-average"))
                .andExpect(content().string(containsString("Avg Loan Amount")));
    }

    @Test
    void loanAverageIsAdminOnlyByDefault() throws Exception {
        AppUser monitoring = userRepository.findByAzureAdId("dev-monitoring").orElseThrow();

        mockMvc.perform(get("/transactions/loan-average").with(user(BreUserPrincipal.fromUser(monitoring))))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void administratorCanViewOtpHits() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        OtpHitsView otp = new OtpHitsView(
                Instant.now(), "Live MySQL", true, null, LocalDate.of(2026, 10, 8),
                new OtpHitsView.OtpSegment("", "Total", 12345, 11000, 1200, 89.1, 100),
                List.of(
                        new OtpHitsView.OtpSegment("139908", "Flipkart", 8000, 6000, 1900, 75.0, 64.8),
                        new OtpHitsView.OtpSegment("195298", "Amazon", 4000, 3900, 90, 97.5, 32.4)),
                List.of(new OtpHitsView.OtpMinuteRow("14:07",
                        new OtpHitsView.OtpSegment("", "Total", 321, 300, 20, 93.46, 100),
                        List.of(
                                new OtpHitsView.OtpSegment("139908", "Flipkart", 200, 190, 10, 95.0, 62.3),
                                new OtpHitsView.OtpSegment("195298", "Amazon", 100, 95, 5, 95.0, 31.15)))));
        doReturn(otp).when(dashboardService).loadOtpHits();

        mockMvc.perform(get("/transactions/otp-hits").param("fragment", "true")
                        .with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("otp-hits :: live-shell"))
                .andExpect(model().attribute("activeNav", "otp-hits"))
                .andExpect(content().string(containsString("12,345")))
                .andExpect(content().string(containsString("Flipkart")))
                .andExpect(content().string(containsString("8,000")))
                .andExpect(content().string(containsString("97.50%")))
                .andExpect(content().string(containsString("14:07")))
                .andExpect(content().string(containsString("Today total")))
                .andExpect(content().string(not(containsString("channel-card"))));

        mockMvc.perform(get("/transactions/otp-hits").with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("otp-hits"))
                .andExpect(content().string(containsString("OTP Hits")));
    }

    @Test
    void otpHitsIsAdminOnlyByDefault() throws Exception {
        AppUser monitoring = userRepository.findByAzureAdId("dev-monitoring").orElseThrow();

        mockMvc.perform(get("/transactions/otp-hits").with(user(BreUserPrincipal.fromUser(monitoring))))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void administratorCanViewLiveOverview() throws Exception {
        AppUser admin = userRepository.findByAzureAdId("dev-admin").orElseThrow();
        VolumePoint minute = new VolumePoint("14:07", 4321, 60, 54, 90.0,
                40, 30, 75.0, 20, 19, 95.0, 0, 0, 100, 0, false);
        TransactionMetrics day = new TransactionMetrics(987654, 5000, 4500, 90.0,
                3000, 2700, 90.0, 2000, 1800, 90.0, 0, 0);
        TransactionDashboardView auth = new TransactionDashboardView(
                Instant.now(), "Live MySQL", true, null, day, TransactionMetrics.empty(),
                TransactionDashboardView.MetricDelta.none(), List.of(), List.of(), List.of(minute), List.of(),
                List.of(), TransactionDashboardView.MinuteChart.empty(), TransactionDashboardView.LatencySummary.empty());
        OtpHitsView otp = new OtpHitsView(
                Instant.now(), "Live MySQL", true, null, LocalDate.of(2026, 10, 8),
                new OtpHitsView.OtpSegment("", "Total", 12345, 11000, 1200, 89.1, 100),
                List.of(
                        new OtpHitsView.OtpSegment("139908", "Flipkart", 8000, 6000, 1900, 75.0, 64.8),
                        new OtpHitsView.OtpSegment("195298", "Amazon", 4000, 3900, 90, 97.5, 32.4)),
                List.of());
        LoanAverageView loans = new LoanAverageView(
                Instant.now(), "Live MySQL", true, null, LocalDate.of(2026, 10, 8), 5, 250000, 50000.0,
                List.of(new LoanAverageView.DealerLoanStat("139908", "Flipkart", 5, 250000, 50000.0, 100.0,
                        new LoanAverageView.LoanBand(1, 4200, 4200.0),
                        new LoanAverageView.LoanBand(4, 245800, 61450.0))));
        doReturn(new LiveOverviewView(Instant.now(), auth, otp,
                OtpHitsView.unavailable("Live MySQL", "Eligibility temporarily unavailable"), loans))
                .when(dashboardService).loadOverview();

        mockMvc.perform(get("/transactions/overview").param("fragment", "true")
                        .with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("overview :: live-shell"))
                .andExpect(model().attribute("activeNav", "overview"))
                .andExpect(content().string(containsString("987,654")))
                .andExpect(content().string(containsString("4,321")))
                .andExpect(content().string(containsString("75.00%")))
                .andExpect(content().string(containsString("12,345")))
                .andExpect(content().string(containsString("No Auto OTP hits in the last 10 minutes.")))
                .andExpect(content().string(containsString("Eligibility temporarily unavailable")))
                .andExpect(content().string(containsString("61,450")))
                .andExpect(content().string(containsString("250,000")))
                .andExpect(content().string(containsString("data-live-shell")));

        mockMvc.perform(get("/transactions/overview").with(user(BreUserPrincipal.fromUser(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("overview"))
                .andExpect(content().string(containsString("Live Overview")));
    }

    @Test
    void liveOverviewIsAdminOnlyByDefault() throws Exception {
        AppUser monitoring = userRepository.findByAzureAdId("dev-monitoring").orElseThrow();

        mockMvc.perform(get("/transactions/overview").with(user(BreUserPrincipal.fromUser(monitoring))))
                .andExpect(status().is3xxRedirection());
    }
}
