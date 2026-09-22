package com.bredashboard.auth.domain;

public enum AppTab {
    DASHBOARD("dashboard", "Dashboard", "/"),
    HEALTH("health", "System Health", "/health"),
    TRANSACTIONS("transactions", "Transaction Analytics", "/transactions"),
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
}
