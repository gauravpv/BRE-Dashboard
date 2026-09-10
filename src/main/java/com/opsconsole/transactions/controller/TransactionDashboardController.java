package com.opsconsole.transactions.controller;

import com.opsconsole.auth.domain.AppTab;
import com.opsconsole.transactions.service.TransactionDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TransactionDashboardController {

    private final TransactionDashboardService dashboardService;

    public TransactionDashboardController(TransactionDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/transactions")
    public String transactionDashboard(Model model) {
        model.addAttribute("activeNav", AppTab.TRANSACTIONS.id());
        model.addAttribute("dashboard", dashboardService.loadDashboard());
        model.addAttribute("refreshSeconds", dashboardService.refreshSeconds());
        return "transaction-dashboard";
    }
}
