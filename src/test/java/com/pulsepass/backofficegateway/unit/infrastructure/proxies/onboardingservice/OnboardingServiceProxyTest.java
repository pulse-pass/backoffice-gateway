package com.pulsepass.backofficegateway.unit.infrastructure.proxies.onboardingservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import com.pulsepass.backofficegateway.unit.infrastructure.proxies.StubHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

class OnboardingServiceProxyTest {

    @Test
    void forward_configuredUrl_callsOnboardingService() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/legal_persons", StubHttpServer.Response.of(201, "{}"));
            var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

            var response = proxy.forward(
                    HttpMethod.POST,
                    "/onboarding/legal_persons",
                    new MockHttpServletRequest("POST", "/legal_persons"),
                    "{}".getBytes()
            );

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(stub.received()).hasSize(1);
        }
    }
}
