package com.bredashboard.admin.util;

public final class AdminPathValidator {

    private AdminPathValidator() {
    }

    public static void validateScriptPath(String path, String fieldName) {
        validateAbsolutePath(path, fieldName);
    }

    public static void validatePropertiesPath(String path) {
        validateAbsolutePath(path, "propertiesPath");
    }

    private static void validateAbsolutePath(String path, String fieldName) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            throw new IllegalArgumentException(fieldName + " must be an absolute path");
        }
        if (trimmed.contains(";") || trimmed.contains("|") || trimmed.contains("$") || trimmed.contains("`")) {
            throw new IllegalArgumentException(fieldName + " contains invalid characters");
        }
    }
}
