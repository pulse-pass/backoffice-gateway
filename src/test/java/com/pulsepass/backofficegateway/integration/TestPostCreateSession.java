package com.pulsepass.backofficegateway.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.common.CsrfTools;
import com.pulsepass.backofficegateway.common.MockTools;
import com.pulsepass.backofficegateway.common.TokenTools;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestPostCreateSession extends BaseIntegrationTest {

    private static final String POST_AUTH_LOGIN = "/auth/login";

    @Test
    void createSession_targetOk_returns204WithSessionCookiesAndNoBody() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String accessToken = UUID.randomUUID().toString();
        String refreshToken = UUID.randomUUID().toString();
        MockTools.addExpectation("onboarding-service/post-auth_login", Map.of(), Map.of("payload", Map.of(
                "access_token", accessToken,
                "expires_in", 120,
                "refresh_token", refreshToken,
                "refresh_expires_in", 900
        )));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "52998224725", "password", "secret"))
                .when()
                    .post(POST_AUTH_LOGIN)
                .then()
                    .extract().response();

        List<String> cookies = response.headers().getValues("Set-Cookie");
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.asString()).isEmpty();
        assertThat(cookies).hasSize(2);
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_access_token=" + accessToken + ";")
                .contains("Max-Age=120", "Path=/;", "HttpOnly", "Secure", "SameSite=Strict"));
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_refresh_token=" + refreshToken + ";")
                .contains("Max-Age=900", "Path=/auth", "HttpOnly", "Secure", "SameSite=Strict"));
        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/auth/login");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("body").toString()).contains("52998224725", "secret");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createSession_headers_neverForwardsAuthorizationInternalTokenNorSelectedKeys() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_login");

        given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "pt-BR")
                    .header("internal-token", "must-not-be-forwarded")
                    .header("selected-user-key", "forged-user")
                    .header("selected-agent-key", "forged-agent")
                    .header("selected-user-roles", "admin")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "52998224725", "password", "secret"))
                .when()
                    .post(POST_AUTH_LOGIN)
                .then()
                    .extract().response();

        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/auth/login");
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("Accept-Language").get(0)).isEqualTo("pt-BR");
        assertThat(headers.get("Content-Type").get(0)).startsWith("application/json");
        assertThat(headers).doesNotContainKey("internal-token");
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Cookie");
        assertThat(headers).doesNotContainKey("selected-user-key");
        assertThat(headers).doesNotContainKey("selected-agent-key");
        assertThat(headers).doesNotContainKey("selected-user-roles");
    }

    @Test
    void createSession_targetUnauthorized_returnsSameStatusAndBodyWithoutCookies() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_login_unauthorized");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "52998224725", "password", "wrong"))
                .when()
                    .post(POST_AUTH_LOGIN)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.headers().getValues("Set-Cookie")).isEmpty();
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Unauthorized",
                "message", "rejected by target",
                "code", "GEN00401",
                "extra_fields", List.of()
        ));
    }

    @Test
    void createSession_targetDropsConnection_returns500Gen00500WithoutDetails() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_login_dropped_connection");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "52998224725", "password", "secret"))
                .when()
                    .post(POST_AUTH_LOGIN)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00500");
        assertThat(response.jsonPath().getString("message"))
                .isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
        assertThat(response.headers().getValues("Set-Cookie")).isEmpty();
        assertThat(response.asString()).doesNotContain("onboarding", "mockserver", "Connection", "Exception");
    }

    @Test
    void createSession_withoutCsrfToken_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_login");

        Response response = given()
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "52998224725", "password", "secret"))
                .when()
                    .post(POST_AUTH_LOGIN)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(response.jsonPath().getString("message"))
                .isEqualTo("You do not have permission to access this resource.");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/login")).isEmpty();
    }

    @Test
    void createSession_mismatchedCsrfHeader_returns403Gen00403() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_login");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", UUID.randomUUID().toString())
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "52998224725", "password", "secret"))
                .when()
                    .post(POST_AUTH_LOGIN)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
    }

    @Test
    void createSession_expiredAccessCookie_isIgnoredAndForwarded() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_login");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "52998224725", "password", "secret"))
                .when()
                    .post(POST_AUTH_LOGIN)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/login")).hasSize(1);
    }

    @Test
    void createSession_tamperedAccessCookie_isIgnoredAndForwarded() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_login");
        String signed = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));
        String token = signed.substring(0, signed.lastIndexOf('.') + 1) + "invalidsignature";

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "52998224725", "password", "secret"))
                .when()
                    .post(POST_AUTH_LOGIN)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/login")).hasSize(1);
    }

    @Test
    void preflight_allowedOrigin_allowsCredentialsAndCsrfHeaderWithoutToken() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type, accept-language, x-xsrf-token")
                .when()
                    .options(POST_AUTH_LOGIN)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
    }
}
