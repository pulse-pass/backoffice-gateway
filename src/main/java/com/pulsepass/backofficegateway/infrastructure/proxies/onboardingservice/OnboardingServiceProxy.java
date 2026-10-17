package com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice;

import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OnboardingServiceProxy extends ServiceProxy {

    public OnboardingServiceProxy(
            @Value("${app.onboarding-service.url}") String url,
            RestClient.Builder restClientBuilder
    ) {
        super("onboarding-service", url, restClientBuilder);
    }
}
