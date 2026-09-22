package com.bredashboard.transactions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bredashboard.transaction-dashboard")
public class TransactionDashboardProperties {

    private String jdbcUrl = "";
    private String username = "";
    private String password = "";
    private String schema = "bre_underwriting";
    private String minuteView = "festival_summary_detailed_mins_new";
    private String hourlyView = "festival_summary_detailed_new";
    private String otpTable = "transaction_details_srcreq_otp";
    private int refreshSeconds = 60;

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getMinuteView() {
        return minuteView;
    }

    public void setMinuteView(String minuteView) {
        this.minuteView = minuteView;
    }

    public String getHourlyView() {
        return hourlyView;
    }

    public void setHourlyView(String hourlyView) {
        this.hourlyView = hourlyView;
    }

    public String getOtpTable() {
        return otpTable;
    }

    public void setOtpTable(String otpTable) {
        this.otpTable = otpTable;
    }

    public int getRefreshSeconds() {
        return refreshSeconds;
    }

    public void setRefreshSeconds(int refreshSeconds) {
        this.refreshSeconds = Math.max(15, refreshSeconds);
    }
}
