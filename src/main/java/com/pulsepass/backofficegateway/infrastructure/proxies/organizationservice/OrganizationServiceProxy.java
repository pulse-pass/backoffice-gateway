package com.pulsepass.backofficegateway.infrastructure.proxies.organizationservice;

import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrganizationServiceProxy extends ServiceProxy {

    public OrganizationServiceProxy(
            @Value("${app.organization-service.url}") String url,
            RestClient.Builder restClientBuilder
    ) {
        super("organization-service", url, restClientBuilder);
    }
}
