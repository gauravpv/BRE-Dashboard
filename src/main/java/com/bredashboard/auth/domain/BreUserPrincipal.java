package com.bredashboard.auth.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BreUserPrincipal implements OAuth2User, UserDetails {

    private final AppUser user;
    private final Map<String, Object> attributes;
    private final String passwordHash;

    public BreUserPrincipal(AppUser user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes == null ? Map.of() : attributes;
        this.passwordHash = user.getPasswordHash();
    }

    public static BreUserPrincipal fromUser(AppUser user) {
        return new BreUserPrincipal(user, Map.of(
                "oid", user.getAzureAdId(),
                "email", user.getEmail(),
                "name", user.getDisplayName()
        ));
    }

    public AppUser getUser() {
        return user;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getCode()));
    }

    @Override
    public String getPassword() {
        return passwordHash == null ? "" : passwordHash;
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.isEnabled();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    @Override
    public String getName() {
        return user.getAzureAdId();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof BreUserPrincipal principal) {
            return Objects.equals(getName(), principal.getName());
        }
        if (other instanceof BreOidcUser principal) {
            return Objects.equals(getName(), principal.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName());
    }
}
