package com.pulsepass.backofficegateway.unit.api.configurations;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.api.configurations.CookieBearerTokenResolver;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

class CookieBearerTokenResolverTest {

    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/login"));

    @Test
    void resolve_accessCookie_returnsCookieValue() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.setCookies(new Cookie("pulsepass_access_token", "token-value"));

        assertThat(this.resolver.resolve(request)).isEqualTo("token-value");
    }

    @Test
    void resolve_noCookies_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");

        assertThat(this.resolver.resolve(request)).isNull();
    }

    @Test
    void resolve_onlyRefreshCookie_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.setCookies(new Cookie("pulsepass_refresh_token", "refresh-value"));

        assertThat(this.resolver.resolve(request)).isNull();
    }

    @Test
    void resolve_authorizationHeaderOnly_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader("Authorization", "Bearer header-token");

        assertThat(this.resolver.resolve(request)).isNull();
    }

    @Test
    void resolve_publicRouteWithCookie_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setCookies(new Cookie("pulsepass_access_token", "token-value"));

        assertThat(this.resolver.resolve(request)).isNull();
    }
}
