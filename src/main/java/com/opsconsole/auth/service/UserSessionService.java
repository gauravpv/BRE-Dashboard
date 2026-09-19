package com.opsconsole.auth.service;

import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.auth.domain.OpsOidcUser;
import com.opsconsole.auth.domain.OpsUserPrincipal;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserSessionService {

    private final SessionRegistry sessionRegistry;

    public UserSessionService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public int activeSessionCount(Long userId) {
        return activeSessions(userId).size();
    }

    public int revokeAll(Long userId) {
        List<SessionInformation> sessions = activeSessions(userId);
        sessions.forEach(SessionInformation::expireNow);
        return sessions.size();
    }

    private List<SessionInformation> activeSessions(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> userId.equals(userId(principal)))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .filter(session -> !session.isExpired())
                .toList();
    }

    private static Long userId(Object principal) {
        AppUser user = null;
        if (principal instanceof OpsOidcUser oidcUser) {
            user = oidcUser.getAppUser();
        } else if (principal instanceof OpsUserPrincipal opsUser) {
            user = opsUser.getUser();
        }
        return user != null ? user.getId() : null;
    }
}
