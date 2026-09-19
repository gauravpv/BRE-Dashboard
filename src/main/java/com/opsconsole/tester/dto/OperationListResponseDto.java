package com.opsconsole.tester.dto;

import java.util.List;

public record OperationListResponseDto(
        String environment,
        String baseUrl,
        String description,
        String statusCode,
        String listEncryptionKey,
        String listEncryptionIv,
        String responseEncryptionKey,
        List<OperationEntryDto> operations,
        TokenStatusDto tokenStatus
) {

    /** Returns a copy carrying the token state fetched immediately after this list. */
    public OperationListResponseDto withTokenStatus(TokenStatusDto status) {
        return new OperationListResponseDto(
                environment,
                baseUrl,
                description,
                statusCode,
                listEncryptionKey,
                listEncryptionIv,
                responseEncryptionKey,
                operations,
                status
        );
    }
}