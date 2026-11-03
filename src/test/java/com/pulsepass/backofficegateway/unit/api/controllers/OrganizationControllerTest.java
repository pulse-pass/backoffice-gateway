package com.pulsepass.backofficegateway.unit.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulsepass.backofficegateway.api.configurations.SecurityConfiguration;
import com.pulsepass.backofficegateway.api.controllers.OrganizationController;
import com.pulsepass.backofficegateway.api.exceptions.GlobalExceptionHandler;
import com.pulsepass.backofficegateway.api.exceptions.SecurityErrorHandler;
import com.pulsepass.backofficegateway.common.TokenTools;
import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceRequestException;
import com.pulsepass.backofficegateway.infrastructure.proxies.organizationservice.OrganizationServiceProxy;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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

@WebMvcTest(OrganizationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class, SecurityErrorHandler.class})
@TestPropertySource(
        locations = "classpath:security-test.properties",
        properties = {
                "spring.messages.basename=messages",
                "spring.messages.fallback-to-system-locale=false"
        }
)
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationServiceProxy organizationServiceProxy;

    private Cookie accessCookie(String role) {
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(600),
                Instant.now().plusSeconds(300),
                Map.of("sub", "user", "person_key", "p1", "realm_access", Map.of("roles", List.of(role))));
        return new Cookie("pulsepass_access_token", token);
    }

    @Test
    void listOrganizations_organizer_forwardsGetWithJwtAndReturnsTargetResponse() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":[]}".getBytes()));

        mockMvc.perform(get("/organizations").param("page", "2").cookie(this.accessCookie("organizer")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(content().string("{\"data\":[]}"));

        verify(organizationServiceProxy).forward(
                eq(HttpMethod.GET), eq("/organization/organizations"), any(), isNull(), isNull(), any(Jwt.class));
    }

    @Test
    void listOrganizations_noToken_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(get("/organizations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listOrganizations_adminRole_returns403WithoutCallingProxy() throws Exception {
        mockMvc.perform(get("/organizations").cookie(this.accessCookie("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listOrganizations_proxyFails_returns500Gen00500() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(get("/organizations").cookie(this.accessCookie("organizer")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"));
    }

    @Test
    void listInternalOrganizations_admin_forwardsGetWithJwtAndReturnsTargetResponse() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":[]}".getBytes()));

        mockMvc.perform(get("/internal/organizations").param("page", "2").cookie(this.accessCookie("admin")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(content().string("{\"data\":[]}"));

        verify(organizationServiceProxy).forward(
                eq(HttpMethod.GET), eq("/organization/internal/organizations"),
                any(), isNull(), isNull(), any(Jwt.class));
    }

    @Test
    void listInternalOrganizations_noToken_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(get("/internal/organizations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listInternalOrganizations_organizerRole_returns403WithoutCallingProxy() throws Exception {
        mockMvc.perform(get("/internal/organizations").cookie(this.accessCookie("organizer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listInternalOrganizations_proxyFails_returns500Gen00500() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(get("/internal/organizations").cookie(this.accessCookie("admin")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"));
    }
}
