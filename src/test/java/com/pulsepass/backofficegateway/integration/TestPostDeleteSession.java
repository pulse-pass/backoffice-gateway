package com.pulsepass.backofficegateway.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.common.CsrfTools;
import com.pulsepass.backofficegateway.common.MockTools;
import com.pulsepass.backofficegateway.common.TokenTools;
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
    @SuppressWarnings("unchecked")
    void deleteSession_organizerCookies_sendsRefreshTokenAndSelectedHeadersAndClearsCookies() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");
        String personKey = UUID.randomUUID().toString();
        String legalPersonKey = UUID.randomUUID().toString();
        String refreshToken = UUID.randomUUID().toString();
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", refreshToken)
                    .header("Accept-Language", "pt-BR")
                    .header("selected-user-key", "forged-user")
                    .header("selected-agent-key", "forged-agent")
                    .header("selected-user-roles", "admin")
                    .contentType("text/plain")
                    .body("ignored by the gateway")
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        List<String> cookies = response.headers().getValues("Set-Cookie");
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.asString()).isEmpty();
        assertThat(cookies).hasSize(2);
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_access_token=;")
                .contains("Max-Age=0", "Path=/;", "HttpOnly", "Secure", "SameSite=Strict"));
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_refresh_token=;")
                .contains("Max-Age=0", "Path=/auth", "HttpOnly", "Secure", "SameSite=Strict"));
        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/auth/logout");
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-user-key")).containsExactly(personKey);
        assertThat(headers.get("selected-agent-key")).containsExactly(legalPersonKey);
        assertThat(headers.get("selected-user-roles")).containsExactly("organizer");
        assertThat(headers.get("Content-Type").get(0)).startsWith("application/json");
        assertThat(headers.get("Accept-Language").get(0)).isEqualTo("pt-BR");
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Cookie");
        assertThat(requests.get(0).get("body").toString()).contains(refreshToken);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteSession_adminCookies_forwardsOnlySelectedUserKeyAndRoles() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");
        String personKey = UUID.randomUUID().toString();
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                    .header("selected-agent-key", "forged-agent")
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
        assertThat(headers.get("selected-user-roles")).containsExactly("admin");
        assertThat(headers).doesNotContainKey("selected-agent-key");
    }

    @Test
    void deleteSession_buyerCookies_isAllowedAndClearsCookies() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("buyer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.headers().getValues("Set-Cookie")).hasSize(2);
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/logout")).hasSize(1);
    }

    @Test
    void deleteSession_noRefreshCookie_returns401Gen00401WithoutCallingTarget() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/logout")).isEmpty();
    }

    @Test
    void deleteSession_noAccessCookie_returns401Gen00401WithoutCallingTarget() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                    .header("Accept-Language", "en")
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(response.jsonPath().getString("message"))
                .isEqualTo("Authentication is required to access this resource.");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/logout")).isEmpty();
    }

    @Test
    void deleteSession_authorizationHeaderWithValidToken_returns401Gen00401() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                    .header("Authorization", "Bearer " + token)
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/logout")).isEmpty();
    }

    @Test
    void deleteSession_malformedAccessCookie_returns401Gen00401() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", "not-a-jwt")
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void deleteSession_expiredAccessCookie_returns401Gen00401() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void deleteSession_tamperedAccessCookie_returns401Gen00401() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String signed = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));
        String token = signed.substring(0, signed.lastIndexOf('.') + 1) + "invalidsignature";

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void deleteSession_cookieSignedByOtherKey_returns401Gen00401() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String token = TokenTools.sign(TokenTools.otherPrivateKey(), "http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void deleteSession_wrongIssuerCookie_returns401Gen00401() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String token = TokenTools.sign("http://other.example/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
    }

    @Test
    void deleteSession_validCookiesWithoutCsrfToken_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                    .header("Accept-Language", "en")
                .when()
                    .post(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/logout")).isEmpty();
    }

    @Test
    void deleteSession_targetUnauthorized_returnsSameStatusAndBodyAndClearsCookies() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout_unauthorized");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
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
        assertThat(response.headers().getValues("Set-Cookie")).hasSize(2);
    }

    @Test
    void deleteSession_targetDropsConnection_returns500Gen00500WithoutDetails() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_logout_dropped_connection");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                    .header("Accept-Language", "en")
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
    void deleteSession_getMethod_returns405Gen00405() {
        MockTools.clearMocker();
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00405");
    }

    @Test
    void deleteSession_buyerCookieOnRoleProtectedRoute_returns403Gen00403() {
        MockTools.clearMocker();
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("buyer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get("/unknown_route")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
    }

    @Test
    void deleteSession_organizerCookieOnUnknownRoute_returns404Gen00404() {
        MockTools.clearMocker();
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get("/unknown_route")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00404");
    }

    @Test
    void preflight_allowedOrigin_allowsCredentialsAndCsrfHeaderWithoutToken() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type, x-xsrf-token")
                .when()
                    .options(POST_AUTH_LOGOUT)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
    }
}
