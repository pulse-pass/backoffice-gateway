package com.pulsepass.backofficegateway.unit.api.configurations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulsepass.backofficegateway.api.configurations.CorsConfiguration;
import com.pulsepass.backofficegateway.api.configurations.SecurityConfiguration;
import com.pulsepass.backofficegateway.api.controllers.OnboardingController;
import com.pulsepass.backofficegateway.api.exceptions.GlobalExceptionHandler;
import com.pulsepass.backofficegateway.api.exceptions.SecurityErrorHandler;
import com.pulsepass.backofficegateway.common.TokenTools;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OnboardingController.class)
@Import({SecurityConfiguration.class, SecurityErrorHandler.class, GlobalExceptionHandler.class,
        CorsConfiguration.class})
@TestPropertySource(
        locations = "classpath:security-test.properties",
        properties = {
                "app.cors.allowed-origin=http://localhost:3000",
                "spring.messages.basename=messages",
                "spring.messages.fallback-to-system-locale=false"
        }
)
class SecurityConfigurationTest {

    private static final String ISSUER = "http://pulse-pass-keycloak:8080/realms/pulse-pass";

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.security.jwt.public-key}")
    private String publicKey;

    @MockitoBean
    private OnboardingServiceProxy onboardingServiceProxy;

    @BeforeEach
    void setUp() {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200).body("{}".getBytes()));
        when(onboardingServiceProxy.forward(any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(204).build());
    }

    private String token(String issuer, Instant expiresAt, String role) {
        return TokenTools.sign(issuer, Instant.now().minusSeconds(600), expiresAt, Map.of(
                "sub", "user",
                "realm_access", Map.of("roles", List.of(role))
        ));
    }

    private String tampered(String token) {
        return token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";
    }

    @Test
    void filterChain_publicLegalPersonsWithoutToken_passes() throws Exception {
        mockMvc.perform(post("/legal_persons").content("{}")).andExpect(status().isOk());
    }

    @Test
    void filterChain_publicLoginWithoutToken_passes() throws Exception {
        mockMvc.perform(post("/auth/login").content("{}")).andExpect(status().isOk());
    }

    @Test
    void filterChain_publicRefreshWithoutToken_passes() throws Exception {
        mockMvc.perform(post("/auth/refresh").content("{}")).andExpect(status().isOk());
    }

    @Test
    void filterChain_publicLegalPersonsWithExpiredToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().minusSeconds(300), "organizer");

        mockMvc.perform(post("/legal_persons").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void filterChain_publicLegalPersonsWithTamperedToken_passes() throws Exception {
        String token = this.tampered(token(ISSUER, Instant.now().plusSeconds(300), "organizer"));

        mockMvc.perform(post("/legal_persons").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void filterChain_publicLoginWithExpiredToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().minusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void filterChain_publicLoginWithTamperedToken_passes() throws Exception {
        String token = this.tampered(token(ISSUER, Instant.now().plusSeconds(300), "organizer"));

        mockMvc.perform(post("/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void filterChain_publicRefreshWithExpiredToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().minusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void filterChain_publicRefreshWithTamperedToken_passes() throws Exception {
        String token = this.tampered(token(ISSUER, Instant.now().plusSeconds(300), "organizer"));

        mockMvc.perform(post("/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void filterChain_healthWithoutToken_isNotRejectedBySecurity() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    @Test
    void filterChain_preflightWithoutToken_passes() throws Exception {
        mockMvc.perform(options("/auth/logout")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void filterChain_protectedWithoutToken_returns401Gen00401() throws Exception {
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("GEN00401"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."))
                .andExpect(jsonPath("$.extra_fields").isEmpty());
    }

    @Test
    void filterChain_protectedWithoutTokenInPortuguese_returnsTranslatedMessage() throws Exception {
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("É preciso estar autenticado para acessar este recurso."));
    }

    @Test
    void filterChain_malformedToken_returns401Gen00401() throws Exception {
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_expiredToken_returns401Gen00401() throws Exception {
        String token = token(ISSUER, Instant.now().minusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_wrongIssuer_returns401Gen00401() throws Exception {
        String token = token("http://other.example/realms/pulse-pass", Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_signatureFromOtherKey_returns401Gen00401() throws Exception {
        String token = TokenTools.sign(TokenTools.otherPrivateKey(), ISSUER, Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300), Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_buyerTokenOnRoleProtectedRoute_returns403Gen00403() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "buyer");

        mockMvc.perform(get("/unknown")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("GEN00403"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."));
    }

    @Test
    void filterChain_tokenWithoutRealmAccessOnRoleProtectedRoute_returns403Gen00403() throws Exception {
        String token = TokenTools.sign(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("sub", "user"));

        mockMvc.perform(get("/unknown").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));
    }

    @Test
    void filterChain_tokenWithEmptyRolesOnRoleProtectedRoute_returns403Gen00403() throws Exception {
        String token = TokenTools.sign(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("sub", "user", "realm_access", Map.of("roles", List.of())));

        mockMvc.perform(get("/unknown").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));
    }

    @Test
    void filterChain_logoutWithBuyerToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "buyer");

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_logoutWithTokenWithoutRoles_passes() throws Exception {
        String token = TokenTools.sign(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("sub", "user"));

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_organizerToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(get("/unknown").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void filterChain_adminToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "admin");

        mockMvc.perform(get("/unknown").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void filterChain_tokenWithBothRoles_passes() throws Exception {
        String token = TokenTools.sign(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("sub", "user", "realm_access", Map.of("roles", List.of("organizer", "admin"))));

        mockMvc.perform(get("/unknown").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void filterChain_logoutWithOrganizerToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_logoutWithAdminToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "admin");

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_hmacTokenSignedWithPublicKeyBytes_returns401Gen00401() throws Exception {
        String token = TokenTools.signHmac(Base64.getDecoder().decode(this.publicKey), ISSUER,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_hmacTokenSignedWithPublicKeyText_returns401Gen00401() throws Exception {
        String token = TokenTools.signHmac(this.publicKey.getBytes(), ISSUER,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_algNoneToken_returns401Gen00401() throws Exception {
        String token = TokenTools.unsigned(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_preflightFromNotAllowedOrigin_hasNoAllowOriginHeader() throws Exception {
        mockMvc.perform(options("/auth/logout")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void filterChain_preflightWithAuthorizationHeader_isAccepted() throws Exception {
        mockMvc.perform(options("/auth/logout")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Headers", "authorization"));
    }

    @Test
    void filterChain_unknownRouteWithOrganizerToken_returns404Gen00404() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(get("/unknown").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GEN00404"));
    }
}
