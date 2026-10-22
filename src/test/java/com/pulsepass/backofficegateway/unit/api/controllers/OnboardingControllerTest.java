package com.pulsepass.backofficegateway.unit.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulsepass.backofficegateway.api.configurations.SecurityConfiguration;
import com.pulsepass.backofficegateway.api.controllers.OnboardingController;
import com.pulsepass.backofficegateway.api.exceptions.GlobalExceptionHandler;
import com.pulsepass.backofficegateway.api.exceptions.SecurityErrorHandler;
import com.pulsepass.backofficegateway.common.TokenTools;
import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceRequestException;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(OnboardingController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class, SecurityErrorHandler.class})
@TestPropertySource(
        locations = "classpath:security-test.properties",
        properties = {
                "spring.messages.basename=messages",
                "spring.messages.fallback-to-system-locale=false"
        }
)
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnboardingServiceProxy onboardingServiceProxy;

    private Cookie accessCookie(String role) {
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(600),
                Instant.now().plusSeconds(300),
                Map.of("sub", "user", "person_key", "p1", "realm_access", Map.of("roles", List.of(role))));
        return new Cookie("pulsepass_access_token", token);
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

    @Test
    void createLegalPerson_targetResponds_returnsSameStatusBodyAndContentType() throws Exception {
        when(onboardingServiceProxy.forward(
                eq(HttpMethod.POST), eq("/onboarding/legal_persons"), any(HttpServletRequest.class), any()))
                .thenReturn(ResponseEntity.status(409)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"ONB00001\"}".getBytes()));

        mockMvc.perform(post("/legal_persons")
                .with(this.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":1}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(content().string("{\"code\":\"ONB00001\"}"));
    }

    @Test
    void createLegalPerson_body_forwardsRawBytesToOnboardingPath() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(201).body(new byte[0]));

        mockMvc.perform(post("/legal_persons")
                .with(this.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":1}"))
                .andExpect(status().isCreated());

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST),
                eq("/onboarding/legal_persons"),
                any(HttpServletRequest.class),
                eq("{\"a\":1}".getBytes()));
    }

    @Test
    void createLegalPerson_noBody_forwardsNullBody() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(400).body(new byte[0]));

        mockMvc.perform(post("/legal_persons").with(this.csrfToken())).andExpect(status().isBadRequest());

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST), eq("/onboarding/legal_persons"), any(HttpServletRequest.class), isNull());
    }

    @Test
    void createLegalPerson_proxyFails_returns500Gen00500() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(post("/legal_persons")
                .with(this.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"))
                .andExpect(jsonPath("$.extra_fields").isEmpty());
    }

    @Test
    void createLegalPerson_getMethod_returns405Gen00405() throws Exception {
        mockMvc.perform(get("/legal_persons").cookie(this.accessCookie("organizer")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("GEN00405"));
    }

    @Test
    void createLegalPerson_unknownRoute_returns404Gen00404() throws Exception {
        mockMvc.perform(get("/unknown").cookie(this.accessCookie("organizer")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GEN00404"));
    }

    private static final String SESSION_JSON = "{\"access_token\":\"access-1\",\"expires_in\":300,"
            + "\"refresh_token\":\"refresh-1\",\"refresh_expires_in\":1800,\"token_type\":\"Bearer\"}";

    private static final String REFRESH_BODY = "{\"refresh_token\":\"refresh-secret\"}";

    @Test
    void createSession_targetOk_returns204WithBothCookiesAndNoBody() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(SESSION_JSON.getBytes()));

        mockMvc.perform(post("/auth/login")
                .with(this.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":1}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(cookie().value("pulsepass_access_token", "access-1"))
                .andExpect(cookie().maxAge("pulsepass_access_token", 300))
                .andExpect(cookie().path("pulsepass_access_token", "/"))
                .andExpect(cookie().value("pulsepass_refresh_token", "refresh-1"))
                .andExpect(cookie().maxAge("pulsepass_refresh_token", 1800))
                .andExpect(cookie().path("pulsepass_refresh_token", "/auth"));

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST),
                eq("/onboarding/auth/login"),
                any(HttpServletRequest.class),
                eq("{\"a\":1}".getBytes()));
    }

    @Test
    void createSession_targetUnauthorized_returnsRawResponseWithoutCookies() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(401)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"GEN00401\"}".getBytes()));

        mockMvc.perform(post("/auth/login")
                .with(this.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().string("{\"code\":\"GEN00401\"}"));
    }

    @Test
    void createSession_noCsrf_returns403WithoutCallingProxy() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));

        verify(onboardingServiceProxy, never()).forward(any(), any(), any(), any());
    }

    @Test
    void createSession_proxyFails_returns500Gen00500() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(post("/auth/login")
                .with(this.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"));
    }

    @Test
    void refreshSession_cookieAndTargetOk_sendsRefreshBodyAndRewritesCookies() throws Exception {
        when(onboardingServiceProxy.forwardJson(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(SESSION_JSON.getBytes()));

        mockMvc.perform(post("/auth/refresh").with(this.csrfToken())
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("ignored"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(cookie().value("pulsepass_access_token", "access-1"))
                .andExpect(cookie().value("pulsepass_refresh_token", "refresh-1"));

        verify(onboardingServiceProxy).forwardJson(
                eq(HttpMethod.POST),
                eq("/onboarding/auth/refresh"),
                any(HttpServletRequest.class),
                eq(REFRESH_BODY.getBytes()));
    }

    @Test
    void refreshSession_noRefreshCookie_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(post("/auth/refresh").with(this.csrfToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(onboardingServiceProxy, never()).forwardJson(any(), any(), any(), any());
    }

    @Test
    void refreshSession_targetUnauthorized_clearsCookiesAndReturnsRawResponse() throws Exception {
        when(onboardingServiceProxy.forwardJson(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(401)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"GEN00401\"}".getBytes()));

        mockMvc.perform(post("/auth/refresh").with(this.csrfToken())
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(content().string("{\"code\":\"GEN00401\"}"))
                .andExpect(cookie().maxAge("pulsepass_access_token", 0))
                .andExpect(cookie().maxAge("pulsepass_refresh_token", 0));
    }

    @Test
    void refreshSession_targetServerError_returnsRawResponseWithoutCookies() throws Exception {
        when(onboardingServiceProxy.forwardJson(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(500)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"GEN00500\"}".getBytes()));

        mockMvc.perform(post("/auth/refresh").with(this.csrfToken())
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().string("{\"code\":\"GEN00500\"}"));
    }

    @Test
    void deleteSession_authenticated_sendsRefreshBodyAndJwtAndClearsCookies() throws Exception {
        when(onboardingServiceProxy.forwardJson(any(), any(), any(), any(), any(Jwt.class)))
                .thenReturn(ResponseEntity.status(204).build());

        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(this.accessCookie("organizer"))
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("pulsepass_access_token", 0))
                .andExpect(cookie().value("pulsepass_access_token", ""))
                .andExpect(cookie().maxAge("pulsepass_refresh_token", 0))
                .andExpect(cookie().value("pulsepass_refresh_token", ""));

        verify(onboardingServiceProxy).forwardJson(
                eq(HttpMethod.POST),
                eq("/onboarding/auth/logout"),
                any(HttpServletRequest.class),
                eq(REFRESH_BODY.getBytes()),
                any(Jwt.class));
    }

    @Test
    void deleteSession_targetUnauthorized_returnsRawResponseAndClearsCookies() throws Exception {
        when(onboardingServiceProxy.forwardJson(any(), any(), any(), any(), any(Jwt.class)))
                .thenReturn(ResponseEntity.status(401)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"GEN00401\"}".getBytes()));

        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(this.accessCookie("buyer"))
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("{\"code\":\"GEN00401\"}"))
                .andExpect(cookie().maxAge("pulsepass_access_token", 0))
                .andExpect(cookie().maxAge("pulsepass_refresh_token", 0));
    }

    @Test
    void deleteSession_noRefreshCookie_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(this.accessCookie("organizer")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(onboardingServiceProxy, never()).forwardJson(any(), any(), any(), any(), any());
    }

    @Test
    void deleteSession_noToken_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(onboardingServiceProxy, never()).forwardJson(any(), any(), any(), any(), any());
    }

    @Test
    void deleteSession_buyerRole_isAllowed() throws Exception {
        when(onboardingServiceProxy.forwardJson(any(), any(), any(), any(), any(Jwt.class)))
                .thenReturn(ResponseEntity.status(204).build());

        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(this.accessCookie("buyer"))
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSession_proxyFails_returns500Gen00500() throws Exception {
        when(onboardingServiceProxy.forwardJson(any(), any(), any(), any(), any(Jwt.class)))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(this.accessCookie("organizer"))
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"));
    }

    @Test
    void readMe_authenticated_forwardsGetWithJwtAndReturnsTargetResponse() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any(), any(Jwt.class)))
                .thenReturn(ResponseEntity.status(200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"person_key\":\"p1\"}".getBytes()));

        mockMvc.perform(get("/auth/me").cookie(this.accessCookie("buyer")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(content().string("{\"person_key\":\"p1\"}"));

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.GET),
                eq("/onboarding/auth/me"),
                any(HttpServletRequest.class),
                isNull(),
                any(Jwt.class));
    }

    @Test
    void readMe_noToken_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(onboardingServiceProxy, never()).forward(any(), any(), any(), any(), any());
    }
}
