package com.bredashboard.auth.service;

import com.bredashboard.auth.config.AuthProperties;
import com.bredashboard.auth.domain.AccountStatus;
import com.bredashboard.auth.domain.AppRole;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.repository.AppRoleRepository;
import com.bredashboard.auth.repository.AppUserRepository;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProvisioningService {

    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final AuthProperties authProperties;
    private final UserActivityLogService userActivityLogService;

    public UserProvisioningService(
            AppUserRepository userRepository,
            AppRoleRepository roleRepository,
            AuthProperties authProperties,
            UserActivityLogService userActivityLogService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authProperties = authProperties;
        this.userActivityLogService = userActivityLogService;
    }

    @Transactional
    public AppUser provisionFromOAuth(OAuth2User oauthUser) {
        String azureAdId = requireClaim(oauthUser, "oid", "sub");
        String email = firstNonBlank(
                oauthUser.getAttribute("preferred_username"),
                oauthUser.getAttribute("email"),
                oauthUser.getAttribute("upn")
        );
        String displayName = firstNonBlank(oauthUser.getAttribute("name"), email);

        return userRepository.findByAzureAdId(azureAdId)
                .map(existing -> updateOnLogin(existing, displayName))
                .orElseGet(() -> userRepository.findByEmailIgnoreCase(email)
                        .map(existing -> linkExistingAccount(existing, azureAdId, displayName))
                        .orElseGet(() -> createUser(azureAdId, email, displayName)));
    }

    private AppUser createUser(String azureAdId, String email, String displayName) {
        AppRole defaultRole = roleRepository.findByCode(authProperties.getDefaultRoleCode())
                .orElseGet(() -> roleRepository.findByCode("MONITORING")
                        .orElseThrow(() -> new IllegalStateException("Default role not configured")));

        AppUser user = new AppUser(azureAdId, email.toLowerCase(), displayName, defaultRole);
        user.setAccountStatus(AccountStatus.PENDING);
        AppUser saved = userRepository.save(user);
        userActivityLogService.recordAzureProvision(saved);
        return saved;
    }

    private AppUser updateOnLogin(AppUser user, String displayName) {
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }

    private AppUser linkExistingAccount(AppUser user, String azureAdId, String displayName) {
        user.setAzureAdId(azureAdId);
        return updateOnLogin(user, displayName);
    }

    private static String requireClaim(OAuth2User user, String... keys) {
        for (String key : keys) {
            Object value = user.getAttribute(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        throw new IllegalStateException("Azure AD login missing oid/sub claim");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "unknown@bredashboard.local";
    }
}
