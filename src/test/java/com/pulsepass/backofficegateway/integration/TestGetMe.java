package com.pulsepass.backofficegateway.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.common.MockTools;
import com.pulsepass.backofficegateway.common.TokenTools;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestGetMe extends BaseIntegrationTest {

    private static final String GET_AUTH_ME = "/auth/me";

    @Test
    @SuppressWarnings("unchecked")
    void readMe_organizerCookie_forwardsSelectedHeadersAndReturnsTargetBody() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation("onboarding-service/get-auth_me", Map.of(),
                Map.of("payload", Map.of("person_key", personKey)));
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                    .header("selected-user-key", "forged-user")
                    .header("selected-agent-key", "forged-agent")
                    .header("selected-user-roles", "admin")
                .when()
                    .get(GET_AUTH_ME)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "person_key", personKey,
                "name", "Maria Souza",
                "roles", List.of("organizer")
        ));
        List<Map<String, Object>> requests = MockTools.retrieveRequests("GET", "/onboarding/auth/me");
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-user-key")).containsExactly(personKey);
        assertThat(headers.get("selected-agent-key")).containsExactly(legalPersonKey);
        assertThat(headers.get("selected-user-roles")).containsExactly("organizer");
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Cookie");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readMe_buyerCookieWithTwoRoles_forwardsRolesJoinedByCommaWithoutAgentKey() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/get-auth_me");
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("buyer", "organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(GET_AUTH_ME)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> requests = MockTools.retrieveRequests("GET", "/onboarding/auth/me");
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-user-roles")).containsExactly("buyer,organizer");
        assertThat(headers).doesNotContainKey("selected-agent-key");
    }

    @Test
    void readMe_noCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/get-auth_me");

        Response response = given()
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_AUTH_ME)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/auth/me")).isEmpty();
    }

    @Test
    void readMe_noCookie_returnsReadableXsrfCookie() {
        Response response = given()
                .when()
                    .get(GET_AUTH_ME)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().getValues("Set-Cookie")).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("XSRF-TOKEN=")
                .contains("Path=/")
                .doesNotContain("HttpOnly"));
    }

    @Test
    void readMe_authorizationHeaderWithValidToken_returns401Gen00401() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/get-auth_me");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .header("Authorization", "Bearer " + token)
                .when()
                    .get(GET_AUTH_ME)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/auth/me")).isEmpty();
    }

    @Test
    void readMe_expiredCookie_returns401Gen00401() {
        MockTools.clearMocker();
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(GET_AUTH_ME)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void readMe_cookieSignedByOtherKey_returns401Gen00401() {
        MockTools.clearMocker();
        String token = TokenTools.sign(TokenTools.otherPrivateKey(), "http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(GET_AUTH_ME)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void readMe_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/get-auth_me_dropped_connection");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_AUTH_ME)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00500");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
        assertThat(response.asString()).doesNotContain("onboarding", "mockserver", "Connection", "Exception");
    }
}
