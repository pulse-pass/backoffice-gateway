package com.pulsepass.backofficegateway.api.controllers;

import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnboardingController {

    private final OnboardingServiceProxy onboardingServiceProxy;

    public OnboardingController(OnboardingServiceProxy onboardingServiceProxy) {
        this.onboardingServiceProxy = onboardingServiceProxy;
    }

    @PostMapping("/legal_persons")
    public ResponseEntity<byte[]> createLegalPerson(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        return this.onboardingServiceProxy.forward(HttpMethod.POST, "/onboarding/legal_persons", request, body);
    }
}
