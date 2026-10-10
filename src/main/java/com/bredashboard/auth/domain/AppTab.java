package com.bredashboard.auth.domain;

import java.util.Arrays;
import java.util.List;

public enum AppTab {
    DASHBOARD("dashboard", "Dashboard", "/"),
    HEALTH("health", "System Health", "/health"),
    TRANSACTIONS("transactions", "Transaction Analyser", "/transactions"),
    OVERVIEW("overview", "Live Overview", "/transactions/overview"),
    HOURLY_TREND("hourly-trend", "Auth Transactions", "/transactions/hourly"),
    RESPONSE_TIME("response-time", "Response Time", "/transactions/response-time"),
    CHANNEL_HITS("channel-hits", "Channel Wise Hits", "/transactions/channels"),
    LOAN_AVERAGE("loan-average", "Avg Loan Amount", "/transactions/loan-average"),
    OTP_HITS("otp-hits", "OTP Hits", "/transactions/otp-hits"),
    TESTER("tester", "Tester", "/tester"),
    ADMIN("admin", "System Admin", "/admin"),
    USERS("users", "User Admin", "/users"),
    DEV_UTILS("dev-utils", "Developer Utils", "/dev-utils");

    private final String id;
    private final String label;
    private final String path;

    AppTab(String id, String label, String path) {
        this.id = id;
        this.label = label;
        this.path = path;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String path() {
        return path;
    }

    public static List<AppTab> permissionTabs() {
        return Arrays.stream(values())
                .filter(tab -> tab != TRANSACTIONS)
                .toList();
    }
}
