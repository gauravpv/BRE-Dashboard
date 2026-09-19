package com.opsconsole.tester.dto;

/**
 * State of the cached {@code token} header for an environment, surfaced on the tester page so
 * the user can see the token is ready before selecting an API.
 */
public record TokenStatusDto(
        boolean ready,
        String preview,
        Long expiresInSeconds,
        String error
) {

    public static TokenStatusDto ready(String token, long expiresInSeconds) {
        return new TokenStatusDto(true, mask(token), expiresInSeconds, null);
    }

    public static TokenStatusDto failed(String message) {
        return new TokenStatusDto(false, null, null, message);
    }

    /** Never expose the raw token to the browser. */
    private static String mask(String token) {
        if (token == null || token.length() <= 10) {
            return "********";
        }
        return token.substring(0, 6) + "…" + token.substring(token.length() - 4);
    }
}
