package com.pulsepass.backofficegateway.unit.api.sessions;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.api.sessions.OnboardingSessionResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class OnboardingSessionResponseTest {

    @Test
    void readValue_snakeCaseBody_mapsEveryField() {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        String json = "{\"access_token\":\"a\",\"expires_in\":300,\"refresh_token\":\"r\","
                + "\"refresh_expires_in\":1800,\"token_type\":\"Bearer\",\"scope\":\"x\"}";

        OnboardingSessionResponse session = mapper.readValue(json, OnboardingSessionResponse.class);

        assertThat(session).isEqualTo(new OnboardingSessionResponse("a", 300, "r", 1800, "Bearer"));
    }
}
