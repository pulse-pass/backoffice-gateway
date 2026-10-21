package com.pulsepass.backofficegateway.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

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

class TestPostRefreshSession extends BaseIntegrationTest {

    private static final String POST_AUTH_REFRESH = "/auth/refresh";

    @Test
    void refreshSession_targetOk_returnsSameStatusBodyAndContentType() {
        MockTools.clearMocker();
        String accessToken = UUID.randomUUID().toString();
        MockTools.addExpectation("onboarding-service/post-auth_refresh", Map.of(),
                Map.of("payload", Map.of("access_token", accessToken)));

        Response response = given()
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "access_token", accessToken,
                "expires_in", 300,
                "refresh_token", "refresh-token-from-target",
                "refresh_expires_in", 1800,
                "token_type", "Bearer"
        ));
        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/auth/refresh");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("body").toString()).contains("refresh-secret");
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshSession_headers_neverForwardsAuthorizationInternalTokenNorSelectedKeys() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh");

        given()
                    .header("Accept-Language", "pt-BR")
                    .header("internal-token", "must-not-be-forwarded")
                    .header("selected-user-key", "forged-user")
                    .header("selected-agent-key", "forged-agent")
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/auth/refresh");
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("Accept-Language").get(0)).isEqualTo("pt-BR");
        assertThat(headers.get("Content-Type").get(0)).startsWith("application/json");
        assertThat(headers).doesNotContainKey("internal-token");
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("selected-user-key");
        assertThat(headers).doesNotContainKey("selected-agent-key");
    }

    @Test
    void refreshSession_targetUnauthorized_returnsSameStatusAndBody() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh_unauthorized");

        Response response = given()
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-wrong"))
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Unauthorized",
                "message", "rejected by target",
                "code", "GEN00401",
                "extra_fields", List.of()
        ));
    }

    @Test
    void refreshSession_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh_dropped_connection");

        Response response = given()
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00500");
        assertThat(response.jsonPath().getString("message"))
                .isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
        assertThat(response.asString()).doesNotContain("onboarding", "mockserver", "Connection", "Exception");
    }

    @Test
    void refreshSession_getMethod_returns401WithoutToken() {
        MockTools.clearMocker();

        Response response = given().when().get(POST_AUTH_REFRESH).then().extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void refreshSession_expiredBearerToken_isIgnoredAndForwarded() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(300), Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/refresh")).hasSize(1);
    }

    @Test
    void refreshSession_tamperedBearerToken_isIgnoredAndForwarded() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh");
        String signed = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300), Map.of("realm_access", Map.of("roles", List.of("organizer"))));
        String token = signed.substring(0, signed.lastIndexOf('.') + 1) + "invalidsignature";

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/refresh")).hasSize(1);
    }

    @Test
    void preflight_allowedOrigin_returnsAllowOriginHeaderWithoutToken() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type, accept-language")
                .when()
                    .options(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
    }
}
