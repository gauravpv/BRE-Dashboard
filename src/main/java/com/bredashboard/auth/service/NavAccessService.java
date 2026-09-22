package com.bredashboard.auth.service;

import com.bredashboard.auth.domain.AppTab;
import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.RoleTabAccess;
import com.bredashboard.auth.repository.RoleTabAccessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NavAccessService {

    private final RoleTabAccessRepository tabAccessRepository;

    public NavAccessService(RoleTabAccessRepository tabAccessRepository) {
        this.tabAccessRepository = tabAccessRepository;
    }

    public boolean canAccess(AppUser user, AppTab tab) {
        if (user == null || !user.isEnabled() || tab == null) {
            return false;
        }
        return allowedTabs(user).contains(tab);
    }

    public void require(AppUser user, AppTab tab) {
        if (!canAccess(user, tab)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, tab.label() + " access required");
        }
    }

    public Map<String, Boolean> navAccessMap(AppUser user) {
        if (user == null || !user.isEnabled()) {
            return Map.of();
        }
        Set<AppTab> allowed = allowedTabs(user);
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (AppTab tab : AppTab.values()) {
            map.put(tab.id(), allowed.contains(tab));
        }
        return map;
    }

    public AppTab tabForPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return AppTab.DASHBOARD;
        }
        for (AppTab tab : AppTab.values()) {
            if (tab.path().equals(path)) {
                return tab;
            }
        }
        return null;
    }

    private Set<AppTab> allowedTabs(AppUser user) {
        List<RoleTabAccess> rows = tabAccessRepository.findByRoleIdAndAllowedTrue(user.getRole().getId());
        if (rows.isEmpty()) {
            return EnumSet.noneOf(AppTab.class);
        }
        return rows.stream().map(RoleTabAccess::getTab).collect(Collectors.toCollection(() -> EnumSet.noneOf(AppTab.class)));
    }
}
