package com.opsconsole.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AzureLoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        OAuth2AuthenticationException oauthError = findOAuthError(exception);
        String target = "/login?error";
        if (oauthError != null) {
            String code = oauthError.getError().getErrorCode();
            if ("access_pending".equals(code)) {
                target = "/login?pending";
            } else if ("account_inactive".equals(code)) {
                target = "/login?inactive";
            }
        }
        response.sendRedirect(target);
    }

    private static OAuth2AuthenticationException findOAuthError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof OAuth2AuthenticationException oauthError) {
                return oauthError;
            }
            current = current.getCause();
        }
        return null;
    }
}
