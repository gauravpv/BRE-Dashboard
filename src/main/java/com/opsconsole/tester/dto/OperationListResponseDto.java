package com.opsconsole.tester.dto;

import java.util.List;

public record OperationListResponseDto(
        String environment,
        String baseUrl,
        String description,
        String statusCode,
        List<OperationEntryDto> operations,
        TokenStatusDto tokenStatus,
        boolean fromCache,
        Long listExpiresInSeconds
) {

    public OperationListResponseDto withTokenStatus(TokenStatusDto status) {
        return new OperationListResponseDto(
                environment, baseUrl, description, statusCode, operations, status, fromCache, listExpiresInSeconds);
    }

    public OperationListResponseDto withCacheMeta(boolean cached, Long expiresInSeconds) {
        return new OperationListResponseDto(
                environment, baseUrl, description, statusCode, operations, tokenStatus, cached, expiresInSeconds);
    }
}
