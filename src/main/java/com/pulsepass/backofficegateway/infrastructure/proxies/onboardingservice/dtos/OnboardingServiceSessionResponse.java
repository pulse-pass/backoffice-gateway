package com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.dtos;

public record OnboardingServiceSessionResponse(
        String accessToken,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn,
        String tokenType
) {
}
