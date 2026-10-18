package com.pulsepass.backofficegateway.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.common.MockTools;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation("onboarding-service/post-legal_persons", Map.of(),
                Map.of("payload", Map.of("person_key", personKey, "document_number", document)));

        Response response = given()
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
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons_bad_request");

        Response response = given()
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
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons_conflict");

        Response response = given()
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
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons_server_error");

        Response response = given()
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
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons");

        given()
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
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons_dropped_connection");

        Response response = given()
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
        MockTools.clearMocker();
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockTools.addExpectation("onboarding-service/post-legal_persons_dropped_connection");

        Response response = given()
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

        Response response = given().when().get(POST_LEGAL_PERSONS).then().extract().response();

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00405");
    }

    @Test
    void unknownRoute_getRequest_returns404Gen00404() {
        MockTools.clearMocker();

        Response response = given().when().get("/unknown_route").then().extract().response();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00404");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
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
}
