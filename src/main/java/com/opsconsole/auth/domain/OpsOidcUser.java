package com.opsconsole.auth.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class OpsOidcUser implements OidcUser {

    private final OidcUser delegate;
    private final AppUser appUser;
    private final Collection<? extends GrantedAuthority> authorities;

    public OpsOidcUser(OidcUser delegate, AppUser appUser) {
        this.delegate = delegate;
        this.appUser = appUser;
        this.authorities = OpsUserPrincipal.fromUser(appUser).getAuthorities();
    }

    public AppUser getAppUser() {
        return appUser;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return appUser.getAzureAdId();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof OpsOidcUser principal) {
            return Objects.equals(getName(), principal.getName());
        }
        if (other instanceof OpsUserPrincipal principal) {
            return Objects.equals(getName(), principal.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName());
    }
}
