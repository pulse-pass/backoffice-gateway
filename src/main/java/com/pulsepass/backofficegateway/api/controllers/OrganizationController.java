package com.pulsepass.backofficegateway.api.controllers;

import com.pulsepass.backofficegateway.infrastructure.proxies.organizationservice.OrganizationServiceProxy;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
public class OrganizationController {

    private final OrganizationServiceProxy organizationServiceProxy;

    public OrganizationController(OrganizationServiceProxy organizationServiceProxy) {
        this.organizationServiceProxy = organizationServiceProxy;
    }

    @PatchMapping("/internal/organizations/{organizationKey}")
    public ResponseEntity<byte[]> updateInternalOrganization(
            HttpServletRequest request,
            @PathVariable String organizationKey,
            @RequestBody(required = false) byte[] body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return this.organizationServiceProxy.forward(
                HttpMethod.PATCH,
                "/organization/internal/organizations/"
                        + UriUtils.encodePathSegment(organizationKey, StandardCharsets.UTF_8),
                request, MediaType.APPLICATION_JSON, body, jwt);
    }

    @DeleteMapping("/internal/organizations/{organizationKey}")
    public ResponseEntity<byte[]> deleteInternalOrganization(
            HttpServletRequest request,
            @PathVariable String organizationKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return this.organizationServiceProxy.forward(
                HttpMethod.DELETE,
                "/organization/internal/organizations/"
                        + UriUtils.encodePathSegment(organizationKey, StandardCharsets.UTF_8),
                request, null, null, jwt);
    }

    @GetMapping("/organizations")
    public ResponseEntity<byte[]> listOrganizations(HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
        return this.organizationServiceProxy.forward(
                HttpMethod.GET, "/organization/organizations", request, null, null, jwt);
    }

    @GetMapping("/internal/organizations")
    public ResponseEntity<byte[]> listInternalOrganizations(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return this.organizationServiceProxy.forward(
                HttpMethod.GET, "/organization/internal/organizations", request, null, null, jwt);
    }

    @GetMapping("/internal/organization_rejection_reasons")
    public ResponseEntity<byte[]> listInternalOrganizationRejectionReasons(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return this.organizationServiceProxy.forward(
                HttpMethod.GET, "/organization/internal/organization_rejection_reasons", request, null, null, jwt);
    }
}
