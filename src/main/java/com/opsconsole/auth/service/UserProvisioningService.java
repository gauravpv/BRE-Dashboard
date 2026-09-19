package com.opsconsole.auth.service;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opsconsole.auth.config.AuthProperties;
import com.opsconsole.auth.domain.AccountStatus;
import com.opsconsole.auth.domain.AppRole;
import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.repository.AppRoleRepository;
import com.opsconsole.auth.repository.AppUserRepository;
import com.opsconsole.auth.service.UserActivityLogService;
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
                .map(existing -> updateOnLogin(existing, displayName, email))
                .orElseGet(() -> userRepository.findByEmailIgnoreCase(email)
                        .map(existing -> linkExistingAccount(existing, azureAdId, displayName, email))
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

    private AppUser updateOnLogin(AppUser user, String displayName, String email) {
        user.setDisplayName(displayName);
        if (email != null && !email.isBlank()) {
            if (user.getEmail().equalsIgnoreCase(email) || userRepository.findByEmailIgnoreCase(email).isEmpty()) {
                // keep existing email field immutable in DB for simplicity
            }
        }
        return userRepository.save(user);
    }

    private AppUser linkExistingAccount(
            AppUser user,
            String azureAdId,
            String displayName,
            String email
    ) {
        user.setAzureAdId(azureAdId);
        return updateOnLogin(user, displayName, email);
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
        return "unknown@opsconsole.local";
    }
}
