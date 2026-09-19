package com.opsconsole.auth.service;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import com.opsconsole.auth.domain.AccountStatus;
import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.domain.OpsOidcUser;
@Component
public class AzureOidcUserService extends OidcUserService {

    private final UserProvisioningService provisioningService;
    private final UserActivityLogService activityLogService;

    public AzureOidcUserService(
            UserProvisioningService provisioningService,
            UserActivityLogService activityLogService
    ) {
        this.provisioningService = provisioningService;
        this.activityLogService = activityLogService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        AppUser appUser = provisioningService.provisionFromOAuth(oidcUser);
        if (appUser.getAccountStatus() == AccountStatus.PENDING) {
            activityLogService.recordAccessDenied(appUser, "Sign-in denied while awaiting administrator approval");
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("access_pending"),
                    "Administrator approval is required"
            );
        }
        if (appUser.getAccountStatus() == AccountStatus.INACTIVE) {
            activityLogService.recordAccessDenied(appUser, "Sign-in denied because the account is inactive");
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_inactive"),
                    "Account is inactive"
            );
        }
        return new OpsOidcUser(oidcUser, appUser);
    }
}
