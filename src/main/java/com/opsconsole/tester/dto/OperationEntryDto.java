package com.opsconsole.tester.dto;

public record OperationEntryDto(
        String publicUrl,
        String slug,
        String appVersion,
        String module,
        String apiVersion,
        String fullUrl,
        String encryptionKey,
        String encryptionIv
) {
}
