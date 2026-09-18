package com.opsconsole.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opsconsole.auth")
public class AuthProperties {

    /** dev = local sign-in; azure = Microsoft Entra ID OAuth2. */
    private String mode = "dev";

    /** Default role code for first-time Azure AD sign-ins. */
    private String defaultRoleCode = "MONITORING";

    /** When true, known local development accounts are created. Must stay false in UAT/PROD. */
    private boolean seedDevUsers = false;

    private String bootstrapEmail = "";
    private String bootstrapPassword = "";
    private String bootstrapDisplayName = "Administrator";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isAzureMode() {
        return "azure".equalsIgnoreCase(mode);
    }

    public boolean isDevMode() {
        return !isAzureMode();
    }

    public String getDefaultRoleCode() {
        return defaultRoleCode;
    }

    public void setDefaultRoleCode(String defaultRoleCode) {
        this.defaultRoleCode = defaultRoleCode;
    }

    public boolean isSeedDevUsers() {
        return seedDevUsers;
    }

    public void setSeedDevUsers(boolean seedDevUsers) {
        this.seedDevUsers = seedDevUsers;
    }

    public String getBootstrapEmail() {
        return bootstrapEmail;
    }

    public void setBootstrapEmail(String bootstrapEmail) {
        this.bootstrapEmail = bootstrapEmail;
    }

    public String getBootstrapPassword() {
        return bootstrapPassword;
    }

    public void setBootstrapPassword(String bootstrapPassword) {
        this.bootstrapPassword = bootstrapPassword;
    }

    public String getBootstrapDisplayName() {
        return bootstrapDisplayName;
    }

    public void setBootstrapDisplayName(String bootstrapDisplayName) {
        this.bootstrapDisplayName = bootstrapDisplayName;
    }
}
