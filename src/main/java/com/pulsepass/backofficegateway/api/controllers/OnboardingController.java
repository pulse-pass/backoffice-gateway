package com.pulsepass.backofficegateway.api.controllers;

import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

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

    @GetMapping("/persons/{personKey}")
    public ResponseEntity<byte[]> readPerson(
            HttpServletRequest request,
            @PathVariable String personKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return this.onboardingServiceProxy.forward(
                HttpMethod.GET,
                "/onboarding/persons/" + UriUtils.encodePathSegment(personKey, StandardCharsets.UTF_8),
                request, null, null, jwt);
    }

    @GetMapping("/internal/persons/{personKey}")
    public ResponseEntity<byte[]> readInternalPerson(
            HttpServletRequest request,
            @PathVariable String personKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return this.onboardingServiceProxy.forward(
                HttpMethod.GET,
                "/onboarding/internal/persons/"
                        + UriUtils.encodePathSegment(personKey, StandardCharsets.UTF_8),
                request, null, null, jwt);
    }

    @GetMapping("/legal_persons/{legalPersonKey}/collaborators")
    public ResponseEntity<byte[]> listLegalPersonCollaborators(
            HttpServletRequest request,
            @PathVariable String legalPersonKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return this.onboardingServiceProxy.forward(
                HttpMethod.GET,
                "/onboarding/legal_persons/"
                        + UriUtils.encodePathSegment(legalPersonKey, StandardCharsets.UTF_8) + "/collaborators",
                request, null, null, jwt);
    }

    @GetMapping("/internal/legal_persons/{legalPersonKey}/collaborators")
    public ResponseEntity<byte[]> listInternalLegalPersonCollaborators(
            HttpServletRequest request,
            @PathVariable String legalPersonKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return this.onboardingServiceProxy.forward(
                HttpMethod.GET,
                "/onboarding/internal/legal_persons/"
                        + UriUtils.encodePathSegment(legalPersonKey, StandardCharsets.UTF_8) + "/collaborators",
                request, null, null, jwt);
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
