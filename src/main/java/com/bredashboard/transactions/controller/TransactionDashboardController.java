package com.bredashboard.transactions.controller;

import com.bredashboard.auth.domain.AppTab;
import com.bredashboard.transactions.service.TransactionDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TransactionDashboardController {

    private final TransactionDashboardService dashboardService;

    public TransactionDashboardController(TransactionDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/transactions/hourly")
    public String hourlyTrend(
            @RequestParam(name = "view", defaultValue = "minute") String view,
            @RequestParam(name = "fragment", defaultValue = "false") boolean fragment,
            Model model
    ) {
        boolean showHours = "hour".equals(view);
        model.addAttribute("activeNav", AppTab.HOURLY_TREND.id());
        model.addAttribute("showHours", showHours);
        model.addAttribute("refreshSeconds", dashboardService.refreshSeconds());
        model.addAttribute("reportUrl", "/transactions/hourly?fragment=true&view=" + (showHours ? "hour" : "minute"));
        model.addAttribute("dashboard", dashboardService.loadHourlyTrend(showHours));
        if (fragment) {
            return "hourly-trend :: live-shell";
        }
        return "hourly-trend";
    }

    @GetMapping("/transactions/response-time")
    public String responseTime(@RequestParam(name = "fragment", defaultValue = "false") boolean fragment, Model model) {
        model.addAttribute("activeNav", AppTab.RESPONSE_TIME.id());
        model.addAttribute("refreshSeconds", dashboardService.refreshSeconds());
        model.addAttribute("reportUrl", "/transactions/response-time?fragment=true");
        model.addAttribute("dashboard", dashboardService.loadResponseTimeDistribution());
        if (fragment) {
            return "response-time :: live-shell";
        }
        return "response-time";
    }

    @GetMapping("/transactions/channels")
    public String channelHits(@RequestParam(name = "fragment", defaultValue = "false") boolean fragment, Model model) {
        model.addAttribute("activeNav", AppTab.CHANNEL_HITS.id());
        model.addAttribute("refreshSeconds", dashboardService.refreshSeconds());
        model.addAttribute("reportUrl", "/transactions/channels?fragment=true");
        model.addAttribute("dashboard", dashboardService.loadChannelHits());
        if (fragment) {
            return "channel-hits :: live-shell";
        }
        return "channel-hits";
    }

    @GetMapping("/transactions/loan-average")
    public String loanAverage(@RequestParam(name = "fragment", defaultValue = "false") boolean fragment, Model model) {
        model.addAttribute("activeNav", AppTab.LOAN_AVERAGE.id());
        model.addAttribute("refreshSeconds", dashboardService.refreshSeconds());
        model.addAttribute("reportUrl", "/transactions/loan-average?fragment=true");
        model.addAttribute("dashboard", dashboardService.loadLoanAverage());
        if (fragment) {
            return "loan-average :: live-shell";
        }
        return "loan-average";
    }

    @GetMapping("/transactions/overview")
    public String overview(@RequestParam(name = "fragment", defaultValue = "false") boolean fragment, Model model) {
        model.addAttribute("activeNav", AppTab.OVERVIEW.id());
        model.addAttribute("refreshSeconds", dashboardService.refreshSeconds());
        model.addAttribute("reportUrl", "/transactions/overview?fragment=true");
        model.addAttribute("dashboard", dashboardService.loadOverview());
        if (fragment) {
            return "overview :: live-shell";
        }
        return "overview";
    }

    @GetMapping("/transactions/otp-hits")
    public String otpHits(@RequestParam(name = "fragment", defaultValue = "false") boolean fragment, Model model) {
        model.addAttribute("activeNav", AppTab.OTP_HITS.id());
        model.addAttribute("refreshSeconds", dashboardService.refreshSeconds());
        model.addAttribute("reportUrl", "/transactions/otp-hits?fragment=true");
        model.addAttribute("dashboard", dashboardService.loadOtpHits());
        if (fragment) {
            return "otp-hits :: live-shell";
        }
        return "otp-hits";
    }
}
