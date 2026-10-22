package com.pulsepass.backofficegateway.unit.api.sessions;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.api.sessions.OnboardingSessionResponse;
import com.pulsepass.backofficegateway.api.sessions.SessionCookies;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class SessionCookiesTest {

    @Test
    void addSession_session_addsAccessCookieWithExpiresInAndRootPath() {
        HttpHeaders headers = new HttpHeaders();

        SessionCookies.addSession(headers, new OnboardingSessionResponse("access", 300, "refresh", 1800, "Bearer"));

        assertThat(headers.get(HttpHeaders.SET_COOKIE)).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_access_token=access;")
                .contains("Max-Age=300", "Path=/;", "Secure", "HttpOnly", "SameSite=Strict"));
    }

    @Test
    void addSession_session_addsRefreshCookieWithRefreshExpiresInAndAuthPath() {
        HttpHeaders headers = new HttpHeaders();

        SessionCookies.addSession(headers, new OnboardingSessionResponse("access", 300, "refresh", 1800, "Bearer"));

        assertThat(headers.get(HttpHeaders.SET_COOKIE)).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_refresh_token=refresh;")
                .contains("Max-Age=1800", "Path=/auth", "Secure", "HttpOnly", "SameSite=Strict"));
    }

    @Test
    void addSession_session_addsExactlyTwoCookies() {
        HttpHeaders headers = new HttpHeaders();

        SessionCookies.addSession(headers, new OnboardingSessionResponse("access", 300, "refresh", 1800, "Bearer"));

        assertThat(headers.get(HttpHeaders.SET_COOKIE)).hasSize(2);
    }

    @Test
    void clearSession_headers_expiresAccessCookieWithSameNamePathAndAttributes() {
        HttpHeaders headers = new HttpHeaders();

        SessionCookies.clearSession(headers);

        assertThat(headers.get(HttpHeaders.SET_COOKIE)).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_access_token=;")
                .contains("Max-Age=0", "Path=/;", "Secure", "HttpOnly", "SameSite=Strict"));
    }

    @Test
    void clearSession_headers_expiresRefreshCookieWithSameNamePathAndAttributes() {
        HttpHeaders headers = new HttpHeaders();

        SessionCookies.clearSession(headers);

        assertThat(headers.get(HttpHeaders.SET_COOKIE)).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_refresh_token=;")
                .contains("Max-Age=0", "Path=/auth", "Secure", "HttpOnly", "SameSite=Strict"));
    }
}
