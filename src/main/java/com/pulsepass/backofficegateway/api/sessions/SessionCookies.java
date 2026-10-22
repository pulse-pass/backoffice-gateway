package com.pulsepass.backofficegateway.api.sessions;

import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class SessionCookies {

    public static final String ACCESS_TOKEN = "pulsepass_access_token";
    public static final String REFRESH_TOKEN = "pulsepass_refresh_token";

    private static final String ACCESS_PATH = "/";
    private static final String REFRESH_PATH = "/auth";

    private SessionCookies() {
    }

    private static ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    public static void addSession(HttpHeaders headers, OnboardingSessionResponse session) {
        headers.add(HttpHeaders.SET_COOKIE, build(
                ACCESS_TOKEN, session.accessToken(), ACCESS_PATH, Duration.ofSeconds(session.expiresIn())).toString());
        headers.add(HttpHeaders.SET_COOKIE, build(
                REFRESH_TOKEN, session.refreshToken(), REFRESH_PATH,
                Duration.ofSeconds(session.refreshExpiresIn())).toString());
    }

    public static void clearSession(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, build(ACCESS_TOKEN, "", ACCESS_PATH, Duration.ZERO).toString());
        headers.add(HttpHeaders.SET_COOKIE, build(REFRESH_TOKEN, "", REFRESH_PATH, Duration.ZERO).toString());
    }
}
