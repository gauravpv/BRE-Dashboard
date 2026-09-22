package com.bredashboard.tester.dto;

public record BajajInvokeResponse(
        int statusCode,
        long durationMs,
        int responseSizeBytes,
        String requestUrl,
        String decryptedBody,
        String error
) {
}
