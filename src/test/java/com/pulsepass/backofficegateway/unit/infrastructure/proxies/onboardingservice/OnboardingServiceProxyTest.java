package com.pulsepass.backofficegateway.unit.infrastructure.proxies.onboardingservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceRequestException;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import com.pulsepass.backofficegateway.unit.infrastructure.proxies.StubHttpServer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class OnboardingServiceProxyTest {

    private static final String SESSION_JSON = "{\"access_token\":\"access-1\",\"expires_in\":300,"
            + "\"refresh_token\":\"refresh-1\",\"refresh_expires_in\":1800,\"token_type\":\"Bearer\"}";

    private static final String REFRESH_BODY = "{\"refresh_token\":\"refresh-secret\"}";

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");

    private OnboardingServiceProxy proxy(String url) {
        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        return new OnboardingServiceProxy(url, RestClient.builder(), jsonMapper);
    }

    private Jwt jwt() {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"),
                Map.of("sub", "user", "person_key", "p1", "realm_access", Map.of("roles", List.of("buyer"))));
    }

    @Test
    void forward_configuredUrl_callsOnboardingService() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/legal_persons", StubHttpServer.Response.of(201, "{}"));
            var proxy = this.proxy(stub.url());

            var response = proxy.forward(
                    HttpMethod.POST,
                    "/onboarding/legal_persons",
                    new MockHttpServletRequest("POST", "/legal_persons"),
                    null,
                    "{}".getBytes(),
                    null
            );

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(stub.received()).hasSize(1);
        }
    }

    @Test
    void createSession_targetOk_returns204WithBothCookiesAndNoBody() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/auth/login", StubHttpServer.Response.of(200, SESSION_JSON));

            ResponseEntity<byte[]> response = this.proxy(stub.url())
                    .createSession(this.request, "{\"a\":1}".getBytes());

            assertThat(response.getStatusCode().value()).isEqualTo(204);
            assertThat(response.getBody()).isNull();
            assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2)
                    .anySatisfy(cookie -> assertThat(cookie)
                            .startsWith("pulsepass_access_token=access-1;")
                            .contains("Max-Age=300", "Path=/;", "Secure", "HttpOnly", "SameSite=Strict"))
                    .anySatisfy(cookie -> assertThat(cookie)
                            .startsWith("pulsepass_refresh_token=refresh-1;")
                            .contains("Max-Age=1800", "Path=/auth", "Secure", "HttpOnly", "SameSite=Strict"));
        }
    }

    @Test
    void createSession_clientBody_sendsItRawToLoginPathWithoutJwtHeaders() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/auth/login", StubHttpServer.Response.of(200, SESSION_JSON));

            this.proxy(stub.url()).createSession(this.request, "{\"a\":1}".getBytes());

            assertThat(stub.received()).hasSize(1).allSatisfy(received -> {
                assertThat(received.method()).isEqualTo("POST");
                assertThat(received.path()).isEqualTo("/onboarding/auth/login");
                assertThat(received.body()).isEqualTo("{\"a\":1}");
                assertThat(received.headers()).doesNotContainKeys("selected-user-key", "selected-user-roles");
            });
        }
    }

    @Test
    void createSession_targetUnauthorized_returnsRawResponseWithoutCookies() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/auth/login", StubHttpServer.Response.of(401, "{\"code\":\"GEN00401\"}"));

            ResponseEntity<byte[]> response = this.proxy(stub.url()).createSession(this.request, "{}".getBytes());

            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(response.getBody()).isEqualTo("{\"code\":\"GEN00401\"}".getBytes());
            assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        }
    }

    @Test
    void createSession_targetDown_throwsServiceRequestException() throws Exception {
        String url;
        try (StubHttpServer stub = new StubHttpServer()) {
            url = stub.url();
        }

        assertThatThrownBy(() -> this.proxy(url).createSession(this.request, "{}".getBytes()))
                .isInstanceOf(ServiceRequestException.class);
    }

    @Test
    void refreshSession_targetOk_sendsRefreshBodyAsJsonAndRewritesCookies() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/auth/refresh", StubHttpServer.Response.of(200, SESSION_JSON));

            ResponseEntity<byte[]> response = this.proxy(stub.url()).refreshSession(this.request, "refresh-secret");

            assertThat(stub.received()).hasSize(1).allSatisfy(received -> {
                assertThat(received.method()).isEqualTo("POST");
                assertThat(received.path()).isEqualTo("/onboarding/auth/refresh");
                assertThat(received.body()).isEqualTo(REFRESH_BODY);
                assertThat(received.headers()).containsEntry("content-type", "application/json");
                assertThat(received.headers()).doesNotContainKeys("selected-user-key", "selected-user-roles");
            });
            assertThat(response.getStatusCode().value()).isEqualTo(204);
            assertThat(response.getBody()).isNull();
            assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2)
                    .anySatisfy(cookie -> assertThat(cookie).startsWith("pulsepass_access_token=access-1;"))
                    .anySatisfy(cookie -> assertThat(cookie).startsWith("pulsepass_refresh_token=refresh-1;"));
        }
    }

    @Test
    void refreshSession_clientLanguageAndQuery_forwardsThemToTarget() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/auth/refresh", StubHttpServer.Response.of(200, SESSION_JSON));
            MockHttpServletRequest clientRequest = new MockHttpServletRequest("POST", "/auth/refresh");
            clientRequest.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR");
            clientRequest.setQueryString("source=web");

            this.proxy(stub.url()).refreshSession(clientRequest, "refresh-secret");

            assertThat(stub.received()).hasSize(1).allSatisfy(received -> {
                assertThat(received.headers()).containsEntry("accept-language", "pt-BR");
                assertThat(received.query()).isEqualTo("source=web");
            });
        }
    }

    @Test
    void refreshSession_targetUnauthorized_clearsCookiesAndReturnsRawBody() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/auth/refresh", StubHttpServer.Response.of(401, "{\"code\":\"GEN00401\"}"));

            ResponseEntity<byte[]> response = this.proxy(stub.url()).refreshSession(this.request, "refresh-secret");

            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(response.getBody()).isEqualTo("{\"code\":\"GEN00401\"}".getBytes());
            assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2)
                    .anySatisfy(cookie -> assertThat(cookie)
                            .startsWith("pulsepass_access_token=;")
                            .contains("Max-Age=0", "Path=/;", "Secure", "HttpOnly", "SameSite=Strict"))
                    .anySatisfy(cookie -> assertThat(cookie)
                            .startsWith("pulsepass_refresh_token=;")
                            .contains("Max-Age=0", "Path=/auth", "Secure", "HttpOnly", "SameSite=Strict"));
        }
    }

    @Test
    void refreshSession_targetServerError_returnsRawResponseWithoutCookies() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/auth/refresh", StubHttpServer.Response.of(500, "{\"code\":\"GEN00500\"}"));

            ResponseEntity<byte[]> response = this.proxy(stub.url()).refreshSession(this.request, "refresh-secret");

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody()).isEqualTo("{\"code\":\"GEN00500\"}".getBytes());
            assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        }
    }

    @Test
    void refreshSession_targetDown_throwsServiceRequestException() throws Exception {
        String url;
        try (StubHttpServer stub = new StubHttpServer()) {
            url = stub.url();
        }

        assertThatThrownBy(() -> this.proxy(url).refreshSession(this.request, "refresh-secret"))
                .isInstanceOf(ServiceRequestException.class);
    }

    @Test
    void deleteSession_targetNoContent_sendsRefreshBodyWithJwtHeadersAndClearsCookies() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/auth/logout", StubHttpServer.Response.empty(204));

            ResponseEntity<byte[]> response = this.proxy(stub.url())
                    .deleteSession(this.request, "refresh-secret", this.jwt());

            assertThat(stub.received()).hasSize(1).allSatisfy(received -> {
                assertThat(received.method()).isEqualTo("POST");
                assertThat(received.path()).isEqualTo("/onboarding/auth/logout");
                assertThat(received.body()).isEqualTo(REFRESH_BODY);
                assertThat(received.headers()).containsEntry("content-type", "application/json");
                assertThat(received.headers()).containsEntry("selected-user-key", "p1");
                assertThat(received.headers()).containsEntry("selected-user-roles", "buyer");
            });
            assertThat(response.getStatusCode().value()).isEqualTo(204);
            assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2)
                    .anySatisfy(cookie -> assertThat(cookie)
                            .startsWith("pulsepass_access_token=;")
                            .contains("Max-Age=0", "Path=/;", "Secure", "HttpOnly", "SameSite=Strict"))
                    .anySatisfy(cookie -> assertThat(cookie)
                            .startsWith("pulsepass_refresh_token=;")
                            .contains("Max-Age=0", "Path=/auth", "Secure", "HttpOnly", "SameSite=Strict"));
        }
    }

    @Test
    void deleteSession_targetUnauthorized_returnsRawBodyAndClearsCookies() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.on("POST", "/onboarding/auth/logout", StubHttpServer.Response.of(401, "{\"code\":\"GEN00401\"}"));

            ResponseEntity<byte[]> response = this.proxy(stub.url())
                    .deleteSession(this.request, "refresh-secret", this.jwt());

            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getBody()).isEqualTo("{\"code\":\"GEN00401\"}".getBytes());
            assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2)
                    .anySatisfy(cookie -> assertThat(cookie)
                            .startsWith("pulsepass_access_token=;")
                            .contains("Max-Age=0", "Path=/;", "Secure", "HttpOnly", "SameSite=Strict"))
                    .anySatisfy(cookie -> assertThat(cookie)
                            .startsWith("pulsepass_refresh_token=;")
                            .contains("Max-Age=0", "Path=/auth", "Secure", "HttpOnly", "SameSite=Strict"));
        }
    }

    @Test
    void deleteSession_targetDown_throwsServiceRequestException() throws Exception {
        String url;
        try (StubHttpServer stub = new StubHttpServer()) {
            url = stub.url();
        }

        assertThatThrownBy(() -> this.proxy(url).deleteSession(this.request, "refresh-secret", this.jwt()))
                .isInstanceOf(ServiceRequestException.class);
    }
}
