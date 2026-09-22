package com.bredashboard.auth.domain;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "role_tab_access",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "tab"})
)
public class RoleTabAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private AppRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AppTab tab;

    @Column(nullable = false)
    private boolean allowed;

    protected RoleTabAccess() {
    }

    public RoleTabAccess(AppRole role, AppTab tab, boolean allowed) {
        this.role = role;
        this.tab = tab;
        this.allowed = allowed;
    }

    public Long getId() {
        return id;
    }

    public AppRole getRole() {
        return role;
    }

    public AppTab getTab() {
        return tab;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }
}
