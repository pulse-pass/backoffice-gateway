package com.pulsepass.backofficegateway.unit.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Test
    void listInternalOrganizationRejectionReasons_admin_forwardsGetWithJwtAndReturnsTargetResponse() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":[]}".getBytes()));

        mockMvc.perform(get("/internal/organization_rejection_reasons").param("page", "2")
                        .cookie(this.accessCookie("admin")))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"data\":[]}"));

        verify(organizationServiceProxy).forward(
                eq(HttpMethod.GET), eq("/organization/internal/organization_rejection_reasons"),
                any(), isNull(), isNull(), any(Jwt.class));
    }

    @Test
    void listInternalOrganizationRejectionReasons_organizerRole_returns403WithoutCallingProxy() throws Exception {
        mockMvc.perform(get("/internal/organization_rejection_reasons").cookie(this.accessCookie("organizer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateInternalOrganization_admin_forwardsPatchWithBodyJsonAndJwtAndReturnsTargetResponse() throws Exception {
        byte[] requestBody = "{\"organization_status\":\"APPROVED\"}".getBytes();
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"organization_key\":\"k\"}".getBytes()));

        mockMvc.perform(patch("/internal/organizations/k").with(csrf())
                        .cookie(this.accessCookie("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"organization_key\":\"k\"}"));

        verify(organizationServiceProxy).forward(
                eq(HttpMethod.PATCH), eq("/organization/internal/organizations/k"),
                any(), eq(MediaType.APPLICATION_JSON), eq(requestBody), any(Jwt.class));
    }

    @Test
    void updateInternalOrganization_keyWithEncodedHash_forwardsEncodedPath() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        mockMvc.perform(patch("/internal/organizations/{key}", "x#y").with(csrf())
                        .cookie(this.accessCookie("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(organizationServiceProxy).forward(
                eq(HttpMethod.PATCH), eq("/organization/internal/organizations/x%23y"),
                any(), eq(MediaType.APPLICATION_JSON), any(), any(Jwt.class));
    }

    @Test
    void updateInternalOrganization_targetUnprocessable_returnsSameStatusAndBody() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(422)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"ORG00003\"}".getBytes()));

        mockMvc.perform(patch("/internal/organizations/k").with(csrf())
                        .cookie(this.accessCookie("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORG00003"));
    }

    @Test
    void updateInternalOrganization_organizerRole_returns403WithoutCallingProxy() throws Exception {
        mockMvc.perform(patch("/internal/organizations/k").with(csrf())
                        .cookie(this.accessCookie("organizer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateInternalOrganization_noToken_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(patch("/internal/organizations/k").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateInternalOrganization_withoutCsrf_returns403WithoutCallingProxy() throws Exception {
        mockMvc.perform(patch("/internal/organizations/k")
                        .cookie(this.accessCookie("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateInternalOrganization_proxyFails_returns500Gen00500() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(patch("/internal/organizations/k").with(csrf())
                        .cookie(this.accessCookie("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"));
    }

    @Test
    void deleteInternalOrganization_admin_forwardsDeleteWithJwtAndReturns204() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.noContent().build());

        mockMvc.perform(delete("/internal/organizations/k").with(csrf()).cookie(this.accessCookie("admin")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(organizationServiceProxy).forward(
                eq(HttpMethod.DELETE), eq("/organization/internal/organizations/k"),
                any(), isNull(), isNull(), any(Jwt.class));
    }

    @Test
    void deleteInternalOrganization_keyWithEncodedHash_forwardsEncodedPath() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.noContent().build());

        mockMvc.perform(delete("/internal/organizations/{key}", "x#y").with(csrf())
                        .cookie(this.accessCookie("admin")))
                .andExpect(status().isNoContent());

        verify(organizationServiceProxy).forward(
                eq(HttpMethod.DELETE), eq("/organization/internal/organizations/x%23y"),
                any(), isNull(), isNull(), any(Jwt.class));
    }

    @Test
    void deleteInternalOrganization_targetNotFound_returnsSameStatusAndBody() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(404)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"GEN00404\"}".getBytes()));

        mockMvc.perform(delete("/internal/organizations/k").with(csrf()).cookie(this.accessCookie("admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GEN00404"));
    }

    @Test
    void deleteInternalOrganization_organizerRole_returns403WithoutCallingProxy() throws Exception {
        mockMvc.perform(delete("/internal/organizations/k").with(csrf()).cookie(this.accessCookie("organizer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteInternalOrganization_noToken_returns401WithoutCallingProxy() throws Exception {
        mockMvc.perform(delete("/internal/organizations/k").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GEN00401"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteInternalOrganization_withoutCsrf_returns403WithoutCallingProxy() throws Exception {
        mockMvc.perform(delete("/internal/organizations/k").cookie(this.accessCookie("admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GEN00403"));

        verify(organizationServiceProxy, never()).forward(any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteInternalOrganization_proxyFails_returns500Gen00500() throws Exception {
        when(organizationServiceProxy.forward(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(delete("/internal/organizations/k").with(csrf()).cookie(this.accessCookie("admin")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"));
    }
}
