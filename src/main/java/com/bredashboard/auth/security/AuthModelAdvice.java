package com.bredashboard.auth.security;

import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.CurrentUser;
import com.bredashboard.auth.service.NavAccessService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

@ControllerAdvice
public class AuthModelAdvice {

    private final NavAccessService navAccessService;

    public AuthModelAdvice(NavAccessService navAccessService) {
        this.navAccessService = navAccessService;
    }

    @ModelAttribute("navAccess")
    public Map<String, Boolean> navAccess(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/")) {
            return null;
        }
        Object cached = request.getAttribute(NavAccessInterceptor.NAV_ACCESS_ATTRIBUTE);
        if (cached instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> nav = (Map<String, Boolean>) map;
            return nav;
        }
        AppUser user = CurrentUser.userOrNull();
        if (user == null) {
            return null;
        }
        return navAccessService.navAccessMap(user);
    }

    @ModelAttribute("currentUser")
    public AppUser currentUser() {
        return CurrentUser.userOrNull();
    }
}
