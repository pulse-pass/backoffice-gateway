package com.pulsepass.backofficegateway.api.controllers;

import com.pulsepass.backofficegateway.infrastructure.proxies.organizationservice.OrganizationServiceProxy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrganizationController {

    private final OrganizationServiceProxy organizationServiceProxy;

    public OrganizationController(OrganizationServiceProxy organizationServiceProxy) {
        this.organizationServiceProxy = organizationServiceProxy;
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
}
