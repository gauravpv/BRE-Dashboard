package com.bredashboard.health.domain;

import java.util.Locale;

public final class ServiceEndpointMatcher {

    private ServiceEndpointMatcher() {
    }

    public static boolean matches(String hostA, int portA, String hostB, int portB) {
        return portA == portB && normalizeHost(hostA).equals(normalizeHost(hostB));
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if ("127.0.0.1".equals(normalized) || "localhost".equals(normalized)) {
            return "localhost";
        }
        return normalized;
    }
}
