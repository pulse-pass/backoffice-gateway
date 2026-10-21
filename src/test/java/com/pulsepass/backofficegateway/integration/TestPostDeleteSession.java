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

class TestPostDeleteSession extends BaseIntegrationTest {

    private static final String POST_AUTH_LOGOUT = "/auth/logout";

    @Test
    void deleteSession_noToken_returns401Gen00401() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");

        Response response = given()
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.contentType()).startsWith("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Unauthorized",
                "message", "Authentication is required to access this resource.",
                "code", "GEN00401",
                "extra_fields", List.of()
        ));
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/logout")).isEmpty();
    }

    @Test
    void deleteSession_noTokenInPortuguese_returnsTranslatedMessage() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Accept-Language", "pt-BR")
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("message"))
                .isEqualTo("É preciso estar autenticado para acessar este recurso.");
    }

    @Test
    void deleteSession_tamperedToken_returns401Gen00401() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        String signed = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("organizer"))));
        String token = signed.substring(0, signed.lastIndexOf('.') + 1) + "invalidsignature";

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/logout")).isEmpty();
    }

    @Test
    void deleteSession_tokenSignedByOtherKey_returns401Gen00401() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                TokenTools.otherPrivateKey(), "http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void deleteSession_wrongIssuer_returns401Gen00401() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign("http://other.example/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void deleteSession_expiredToken_returns401Gen00401() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(120),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void deleteSession_getMethodWithBuyerToken_returns403Gen00403() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("buyer"))));

        Response response = given()
                    .header("Accept-Language", "en")
                    .header("Authorization", "Bearer " + token)
                .when()
                    .get(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Forbidden",
                "message", "You do not have permission to access this resource.",
                "code", "GEN00403",
                "extra_fields", List.of()
        ));
    }

    @Test
    void deleteSession_getMethodWithBuyerTokenInPortuguese_returnsTranslatedMessage() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("buyer"))));

        Response response = given()
                    .header("Accept-Language", "pt-BR")
                    .header("Authorization", "Bearer " + token)
                .when()
                    .get(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("message"))
                .isEqualTo("Você não tem permissão para acessar este recurso.");
    }

    @Test
    void deleteSession_getMethodWithOrganizerToken_returns405Gen00405() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                .when()
                    .get(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00405");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteSession_tokenWithoutRoles_forwardsWithSelectedUserKeyOnly() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("person_key", personKey));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .header("selected-agent-key", "forged-agent")
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/auth/logout");
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-user-key")).containsExactly(personKey);
        assertThat(headers).doesNotContainKey("selected-agent-key");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteSession_buyerToken_forwardsWithSelectedUserKeyOnly() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("buyer"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .header("selected-agent-key", "forged-agent")
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/auth/logout");
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-user-key")).containsExactly(personKey);
        assertThat(headers).doesNotContainKey("selected-agent-key");
        assertThat(headers).doesNotContainKey("Authorization");
    }

    @Test
    void deleteSession_hmacTokenSignedWithArbitrarySecret_returns401Gen00401() {
        MockTools.clearMocker();
        String token = TokenTools.signHmac(UUID.randomUUID().toString().repeat(2).getBytes(),
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/logout")).isEmpty();
    }

    @Test
    void deleteSession_algNoneToken_returns401Gen00401() {
        MockTools.clearMocker();
        String token = TokenTools.unsigned(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/logout")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteSession_organizerToken_forwardsWithSelectedUserAndAgentKeys() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");
        String personKey = UUID.randomUUID().toString();
        String legalPersonKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300), Map.of("person_key", personKey, "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Accept-Language", "pt-BR")
                    .header("Authorization", "Bearer " + token)
                    .header("internal-token", "must-not-be-forwarded")
                    .header("selected-user-key", "forged-user")
                    .header("selected-agent-key", "forged-agent")
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.asString()).isEmpty();
        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/auth/logout");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("body").toString()).contains("refresh-secret");
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-user-key")).containsExactly(personKey);
        assertThat(headers.get("selected-agent-key")).containsExactly(legalPersonKey);
        assertThat(headers.get("Accept-Language").get(0)).isEqualTo("pt-BR");
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("internal-token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteSession_adminToken_forwardsOnlySelectedUserKey() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .header("selected-agent-key", "forged-agent")
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/auth/logout");
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-user-key")).containsExactly(personKey);
        assertThat(headers).doesNotContainKey("selected-agent-key");
        assertThat(headers).doesNotContainKey("Authorization");
    }

    @Test
    void deleteSession_targetUnauthorized_returnsSameStatusAndBody() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout_unauthorized");
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
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
    void deleteSession_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout_dropped_connection");
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign(
                "http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Accept-Language", "en")
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("refresh_token", "refresh-secret"))
                .when()
                    .post(POST_AUTH_LOGOUT)
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
    void preflight_allowedOrigin_returnsAllowOriginHeaderWithoutToken() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "authorization, content-type")
                .when()
                    .options(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
    }

    @Test
    void preflight_authorizationHeader_isAccepted() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "authorization")
                .when()
                    .options(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
        assertThat(response.header("Access-Control-Allow-Headers")).containsIgnoringCase("authorization");
    }

    @Test
    void preflight_notAllowedOrigin_hasNoAllowOriginHeader() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://evil.example")
                    .header("Access-Control-Request-Method", "POST")
                .when()
                    .options(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.header("Access-Control-Allow-Origin")).isNull();
    }
}
