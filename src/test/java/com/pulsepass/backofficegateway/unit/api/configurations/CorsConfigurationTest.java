package com.pulsepass.backofficegateway.unit.api.configurations;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulsepass.backofficegateway.api.configurations.CorsConfiguration;
import com.pulsepass.backofficegateway.api.configurations.SecurityConfiguration;
import com.pulsepass.backofficegateway.api.controllers.OnboardingController;
import com.pulsepass.backofficegateway.api.exceptions.SecurityErrorHandler;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OnboardingController.class)
@Import({CorsConfiguration.class, SecurityConfiguration.class, SecurityErrorHandler.class})
@TestPropertySource(
        locations = "classpath:security-test.properties",
        properties = {
                "app.cors.allowed-origin=http://localhost:3000",
                "spring.messages.basename=messages",
                "spring.messages.fallback-to-system-locale=false"
        }
)
class CorsConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnboardingServiceProxy onboardingServiceProxy;

    @Test
    void addCorsMappings_allowedOrigin_answersPreflightWithAllowOrigin() throws Exception {
        mockMvc.perform(options("/legal_persons")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type, accept-language"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Headers", "content-type, accept-language"));
    }

    @Test
    void addCorsMappings_allowedOrigin_allowsCredentialsAndCsrfHeader() throws Exception {
        mockMvc.perform(options("/legal_persons")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Headers", "x-xsrf-token"));
    }

    @Test
    void addCorsMappings_headerNotListed_rejectsPreflight() throws Exception {
        mockMvc.perform(options("/legal_persons")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addCorsMappings_otherOrigin_rejectsPreflight() throws Exception {
        mockMvc.perform(options("/legal_persons")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
