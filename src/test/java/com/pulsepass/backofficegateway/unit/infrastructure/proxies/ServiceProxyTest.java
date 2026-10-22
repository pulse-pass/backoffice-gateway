package com.pulsepass.backofficegateway.unit.infrastructure.proxies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceRequestException;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

class ServiceProxyTest {

    private StubHttpServer stub;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubHttpServer();
        request = new MockHttpServletRequest("POST", "/legal_persons");
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    void forward_created_returnsSameStatusBodyAndContentType() {
        String path = "/onboarding/legal_persons";
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);
        stub.on("POST", path, StubHttpServer.Response.of(201, "{\"person_key\":\"abc\"}"));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        var response = proxy.forward(HttpMethod.POST, path, request, body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"person_key\":\"abc\"}");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void forward_badRequest_returnsSameStatusAndBody() {
        String path = "/onboarding/legal_persons";
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);
        stub.on("POST", path, StubHttpServer.Response.of(400, "{\"code\":\"GEN00400\"}"));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        var response = proxy.forward(HttpMethod.POST, path, request, body);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"code\":\"GEN00400\"}");
    }

    @Test
    void forward_conflict_returnsSameStatusAndBody() {
        String path = "/onboarding/legal_persons";
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);
        stub.on("POST", path, StubHttpServer.Response.of(409, "{\"code\":\"ONB00001\"}"));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        var response = proxy.forward(HttpMethod.POST, path, request, body);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"code\":\"ONB00001\"}");
    }

    @Test
    void forward_serverError_returnsSameStatusAndBody() {
        String path = "/onboarding/legal_persons";
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);
        stub.on("POST", path, StubHttpServer.Response.of(500, "{\"code\":\"GEN00500\"}"));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        var response = proxy.forward(HttpMethod.POST, path, request, body);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"code\":\"GEN00500\"}");
    }

    @Test
    void forward_emptyResponse_returnsSameStatusWithoutContentType() {
        String path = "/onboarding/legal_persons";
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);
        stub.on("POST", path, StubHttpServer.Response.empty(204));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        var response = proxy.forward(HttpMethod.POST, path, request, body);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().getContentType()).isNull();
        assertThat(response.getBody()).isNull();
    }

    @Test
    void forward_requestWithBody_sendsRawBodyToTarget() {
        String path = "/onboarding/legal_persons";
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);
        stub.on("POST", path, StubHttpServer.Response.of(201, "{}"));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        proxy.forward(HttpMethod.POST, path, request, body);

        assertThat(stub.received()).hasSize(1);
        assertThat(stub.received().get(0).method()).isEqualTo("POST");
        assertThat(stub.received().get(0).path()).isEqualTo(path);
        assertThat(stub.received().get(0).body()).isEqualTo(new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void forward_requestWithoutBody_sendsEmptyBodyToTarget() {
        String path = "/onboarding/legal_persons";
        stub.on("POST", path, StubHttpServer.Response.of(400, "{}"));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        var response = proxy.forward(HttpMethod.POST, path, request, null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(stub.received().get(0).body()).isEmpty();
    }

    @Test
    void forward_contentTypeAndAcceptLanguage_sendsBothToTarget() {
        String path = "/onboarding/legal_persons";
        stub.on("POST", path, StubHttpServer.Response.of(201, "{}"));
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Accept-Language", "pt-BR");
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);

        proxy.forward(HttpMethod.POST, path, request, body);

        assertThat(stub.received().get(0).headers())
                .containsEntry("content-type", "application/json")
                .containsEntry("accept-language", "pt-BR");
    }

    @Test
    void forward_internalTokenAndAuthorization_doesNotSendThemToTarget() {
        String path = "/onboarding/legal_persons";
        stub.on("POST", path, StubHttpServer.Response.of(201, "{}"));
        request.addHeader("internal-token", "secret");
        request.addHeader("Authorization", "Bearer abc");
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);

        proxy.forward(HttpMethod.POST, path, request, body);

        assertThat(stub.received().get(0).headers())
                .doesNotContainKey("internal-token")
                .doesNotContainKey("authorization");
    }

    @Test
    void forward_queryString_sendsItUntouchedToTarget() {
        String path = "/onboarding/legal_persons";
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);
        stub.on("POST", path, StubHttpServer.Response.of(201, "{}"));
        request.setQueryString("a=1&b=x%20y");
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        proxy.forward(HttpMethod.POST, path, request, body);

        assertThat(stub.received().get(0).query()).isEqualTo("a=1&b=x%20y");
    }

    @Test
    void forward_connectionRefused_throwsServiceRequestException() throws IOException {
        String path = "/onboarding/legal_persons";
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var unreachable = new OnboardingServiceProxy(
                "http://127.0.0.1:" + closedPort, RestClient.builder());
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> unreachable.forward(HttpMethod.POST, path, request, body))
                .isInstanceOf(ServiceRequestException.class);
    }

    @Test
    void forward_readTimeout_throwsServiceRequestException() {
        String path = "/onboarding/legal_persons";
        stub.on("POST", path, StubHttpServer.Response.of(201, "{}"));
        stub.delay("POST", path, 1500);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofMillis(300));
        var slow = new OnboardingServiceProxy(stub.url(), RestClient.builder().requestFactory(factory));
        byte[] body = "{\"document_number\":\"11222333000181\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> slow.forward(HttpMethod.POST, path, request, body))
                .isInstanceOf(ServiceRequestException.class);
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
    }

    @Test
    void forward_organizerJwt_sendsSelectedUserAndAgentKeys() {
        String path = "/onboarding/auth/logout";
        stub.on("POST", path, StubHttpServer.Response.empty(204));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());
        Jwt jwt = jwt(Map.of("person_key", "person-1", "legal_person_key", "legal-1",
                "realm_access", Map.of("roles", List.of("organizer"))));

        var response = proxy.forward(HttpMethod.POST, path, request, "{}".getBytes(), jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(stub.received().get(0).headers())
                .containsEntry("selected-user-key", "person-1")
                .containsEntry("selected-agent-key", "legal-1");
    }

    @Test
    void forward_adminJwtWithoutLegalPersonKey_sendsOnlySelectedUserKey() {
        String path = "/onboarding/auth/logout";
        stub.on("POST", path, StubHttpServer.Response.empty(204));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());
        Jwt jwt = jwt(Map.of("person_key", "person-1", "realm_access", Map.of("roles", List.of("admin"))));

        proxy.forward(HttpMethod.POST, path, request, "{}".getBytes(), jwt);

        assertThat(stub.received().get(0).headers())
                .containsEntry("selected-user-key", "person-1")
                .doesNotContainKey("selected-agent-key");
    }

    @Test
    void forward_clientSendsSelectedHeaders_overridesThemWithJwtClaims() {
        String path = "/onboarding/auth/logout";
        stub.on("POST", path, StubHttpServer.Response.empty(204));
        request.addHeader("selected-user-key", "forged-user");
        request.addHeader("selected-agent-key", "forged-agent");
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());
        Jwt jwt = jwt(Map.of("person_key", "person-1", "legal_person_key", "legal-1",
                "realm_access", Map.of("roles", List.of("organizer"))));

        proxy.forward(HttpMethod.POST, path, request, "{}".getBytes(), jwt);

        assertThat(stub.received().get(0).headers())
                .containsEntry("selected-user-key", "person-1")
                .containsEntry("selected-agent-key", "legal-1");
    }

    @Test
    void forward_clientSendsSelectedHeadersWithoutJwt_doesNotSendThem() {
        String path = "/onboarding/auth/login";
        stub.on("POST", path, StubHttpServer.Response.of(200, "{}"));
        request.addHeader("selected-user-key", "forged-user");
        request.addHeader("selected-agent-key", "forged-agent");
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        proxy.forward(HttpMethod.POST, path, request, "{}".getBytes());

        assertThat(stub.received().get(0).headers())
                .doesNotContainKey("selected-user-key")
                .doesNotContainKey("selected-agent-key");
    }

    @Test
    void forward_jwtWithAuthorizationAndInternalToken_neverSendsThem() {
        String path = "/onboarding/auth/logout";
        stub.on("POST", path, StubHttpServer.Response.empty(204));
        request.addHeader("Authorization", "Bearer abc");
        request.addHeader("internal-token", "secret");
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());
        Jwt jwt = jwt(Map.of("person_key", "person-1", "realm_access", Map.of("roles", List.of("admin"))));

        proxy.forward(HttpMethod.POST, path, request, "{}".getBytes(), jwt);

        assertThat(stub.received().get(0).headers())
                .doesNotContainKey("authorization")
                .doesNotContainKey("internal-token");
    }

    @Test
    void forward_jwtAndConnectionRefused_throwsServiceRequestException() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var unreachable = new OnboardingServiceProxy("http://127.0.0.1:" + closedPort, RestClient.builder());
        Jwt jwt = jwt(Map.of("person_key", "person-1", "realm_access", Map.of("roles", List.of("admin"))));

        assertThatThrownBy(() -> unreachable.forward(
                HttpMethod.POST, "/onboarding/auth/logout", request, "{}".getBytes(), jwt))
                .isInstanceOf(ServiceRequestException.class);
    }

    @Test
    void forward_jwtWithRoles_sendsSelectedUserRolesJoinedByComma() {
        String path = "/onboarding/auth/me";
        stub.on("GET", path, StubHttpServer.Response.of(200, "{}"));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());
        Jwt jwt = jwt(Map.of("person_key", "person-1", "realm_access", Map.of("roles", List.of("organizer", "admin"))));

        proxy.forward(HttpMethod.GET, path, request, null, jwt);

        assertThat(stub.received().get(0).headers()).containsEntry("selected-user-roles", "organizer,admin");
    }

    @Test
    void forward_clientSendsSelectedUserRoles_overridesItWithJwtRoles() {
        String path = "/onboarding/auth/me";
        stub.on("GET", path, StubHttpServer.Response.of(200, "{}"));
        request.addHeader("selected-user-roles", "admin");
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());
        Jwt jwt = jwt(Map.of("person_key", "person-1", "realm_access", Map.of("roles", List.of("buyer"))));

        proxy.forward(HttpMethod.GET, path, request, null, jwt);

        assertThat(stub.received().get(0).headers()).containsEntry("selected-user-roles", "buyer");
    }

    @Test
    void forward_noJwt_doesNotSendSelectedUserRoles() {
        String path = "/onboarding/auth/login";
        stub.on("POST", path, StubHttpServer.Response.of(200, "{}"));
        request.addHeader("selected-user-roles", "admin");
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        proxy.forward(HttpMethod.POST, path, request, "{}".getBytes());

        assertThat(stub.received().get(0).headers()).doesNotContainKey("selected-user-roles");
    }

    @Test
    void forwardJson_clientContentType_isReplacedByApplicationJson() {
        String path = "/onboarding/auth/refresh";
        stub.on("POST", path, StubHttpServer.Response.of(200, "{\"access_token\":\"a\"}"));
        request.addHeader("Content-Type", "text/plain");
        request.addHeader("Accept-Language", "pt-BR");
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        var response = proxy.forwardJson(HttpMethod.POST, path, request, "{\"refresh_token\":\"r\"}".getBytes());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(stub.received().get(0).headers())
                .containsEntry("content-type", "application/json")
                .containsEntry("accept-language", "pt-BR")
                .doesNotContainKey("authorization");
        assertThat(stub.received().get(0).body()).isEqualTo("{\"refresh_token\":\"r\"}");
    }

    @Test
    void forwardJson_clientWithoutContentType_sendsApplicationJson() {
        String path = "/onboarding/auth/refresh";
        stub.on("POST", path, StubHttpServer.Response.empty(204));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        var response = proxy.forwardJson(HttpMethod.POST, path, request, "{}".getBytes());

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getBody()).isNull();
        assertThat(stub.received().get(0).headers()).containsEntry("content-type", "application/json");
    }

    @Test
    void forwardJson_targetUnauthorized_returnsSameStatusAndBody() {
        String path = "/onboarding/auth/refresh";
        stub.on("POST", path, StubHttpServer.Response.of(401, "{\"code\":\"GEN00401\"}"));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());

        var response = proxy.forwardJson(HttpMethod.POST, path, request, "{}".getBytes());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"code\":\"GEN00401\"}");
    }

    @Test
    void forwardJson_connectionRefused_throwsServiceRequestException() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var unreachable = new OnboardingServiceProxy("http://127.0.0.1:" + closedPort, RestClient.builder());

        assertThatThrownBy(() -> unreachable.forwardJson(
                HttpMethod.POST, "/onboarding/auth/refresh", request, "{}".getBytes()))
                .isInstanceOf(ServiceRequestException.class);
    }

    @Test
    void forwardJson_jwt_sendsSelectedHeadersAndApplicationJson() {
        String path = "/onboarding/auth/logout";
        stub.on("POST", path, StubHttpServer.Response.empty(204));
        request.addHeader("Content-Type", "text/plain");
        request.addHeader("selected-user-key", "forged-user");
        request.addHeader("Authorization", "Bearer abc");
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());
        Jwt jwt = jwt(Map.of(
                "person_key", "person-1",
                "legal_person_key", "legal-1",
                "realm_access", Map.of("roles", List.of("organizer"))));

        var response = proxy.forwardJson(HttpMethod.POST, path, request, "{}".getBytes(), jwt);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(stub.received().get(0).headers())
                .containsEntry("content-type", "application/json")
                .containsEntry("selected-user-key", "person-1")
                .containsEntry("selected-agent-key", "legal-1")
                .containsEntry("selected-user-roles", "organizer")
                .doesNotContainKey("authorization");
    }

    @Test
    void forwardJson_jwtWithoutLegalPersonKey_doesNotSendSelectedAgentKey() {
        String path = "/onboarding/auth/logout";
        stub.on("POST", path, StubHttpServer.Response.empty(204));
        var proxy = new OnboardingServiceProxy(stub.url(), RestClient.builder());
        Jwt jwt = jwt(Map.of("person_key", "person-1", "realm_access", Map.of("roles", List.of("admin"))));

        proxy.forwardJson(HttpMethod.POST, path, request, "{}".getBytes(), jwt);

        assertThat(stub.received().get(0).headers()).doesNotContainKey("selected-agent-key");
    }

    @Test
    void forwardJson_jwtAndConnectionRefused_throwsServiceRequestException() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var unreachable = new OnboardingServiceProxy("http://127.0.0.1:" + closedPort, RestClient.builder());
        Jwt jwt = jwt(Map.of("person_key", "person-1", "realm_access", Map.of("roles", List.of("admin"))));

        assertThatThrownBy(() -> unreachable.forwardJson(
                HttpMethod.POST, "/onboarding/auth/logout", request, "{}".getBytes(), jwt))
                .isInstanceOf(ServiceRequestException.class);
    }
}
