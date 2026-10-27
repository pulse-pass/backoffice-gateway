package com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice;

import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceProxy;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.dtos.OnboardingServiceRefreshTokenRequest;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.dtos.OnboardingServiceSessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OnboardingServiceProxy extends ServiceProxy {

    private final JsonMapper jsonMapper;

    public OnboardingServiceProxy(
            @Value("${app.onboarding-service.url}") String url,
            RestClient.Builder restClientBuilder,
            JsonMapper jsonMapper
    ) {
        super("onboarding-service", url, restClientBuilder);
        this.jsonMapper = jsonMapper;
    }

    private ResponseCookie buildCookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private ResponseEntity<byte[]> buildSessionResponse(ResponseEntity<byte[]> response) {
        OnboardingServiceSessionResponse session = this.jsonMapper.readValue(
                response.getBody(), OnboardingServiceSessionResponse.class);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, this.buildCookie(
                        "pulsepass_access_token", session.accessToken(), "/",
                        Duration.ofSeconds(session.expiresIn())).toString())
                .header(HttpHeaders.SET_COOKIE, this.buildCookie(
                        "pulsepass_refresh_token", session.refreshToken(), "/auth",
                        Duration.ofSeconds(session.refreshExpiresIn())).toString())
                .build();
    }

    private ResponseEntity<byte[]> buildClearedResponse(ResponseEntity<byte[]> response) {
        return ResponseEntity
                .status(response.getStatusCode())
                .header(HttpHeaders.SET_COOKIE,
                        this.buildCookie("pulsepass_access_token", "", "/", Duration.ZERO).toString())
                .header(HttpHeaders.SET_COOKIE,
                        this.buildCookie("pulsepass_refresh_token", "", "/auth", Duration.ZERO).toString())
                .contentType(response.getHeaders().getContentType())
                .body(response.getBody());
    }

    public ResponseEntity<byte[]> createSession(HttpServletRequest request, byte[] body) {
        ResponseEntity<byte[]> response = this.forward(
                HttpMethod.POST, "/onboarding/auth/login", request, null, body, null);
        if (response.getStatusCode().value() != HttpStatus.OK.value()) {
            return response;
        }
        return this.buildSessionResponse(response);
    }

    public ResponseEntity<byte[]> refreshSession(HttpServletRequest request, String refreshToken) {
        ResponseEntity<byte[]> response = this.forward(
                HttpMethod.POST, "/onboarding/auth/refresh", request, MediaType.APPLICATION_JSON,
                this.jsonMapper.writeValueAsBytes(new OnboardingServiceRefreshTokenRequest(refreshToken)), null);
        if (response.getStatusCode().value() == HttpStatus.OK.value()) {
            return this.buildSessionResponse(response);
        }
        if (response.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            return this.buildClearedResponse(response);
        }
        return response;
    }

    public ResponseEntity<byte[]> deleteSession(HttpServletRequest request, String refreshToken, Jwt jwt) {
        ResponseEntity<byte[]> response = this.forward(
                HttpMethod.POST, "/onboarding/auth/logout", request, MediaType.APPLICATION_JSON,
                this.jsonMapper.writeValueAsBytes(new OnboardingServiceRefreshTokenRequest(refreshToken)), jwt);
        return this.buildClearedResponse(response);
    }
}
