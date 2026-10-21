package com.pulsepass.backofficegateway.unit.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceRequestException;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void createLegalPerson_targetResponds_returnsSameStatusBodyAndContentType() throws Exception {
        when(onboardingServiceProxy.forward(
                eq(HttpMethod.POST), eq("/onboarding/legal_persons"), any(HttpServletRequest.class), any()))
                .thenReturn(ResponseEntity.status(409)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"ONB00001\"}".getBytes()));

        mockMvc.perform(post("/legal_persons").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(content().string("{\"code\":\"ONB00001\"}"));
    }

    @Test
    void createLegalPerson_body_forwardsRawBytesToOnboardingPath() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(201).body(new byte[0]));

        mockMvc.perform(post("/legal_persons").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
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

        mockMvc.perform(post("/legal_persons")).andExpect(status().isBadRequest());

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST), eq("/onboarding/legal_persons"), any(HttpServletRequest.class), isNull());
    }

    @Test
    void createLegalPerson_proxyFails_returns500Gen00500() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(post("/legal_persons").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"))
                .andExpect(jsonPath("$.extra_fields").isEmpty());
    }

    @Test
    void createLegalPerson_getMethod_returns405Gen00405() throws Exception {
        mockMvc.perform(get("/legal_persons").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("GEN00405"));
    }

    @Test
    void createLegalPerson_unknownRoute_returns404Gen00404() throws Exception {
        mockMvc.perform(get("/unknown").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GEN00404"));
    }

    @Test
    void createSession_noToken_forwardsBodyToLoginPath() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"access_token\":\"a\"}".getBytes()));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(content().string("{\"access_token\":\"a\"}"));

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST),
                eq("/onboarding/auth/login"),
                any(HttpServletRequest.class),
                eq("{\"a\":1}".getBytes()));
    }

    @Test
    void createSession_proxyFails_returns500Gen00500() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"));
    }

    @Test
    void refreshSession_noToken_forwardsBodyToRefreshPath() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(401)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"GEN00401\"}".getBytes()));

        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("{\"code\":\"GEN00401\"}"));

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST),
                eq("/onboarding/auth/refresh"),
                any(HttpServletRequest.class),
                eq("{\"a\":1}".getBytes()));
    }

    @Test
    void deleteSession_authenticated_forwardsBodyAndJwtToLogoutPath() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any(), any(Jwt.class)))
                .thenReturn(ResponseEntity.status(204).build());

        mockMvc.perform(post("/auth/logout")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":1}"))
                .andExpect(status().isNoContent());

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST),
                eq("/onboarding/auth/logout"),
                any(HttpServletRequest.class),
                eq("{\"a\":1}".getBytes()),
                any(Jwt.class));
    }

    @Test
    void deleteSession_noToken_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(onboardingServiceProxy, never()).forward(any(), any(), any(), any(), any());
    }

    @Test
    void deleteSession_buyerRole_forwardsToLogoutPath() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any(), any(Jwt.class)))
                .thenReturn(ResponseEntity.status(204).build());

        mockMvc.perform(post("/auth/logout")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST),
                eq("/onboarding/auth/logout"),
                any(HttpServletRequest.class),
                eq("{}".getBytes()),
                any(Jwt.class));
    }
}
