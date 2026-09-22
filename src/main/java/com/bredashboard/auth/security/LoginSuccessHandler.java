package com.bredashboard.auth.security;

import com.bredashboard.activity.service.ActivityFeedService;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.BreOidcUser;
import com.bredashboard.auth.domain.BreUserPrincipal;
import com.bredashboard.auth.repository.AppUserRepository;
import com.bredashboard.auth.service.UserActivityLogService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final String SIGN_IN_AZURE = "Microsoft Entra ID";
    private static final String SIGN_IN_LOCAL = "local password";

    private final AppUserRepository userRepository;
    private final ActivityFeedService activityFeedService;
    private final UserActivityLogService userActivityLogService;

    public LoginSuccessHandler(
            AppUserRepository userRepository,
            ActivityFeedService activityFeedService,
            UserActivityLogService userActivityLogService
    ) {
        this.userRepository = userRepository;
        this.activityFeedService = activityFeedService;
        this.userActivityLogService = userActivityLogService;
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        AppUser resolved = resolveUser(authentication);
        if (resolved != null && resolved.getId() != null) {
            AppUser user = userRepository.findById(resolved.getId()).orElse(resolved);
            boolean azureLogin = authentication.getPrincipal() instanceof BreOidcUser;
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
            activityFeedService.recordLogin(user);
            userActivityLogService.recordLogin(user, azureLogin ? SIGN_IN_AZURE : SIGN_IN_LOCAL);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private static AppUser resolveUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof BreOidcUser oidcUser) {
            return oidcUser.getAppUser();
        }
        if (principal instanceof BreUserPrincipal breUser) {
            return breUser.getUser();
        }
        return null;
    }
}
