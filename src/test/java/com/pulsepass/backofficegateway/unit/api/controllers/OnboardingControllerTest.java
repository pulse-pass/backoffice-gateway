package com.pulsepass.backofficegateway.unit.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.util.Arrays;
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
        when(onboardingServiceProxy.forward(any(), any(), any(), any(), any(), any()))
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
        when(onboardingServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(201).body(new byte[0]));

        mockMvc.perform(post("/legal_persons")
                .with(this.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":1}"))
                .andExpect(status().isCreated());

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST),
                eq("/onboarding/legal_persons"),
                any(),
                isNull(),
                argThat(body -> Arrays.equals(body, "{\"a\":1}".getBytes())),
                isNull());
    }

    @Test
    void createLegalPerson_noBody_forwardsNullBody() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(400).body(new byte[0]));

        mockMvc.perform(post("/legal_persons").with(this.csrfToken())).andExpect(status().isBadRequest());

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST), eq("/onboarding/legal_persons"), any(), isNull(), isNull(), isNull());
    }

    @Test
    void createLegalPerson_proxyFails_returns500Gen00500() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any(), any(), any()))
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

    @Test
    void createSession_body_delegatesToProxyAndReturnsItsResponse() throws Exception {
        when(onboardingServiceProxy.createSession(any(HttpServletRequest.class), any()))
                .thenReturn(ResponseEntity.noContent().build());

        mockMvc.perform(post("/auth/login")
                .with(this.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":1}"))
                .andExpect(status().isNoContent());

        verify(onboardingServiceProxy).createSession(any(HttpServletRequest.class), eq("{\"a\":1}".getBytes()));
    }

    @Test
    void createSession_noCsrf_returns403WithoutCallingProxy() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));

        verify(onboardingServiceProxy, never()).createSession(any(), any());
    }

    @Test
    void createSession_proxyFails_returns500Gen00500() throws Exception {
        when(onboardingServiceProxy.createSession(any(), any()))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(post("/auth/login")
                .with(this.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"));
    }

    @Test
    void refreshSession_refreshCookie_delegatesToProxyWithCookieValue() throws Exception {
        when(onboardingServiceProxy.refreshSession(any(HttpServletRequest.class), any()))
                .thenReturn(ResponseEntity.noContent().build());

        mockMvc.perform(post("/auth/refresh").with(this.csrfToken())
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isNoContent());

        verify(onboardingServiceProxy).refreshSession(any(HttpServletRequest.class), eq("refresh-secret"));
    }

    @Test
    void refreshSession_noRefreshCookie_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(post("/auth/refresh").with(this.csrfToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(onboardingServiceProxy, never()).refreshSession(any(), any());
    }

    @Test
    void deleteSession_authenticated_delegatesToProxyWithCookieAndJwt() throws Exception {
        when(onboardingServiceProxy.deleteSession(any(HttpServletRequest.class), any(), any(Jwt.class)))
                .thenReturn(ResponseEntity.noContent().build());

        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(this.accessCookie("organizer"))
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isNoContent());

        verify(onboardingServiceProxy).deleteSession(
                any(HttpServletRequest.class), eq("refresh-secret"), any(Jwt.class));
    }

    @Test
    void deleteSession_buyerRole_isAllowed() throws Exception {
        when(onboardingServiceProxy.deleteSession(any(), any(), any(Jwt.class)))
                .thenReturn(ResponseEntity.noContent().build());

        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(this.accessCookie("buyer"))
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSession_noRefreshCookie_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(this.accessCookie("organizer")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(onboardingServiceProxy, never()).deleteSession(any(), any(), any());
    }

    @Test
    void deleteSession_noToken_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(onboardingServiceProxy, never()).deleteSession(any(), any(), any());
    }

    @Test
    void deleteSession_proxyFails_returns500Gen00500() throws Exception {
        when(onboardingServiceProxy.deleteSession(any(), any(), any(Jwt.class)))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(post("/auth/logout").with(this.csrfToken())
                        .cookie(this.accessCookie("organizer"))
                        .cookie(new Cookie("pulsepass_refresh_token", "refresh-secret")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"));
    }

    @Test
    void readMe_authenticated_forwardsGetWithJwtAndReturnsTargetResponse() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"person_key\":\"p1\"}".getBytes()));

        mockMvc.perform(get("/auth/me").cookie(this.accessCookie("buyer")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(content().string("{\"person_key\":\"p1\"}"));

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.GET), eq("/onboarding/auth/me"), any(), isNull(), isNull(), any(Jwt.class));
    }

    @Test
    void readMe_noToken_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(onboardingServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }
}
