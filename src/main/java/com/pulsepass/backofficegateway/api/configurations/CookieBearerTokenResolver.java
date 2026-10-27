package com.pulsepass.backofficegateway.api.configurations;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.WebUtils;

public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final RequestMatcher publicRoutes;

    public CookieBearerTokenResolver(RequestMatcher publicRoutes) {
        this.publicRoutes = publicRoutes;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (this.publicRoutes.matches(request)) {
            return null;
        }
        Cookie cookie = WebUtils.getCookie(request, "pulsepass_access_token");
        if (cookie == null) {
            return null;
        }
        return cookie.getValue();
    }
}
