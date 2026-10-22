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

class TestPostRefreshSession extends BaseIntegrationTest {

    private static final String POST_AUTH_REFRESH = "/auth/refresh";

    @Test
    @SuppressWarnings("unchecked")
    void refreshSession_refreshCookie_sendsRefreshTokenAsJsonAndRewritesCookies() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String oldRefreshToken = UUID.randomUUID().toString();
        String newAccessToken = UUID.randomUUID().toString();
        String newRefreshToken = UUID.randomUUID().toString();
        MockTools.addExpectation("onboarding-service/post-auth_refresh", Map.of(), Map.of("payload", Map.of(
                "access_token", newAccessToken,
                "expires_in", 240,
                "refresh_token", newRefreshToken,
                "refresh_expires_in", 1200
        )));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_refresh_token", oldRefreshToken)
                    .header("Accept-Language", "pt-BR")
                    .contentType("text/plain")
                    .body("ignored by the gateway")
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        List<String> cookies = response.headers().getValues("Set-Cookie");
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.asString()).isEmpty();
        assertThat(cookies).hasSize(2);
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_access_token=" + newAccessToken + ";")
                .contains("Max-Age=240", "Path=/;", "HttpOnly", "Secure", "SameSite=Strict"));
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_refresh_token=" + newRefreshToken + ";")
                .contains("Max-Age=1200", "Path=/auth", "HttpOnly", "Secure", "SameSite=Strict"));
        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/auth/refresh");
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("Content-Type").get(0)).startsWith("application/json");
        assertThat(headers.get("Accept-Language").get(0)).isEqualTo("pt-BR");
        assertThat(headers).doesNotContainKey("Cookie");
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(requests.get(0).get("body").toString()).contains(oldRefreshToken);
    }

    @Test
    void refreshSession_noRefreshCookie_returns401Gen00401WithoutCallingTarget() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(response.jsonPath().getString("message"))
                .isEqualTo("Authentication is required to access this resource.");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/refresh")).isEmpty();
    }

    @Test
    void refreshSession_targetUnauthorized_clearsCookiesAndReturnsSameStatusAndBody() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh_unauthorized");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                    .header("Accept-Language", "en")
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        List<String> cookies = response.headers().getValues("Set-Cookie");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Unauthorized",
                "message", "rejected by target",
                "code", "GEN00401",
                "extra_fields", List.of()
        ));
        assertThat(cookies).hasSize(2);
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_access_token=;")
                .contains("Max-Age=0", "Path=/;", "HttpOnly", "Secure", "SameSite=Strict"));
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("pulsepass_refresh_token=;")
                .contains("Max-Age=0", "Path=/auth", "HttpOnly", "Secure", "SameSite=Strict"));
    }

    @Test
    void refreshSession_targetDropsConnection_returns500Gen00500WithoutDetails() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh_dropped_connection");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                    .header("Accept-Language", "en")
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
    void refreshSession_withoutCsrfToken_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh");

        Response response = given()
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                    .header("Accept-Language", "en")
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/refresh")).isEmpty();
    }

    @Test
    void refreshSession_expiredAccessCookie_isIgnoredAndRefreshed() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                    .contentType(ContentType.JSON)
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/refresh")).hasSize(1);
    }

    @Test
    void refreshSession_tamperedAccessCookie_isIgnoredAndRefreshed() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-auth_refresh");
        String signed = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of("organizer"))));
        String token = signed.substring(0, signed.lastIndexOf('.') + 1) + "invalidsignature";

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .cookie("pulsepass_refresh_token", UUID.randomUUID().toString())
                .when()
                    .post(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/auth/refresh")).hasSize(1);
    }

    @Test
    void preflight_allowedOrigin_allowsCredentialsWithoutToken() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "x-xsrf-token")
                .when()
                    .options(POST_AUTH_REFRESH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
    }
}
