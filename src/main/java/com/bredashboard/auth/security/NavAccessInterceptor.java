package com.bredashboard.auth.security;

import com.bredashboard.auth.domain.AppTab;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.CurrentUser;
import com.bredashboard.auth.service.NavAccessService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class NavAccessInterceptor implements HandlerInterceptor {

    public static final String NAV_ACCESS_ATTRIBUTE = "bredashboard.navAccess";

    private final NavAccessService navAccessService;

    public NavAccessInterceptor(NavAccessService navAccessService) {
        this.navAccessService = navAccessService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/api/health") || path.startsWith("/actuator")) {
            return true;
        }

        AppUser user = CurrentUser.userOrNull();
        if (user == null) {
            return true;
        }

        Map<String, Boolean> navAccess = navAccessService.navAccessMap(user);
        request.setAttribute(NAV_ACCESS_ATTRIBUTE, navAccess);

        AppTab tab = navAccessService.tabForPath(path);
        if (tab == null) {
            return true;
        }

        if (!Boolean.TRUE.equals(navAccess.get(tab.id()))) {
            boolean canDashboard = Boolean.TRUE.equals(navAccess.get(AppTab.DASHBOARD.id()));
            String redirect = canDashboard ? "/?denied=1" : "/login?error";
            response.sendRedirect(redirect);
            return false;
        }
        return true;
    }
}
