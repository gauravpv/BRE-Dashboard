package com.opsconsole.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Azure AD object id (oid claim) or dev identifier. */
    @Column(name = "azure_ad_id", nullable = false, unique = true, length = 64)
    private String azureAdId;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(length = 200)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private AppRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** BCrypt hash for local sign-in; null for Azure AD–only accounts. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    protected AppUser() {
    }

    public AppUser(String azureAdId, String email, String displayName, AppRole role) {
        this.azureAdId = azureAdId;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public AppUser(String azureAdId, String email, String displayName, AppRole role, String passwordHash) {
        this(azureAdId, email, displayName, role);
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getAzureAdId() {
        return azureAdId;
    }

    public void setAzureAdId(String azureAdId) {
        this.azureAdId = azureAdId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public void setEnabled(boolean enabled) {
        this.accountStatus = enabled ? AccountStatus.ACTIVE : AccountStatus.INACTIVE;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus == null ? AccountStatus.PENDING : accountStatus;
    }

    public AppRole getRole() {
        return role;
    }

    public void setRole(AppRole role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean hasLocalPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
