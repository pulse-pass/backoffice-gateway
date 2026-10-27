package com.pulsepass.backofficegateway.api.controllers;

import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnboardingController {

    private final OnboardingServiceProxy onboardingServiceProxy;

    public OnboardingController(OnboardingServiceProxy onboardingServiceProxy) {
        this.onboardingServiceProxy = onboardingServiceProxy;
    }

    @PostMapping("/legal_persons")
    public ResponseEntity<byte[]> createLegalPerson(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        return this.onboardingServiceProxy.forward(
                HttpMethod.POST, "/onboarding/legal_persons", request, null, body, null);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<byte[]> createSession(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        return this.onboardingServiceProxy.createSession(request, body);
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<byte[]> refreshSession(
            HttpServletRequest request,
            @CookieValue("pulsepass_refresh_token") String refreshToken
    ) {
        return this.onboardingServiceProxy.refreshSession(request, refreshToken);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<byte[]> deleteSession(
            HttpServletRequest request,
            @CookieValue("pulsepass_refresh_token") String refreshToken,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return this.onboardingServiceProxy.deleteSession(request, refreshToken, jwt);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<byte[]> readMe(HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
        return this.onboardingServiceProxy.forward(
                HttpMethod.GET, "/onboarding/auth/me", request, null, null, jwt);
    }
}
