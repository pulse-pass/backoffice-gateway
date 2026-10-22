package com.pulsepass.backofficegateway.api.controllers;

import com.pulsepass.backofficegateway.api.sessions.OnboardingSessionResponse;
import com.pulsepass.backofficegateway.api.sessions.SessionCookies;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class OnboardingController {

    private final OnboardingServiceProxy onboardingServiceProxy;
    private final JsonMapper jsonMapper;

    public OnboardingController(OnboardingServiceProxy onboardingServiceProxy, JsonMapper jsonMapper) {
        this.onboardingServiceProxy = onboardingServiceProxy;
        this.jsonMapper = jsonMapper;
    }

    private ResponseEntity<byte[]> buildSessionResponse(ResponseEntity<byte[]> response) {
        OnboardingSessionResponse session = this.jsonMapper.readValue(
                response.getBody(), OnboardingSessionResponse.class);
        return ResponseEntity.noContent()
                .headers(headers -> SessionCookies.addSession(headers, session))
                .build();
    }

    private ResponseEntity<byte[]> buildClearedResponse(ResponseEntity<byte[]> response) {
        return ResponseEntity
                .status(response.getStatusCode())
                .headers(SessionCookies::clearSession)
                .contentType(response.getHeaders().getContentType())
                .body(response.getBody());
    }

    @PostMapping("/legal_persons")
    public ResponseEntity<byte[]> createLegalPerson(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        return this.onboardingServiceProxy.forward(HttpMethod.POST, "/onboarding/legal_persons", request, body);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<byte[]> createSession(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        ResponseEntity<byte[]> response = this.onboardingServiceProxy.forward(
                HttpMethod.POST, "/onboarding/auth/login", request, body);
        if (response.getStatusCode().value() != HttpStatus.OK.value()) {
            return response;
        }
        return this.buildSessionResponse(response);
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<byte[]> refreshSession(
            HttpServletRequest request,
            @CookieValue(SessionCookies.REFRESH_TOKEN) String refreshToken
    ) {
        byte[] refreshBody = this.jsonMapper.writeValueAsBytes(Map.of("refresh_token", refreshToken));
        ResponseEntity<byte[]> response = this.onboardingServiceProxy.forwardJson(
                HttpMethod.POST, "/onboarding/auth/refresh", request, refreshBody);
        if (response.getStatusCode().value() == HttpStatus.OK.value()) {
            return this.buildSessionResponse(response);
        }
        if (response.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            return this.buildClearedResponse(response);
        }
        return response;
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<byte[]> deleteSession(
            HttpServletRequest request,
            @CookieValue(SessionCookies.REFRESH_TOKEN) String refreshToken,
            @AuthenticationPrincipal Jwt jwt
    ) {
        byte[] logoutBody = this.jsonMapper.writeValueAsBytes(Map.of("refresh_token", refreshToken));
        ResponseEntity<byte[]> response = this.onboardingServiceProxy.forwardJson(
                HttpMethod.POST, "/onboarding/auth/logout", request, logoutBody, jwt);
        return this.buildClearedResponse(response);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<byte[]> readMe(HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
        return this.onboardingServiceProxy.forward(HttpMethod.GET, "/onboarding/auth/me", request, null, jwt);
    }
}
