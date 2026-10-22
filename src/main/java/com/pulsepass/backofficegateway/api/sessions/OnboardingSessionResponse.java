package com.pulsepass.backofficegateway.api.sessions;

public record OnboardingSessionResponse(
        String accessToken,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn,
        String tokenType
) {
}
