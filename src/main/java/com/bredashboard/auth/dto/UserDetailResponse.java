package com.bredashboard.auth.dto;

import com.bredashboard.auth.domain.AppUser;

import java.time.Instant;

public record UserDetailResponse(
        Long id,
        String email,
        String displayName,
        String jobTitle,
        String roleCode,
        String roleName,
        String accountStatus,
        boolean enabled,
        Instant createdAt,
        Instant lastLoginAt,
        String azureAdId,
        boolean hasLocalPassword,
        int activeSessionCount
) {
    public static UserDetailResponse from(AppUser user) {
        return from(user, 0);
    }

    public static UserDetailResponse from(AppUser user, int activeSessionCount) {
        return new UserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getJobTitle(),
                user.getRole().getCode(),
                user.getRole().getName(),
                user.getAccountStatus().name(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getAzureAdId(),
                user.hasLocalPassword(),
                activeSessionCount
        );
    }
}
