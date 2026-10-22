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
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class TestPostCreateLegalPerson extends BaseIntegrationTest {

    private static final String POST_LEGAL_PERSONS = "/legal_persons";

    @Test
    void createLegalPerson_targetCreated_returnsSameStatusBodyAndContentType() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation("onboarding-service/post-legal_persons", Map.of(),
                Map.of("payload", Map.of("person_key", personKey, "document_number", document)));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", document, "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$"))
                .isEqualTo(Map.of("person_key", personKey, "document_number", document));
        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/legal_persons");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("body").toString()).contains(document, "Lenha Producoes");
    }

    @Test
    void createLegalPerson_targetBadRequest_returnsSameErrorWithExtraFields() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons_bad_request");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", document, "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Bad Request",
                "message", "invalid",
                "code", "GEN00400",
                "extra_fields", List.of(Map.of("field", "name", "error", "REQUIRED"))
        ));
    }

    @Test
    void createLegalPerson_targetConflict_returnsSameOnb00001() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons_conflict");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", document, "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Conflict",
                "message", "already registered",
                "code", "ONB00001",
                "extra_fields", List.of()
        ));
    }

    @Test
    void createLegalPerson_targetServerError_returnsSameStatusAndBody() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons_server_error");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", document, "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$"))
                .isEqualTo(Map.of("code", "GEN00500", "message", "from target"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLegalPerson_headers_forwardsAcceptLanguageAndNeverInternalToken() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons");

        given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "pt-BR")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", document, "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        List<Map<String, Object>> requests = MockTools.retrieveRequests("POST", "/onboarding/legal_persons");
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("Accept-Language").get(0)).isEqualTo("pt-BR");
        assertThat(headers.get("Content-Type").get(0)).startsWith("application/json");
        assertThat(headers).doesNotContainKey("internal-token");
        assertThat(headers).doesNotContainKey("Authorization");
    }

    @Test
    void createLegalPerson_targetDropsConnection_returns500Gen00500WithoutDetails() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons_dropped_connection");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", document, "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
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
    void createLegalPerson_targetDropsConnectionInPortuguese_returnsTranslatedMessage() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons_dropped_connection");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "pt-BR")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", document, "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getString("message"))
                .isEqualTo("Ocorreu um erro inesperado. Tente novamente mais tarde.");
    }

    @Test
    void createLegalPerson_getMethod_returns405Gen00405() {
        MockTools.clearMocker();
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300), Map.of("realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00405");
    }

    @Test
    void unknownRoute_getRequest_returns404Gen00404() {
        MockTools.clearMocker();
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300), Map.of("realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get("/unknown_route")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00404");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
    }

    @Test
    void createLegalPerson_expiredAccessCookie_isIgnoredAndForwarded() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-legal_persons");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(300), Map.of("realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "12345678000190", "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/legal_persons")).hasSize(1);
    }

    @Test
    void createLegalPerson_tamperedAccessCookie_isIgnoredAndForwarded() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-legal_persons");
        String signed = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300), Map.of("realm_access", Map.of("roles", List.of("organizer"))));
        String token = signed.substring(0, signed.lastIndexOf('.') + 1) + "invalidsignature";

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .cookie("pulsepass_access_token", token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "12345678000190", "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/legal_persons")).hasSize(1);
    }

    @Test
    void preflight_allowedOrigin_returnsAllowOriginHeader() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                .when()
                    .options(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
    }

    @Test
    void createLegalPerson_withoutCsrfToken_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-legal_persons");

        Response response = given()
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "12345678000190", "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("POST", "/onboarding/legal_persons")).isEmpty();
    }

    @Test
    void createLegalPerson_csrfCookieWithoutHeader_returns403Gen00403() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-legal_persons");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "12345678000190", "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
    }

    @Test
    void createLegalPerson_authorizationBearerHeader_isIgnoredAndNotForwarded() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation("onboarding-service/post-legal_persons");

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Authorization", "Bearer not-a-jwt")
                    .contentType(ContentType.JSON)
                    .body(Map.of("document_number", "12345678000190", "name", "Lenha Producoes"))
                .when()
                    .post(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void preflight_credentialsAndCsrfHeader_areAllowed() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type, x-xsrf-token")
                .when()
                    .options(POST_LEGAL_PERSONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Headers")).isEqualTo("content-type, x-xsrf-token");
    }
}
