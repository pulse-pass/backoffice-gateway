package com.pulsepass.backofficegateway.unit.infrastructure.proxies.organizationservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.infrastructure.proxies.organizationservice.OrganizationServiceProxy;
import com.pulsepass.backofficegateway.unit.infrastructure.proxies.StubHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

class OrganizationServiceProxyTest {

    @Test
    void forward_configuredUrl_callsOrganizationService() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("GET", "/organization/organizations", StubHttpServer.Response.of(200, "{\"data\":[]}"));
            var proxy = new OrganizationServiceProxy(stub.url(), RestClient.builder());

            var response = proxy.forward(
                    HttpMethod.GET,
                    "/organization/organizations",
                    new MockHttpServletRequest("GET", "/organizations"),
                    null,
                    null,
                    null
            );

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo("{\"data\":[]}".getBytes());
            assertThat(stub.received()).hasSize(1);
        }
    }
}
