package com.pulsepass.backofficegateway.unit.api.configurations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulsepass.backofficegateway.api.configurations.CorsConfiguration;
import com.pulsepass.backofficegateway.api.configurations.SecurityConfiguration;
import com.pulsepass.backofficegateway.api.controllers.OnboardingController;
import com.pulsepass.backofficegateway.api.exceptions.GlobalExceptionHandler;
import com.pulsepass.backofficegateway.api.exceptions.SecurityErrorHandler;
import com.pulsepass.backofficegateway.api.sessions.SessionCookies;
import com.pulsepass.backofficegateway.common.TokenTools;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
        byte[] session = ("{\"access_token\":\"a\",\"expires_in\":300,\"refresh_token\":\"r\","
                + "\"refresh_expires_in\":1800,\"token_type\":\"Bearer\"}").getBytes();
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200).body(session));
        when(onboardingServiceProxy.forward(any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200).body("{}".getBytes()));
        when(onboardingServiceProxy.forwardJson(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200).body(session));
        when(onboardingServiceProxy.forwardJson(any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(204).build());
    }

    private String token(String issuer, Instant expiresAt, String role) {
        return TokenTools.sign(issuer, Instant.now().minusSeconds(600), expiresAt, Map.of(
                "sub", "user",
                "realm_access", Map.of("roles", List.of(role))
        ));
    }

    private RequestPostProcessor csrfToken() {
        return request -> {
            List<Cookie> cookies = new ArrayList<>();
            if (request.getCookies() != null) {
                cookies.addAll(List.of(request.getCookies()));
            }
            cookies.add(new Cookie("XSRF-TOKEN", "csrf-value"));
            request.setCookies(cookies.toArray(new Cookie[0]));
            request.addHeader("X-XSRF-TOKEN", "csrf-value");
            return request;
        };
    }

    private Cookie accessCookie(String token) {
        return new Cookie(SessionCookies.ACCESS_TOKEN, token);
    }

    private Cookie refreshCookie() {
        return new Cookie(SessionCookies.REFRESH_TOKEN, "refresh-secret");
    }

    private String tampered(String token) {
        return token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";
    }

    @Test
    void filterChain_publicLegalPersonsWithoutToken_passes() throws Exception {
        mockMvc.perform(post("/legal_persons").with(this.csrfToken()).content("{}")).andExpect(status().isOk());
    }

    @Test
    void filterChain_publicLoginWithoutToken_passes() throws Exception {
        mockMvc.perform(post("/auth/login").with(this.csrfToken()).content("{}")).andExpect(status().isNoContent());
    }

    @Test
    void filterChain_publicRefreshWithoutToken_passes() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                .with(this.csrfToken())
                .cookie(this.refreshCookie())
                .content("{}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_publicLegalPersonsWithExpiredToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().minusSeconds(300), "organizer");

        mockMvc.perform(post("/legal_persons").with(this.csrfToken()).cookie(this.accessCookie(token)).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void filterChain_publicLegalPersonsWithTamperedToken_passes() throws Exception {
        String token = this.tampered(token(ISSUER, Instant.now().plusSeconds(300), "organizer"));

        mockMvc.perform(post("/legal_persons").with(this.csrfToken()).cookie(this.accessCookie(token)).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void filterChain_publicLoginWithExpiredToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().minusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/login").with(this.csrfToken()).cookie(this.accessCookie(token)).content("{}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_publicLoginWithTamperedToken_passes() throws Exception {
        String token = this.tampered(token(ISSUER, Instant.now().plusSeconds(300), "organizer"));

        mockMvc.perform(post("/auth/login").with(this.csrfToken()).cookie(this.accessCookie(token)).content("{}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_publicRefreshWithExpiredToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().minusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/refresh")
                .with(this.csrfToken())
                .cookie(this.refreshCookie())
                .cookie(this.accessCookie(token))
                .content("{}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_publicRefreshWithTamperedToken_passes() throws Exception {
        String token = this.tampered(token(ISSUER, Instant.now().plusSeconds(300), "organizer"));

        mockMvc.perform(post("/auth/refresh")
                .with(this.csrfToken())
                .cookie(this.refreshCookie())
                .cookie(this.accessCookie(token))
                .content("{}"))
                .andExpect(status().isNoContent());
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
        mockMvc.perform(post("/auth/logout").with(this.csrfToken()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("GEN00401"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."))
                .andExpect(jsonPath("$.extra_fields").isEmpty());
    }

    @Test
    void filterChain_protectedWithoutTokenInPortuguese_returnsTranslatedMessage() throws Exception {
        mockMvc.perform(post("/auth/logout").with(this.csrfToken()).header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("É preciso estar autenticado para acessar este recurso."));
    }

    @Test
    void filterChain_malformedToken_returns401Gen00401() throws Exception {
        mockMvc.perform(post("/auth/logout").with(this.csrfToken()).cookie(this.accessCookie("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_expiredToken_returns401Gen00401() throws Exception {
        String token = token(ISSUER, Instant.now().minusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/logout").with(this.csrfToken()).cookie(this.accessCookie(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_wrongIssuer_returns401Gen00401() throws Exception {
        String token = token("http://other.example/realms/pulse-pass", Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/logout").with(this.csrfToken()).cookie(this.accessCookie(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_signatureFromOtherKey_returns401Gen00401() throws Exception {
        String token = TokenTools.sign(TokenTools.otherPrivateKey(), ISSUER, Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300), Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        mockMvc.perform(post("/auth/logout").with(this.csrfToken()).cookie(this.accessCookie(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_buyerTokenOnRoleProtectedRoute_returns403Gen00403() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "buyer");

        mockMvc.perform(get("/unknown")
                        .cookie(this.accessCookie(token))
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

        mockMvc.perform(get("/unknown").cookie(this.accessCookie(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));
    }

    @Test
    void filterChain_tokenWithEmptyRolesOnRoleProtectedRoute_returns403Gen00403() throws Exception {
        String token = TokenTools.sign(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("sub", "user", "realm_access", Map.of("roles", List.of())));

        mockMvc.perform(get("/unknown").cookie(this.accessCookie(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));
    }

    @Test
    void filterChain_logoutWithBuyerToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "buyer");

        mockMvc.perform(post("/auth/logout")
                .with(this.csrfToken())
                .cookie(this.refreshCookie())
                .cookie(this.accessCookie(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_logoutWithTokenWithoutRoles_passes() throws Exception {
        String token = TokenTools.sign(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("sub", "user"));

        mockMvc.perform(post("/auth/logout")
                .with(this.csrfToken())
                .cookie(this.refreshCookie())
                .cookie(this.accessCookie(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_organizerToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(get("/unknown").cookie(this.accessCookie(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void filterChain_adminToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "admin");

        mockMvc.perform(get("/unknown").cookie(this.accessCookie(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void filterChain_tokenWithBothRoles_passes() throws Exception {
        String token = TokenTools.sign(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("sub", "user", "realm_access", Map.of("roles", List.of("organizer", "admin"))));

        mockMvc.perform(get("/unknown").cookie(this.accessCookie(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void filterChain_logoutWithOrganizerToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/logout")
                .with(this.csrfToken())
                .cookie(this.refreshCookie())
                .cookie(this.accessCookie(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_logoutWithAdminToken_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "admin");

        mockMvc.perform(post("/auth/logout")
                .with(this.csrfToken())
                .cookie(this.refreshCookie())
                .cookie(this.accessCookie(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void filterChain_hmacTokenSignedWithPublicKeyBytes_returns401Gen00401() throws Exception {
        String token = TokenTools.signHmac(Base64.getDecoder().decode(this.publicKey), ISSUER,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        mockMvc.perform(post("/auth/logout").with(this.csrfToken()).cookie(this.accessCookie(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_hmacTokenSignedWithPublicKeyText_returns401Gen00401() throws Exception {
        String token = TokenTools.signHmac(this.publicKey.getBytes(), ISSUER,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        mockMvc.perform(post("/auth/logout").with(this.csrfToken()).cookie(this.accessCookie(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_algNoneToken_returns401Gen00401() throws Exception {
        String token = TokenTools.unsigned(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        mockMvc.perform(post("/auth/logout").with(this.csrfToken()).cookie(this.accessCookie(token)))
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
    void filterChain_preflightWithCsrfHeader_allowsCredentials() throws Exception {
        mockMvc.perform(options("/auth/logout")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-xsrf-token, content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Headers", "x-xsrf-token, content-type"));
    }

    @Test
    void filterChain_authorizationHeaderWithValidToken_returns401Gen00401() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/logout").with(this.csrfToken()).cookie(this.refreshCookie())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_postWithoutCsrfToken_returns403Gen00403() throws Exception {
        mockMvc.perform(post("/legal_persons").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));
    }

    @Test
    void filterChain_loginWithoutCsrfToken_returns403Gen00403() throws Exception {
        mockMvc.perform(post("/auth/login").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));
    }

    @Test
    void filterChain_logoutWithValidCookieButWithoutCsrfToken_returns403Gen00403() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(post("/auth/logout").cookie(this.accessCookie(token), this.refreshCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));
    }

    @Test
    void filterChain_publicGetWithoutCookies_returnsReadableXsrfCookie() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void filterChain_unauthenticatedGet_returns401WithXsrfCookie() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void filterChain_postWithCsrfCookieAndHeader_passes() throws Exception {
        mockMvc.perform(post("/legal_persons")
                        .cookie(new Cookie("XSRF-TOKEN", "csrf-value"))
                        .header("X-XSRF-TOKEN", "csrf-value")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void filterChain_meWithoutCookie_returns401Gen00401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));
    }

    @Test
    void filterChain_meWithBuyerCookie_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "buyer");

        mockMvc.perform(get("/auth/me").cookie(this.accessCookie(token))).andExpect(status().isOk());
    }

    @Test
    void filterChain_meWithOrganizerCookie_passes() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(get("/auth/me").cookie(this.accessCookie(token))).andExpect(status().isOk());
    }

    @Test
    void filterChain_buyerCookieOnRoleProtectedRoute_returns403Gen00403() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "buyer");

        mockMvc.perform(get("/unknown").cookie(this.accessCookie(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));
    }

    @Test
    void filterChain_unknownRouteWithOrganizerToken_returns404Gen00404() throws Exception {
        String token = token(ISSUER, Instant.now().plusSeconds(300), "organizer");

        mockMvc.perform(get("/unknown").cookie(this.accessCookie(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GEN00404"));
    }
}
