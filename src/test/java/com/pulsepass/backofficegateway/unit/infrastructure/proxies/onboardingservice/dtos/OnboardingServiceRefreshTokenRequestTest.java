package com.pulsepass.backofficegateway.unit.infrastructure.proxies.onboardingservice.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.dtos.OnboardingServiceRefreshTokenRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class OnboardingServiceRefreshTokenRequestTest {

    @Test
    void writeValueAsString_refreshToken_serializesSnakeCaseField() {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();

        String json = mapper.writeValueAsString(new OnboardingServiceRefreshTokenRequest("refresh-1"));

        assertThat(json).isEqualTo("{\"refresh_token\":\"refresh-1\"}");
    }
}
