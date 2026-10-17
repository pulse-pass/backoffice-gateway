package com.pulsepass.backofficegateway.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.common.MockServerHandlers;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class TestPostCreateLegalPerson extends BaseIntegrationTest {

    @Test
    void createLegalPerson_targetCreated_returnsSameStatusBodyAndContentType() {
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        String body = "{\"person_key\":\"7f1c1a56-2b1b-4a53-9a43-1f2b5d3d1a11\",\"document_number\":\""
                + document + "\"}";
        MockServerHandlers.expectResponse(
                "POST", MockServerHandlers.LEGAL_PERSONS_PATH, document, 201, "application/json", body);

        Response response = given()
                    .header("Accept-Language", "en")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body("{\"document_number\":\"" + document + "\",\"name\":\"Lenha Producoes\"}")
                .when()
                    .post("/legal_persons")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.asString()).isEqualTo(body);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(MockServerHandlers.verifyReceived("POST", MockServerHandlers.LEGAL_PERSONS_PATH, document))
                .isTrue();
    }

    @Test
    void createLegalPerson_targetBadRequest_returnsSameErrorWithExtraFields() {
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        String body = "{\"title\":\"Bad Request\",\"message\":\"invalid\",\"code\":\"GEN00400\","
                + "\"extra_fields\":[{\"field\":\"name\",\"error\":\"REQUIRED\"}]}";
        MockServerHandlers.expectResponse(
                "POST", MockServerHandlers.LEGAL_PERSONS_PATH, document, 400, "application/json", body);

        Response response = given()
                    .header("Accept-Language", "en")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body("{\"document_number\":\"" + document + "\",\"name\":\"Lenha Producoes\"}")
                .when()
                    .post("/legal_persons")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.asString()).isEqualTo(body);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00400");
        assertThat(response.jsonPath().getList("extra_fields")).hasSize(1);
    }

    @Test
    void createLegalPerson_targetConflict_returnsSameOnb00001() {
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        String body = "{\"title\":\"Conflict\",\"message\":\"already registered\",\"code\":\"ONB00001\","
                + "\"extra_fields\":[]}";
        MockServerHandlers.expectResponse(
                "POST", MockServerHandlers.LEGAL_PERSONS_PATH, document, 409, "application/json", body);

        Response response = given()
                    .header("Accept-Language", "en")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body("{\"document_number\":\"" + document + "\",\"name\":\"Lenha Producoes\"}")
                .when()
                    .post("/legal_persons")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.asString()).isEqualTo(body);
        assertThat(response.jsonPath().getString("code")).isEqualTo("ONB00001");
    }

    @Test
    void createLegalPerson_targetServerError_returnsSameStatusAndBody() {
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        String body = "{\"code\":\"GEN00500\",\"message\":\"from target\"}";
        MockServerHandlers.expectResponse(
                "POST", MockServerHandlers.LEGAL_PERSONS_PATH, document, 500, "application/json", body);

        Response response = given()
                    .header("Accept-Language", "en")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body("{\"document_number\":\"" + document + "\",\"name\":\"Lenha Producoes\"}")
                .when()
                    .post("/legal_persons")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.asString()).isEqualTo(body);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLegalPerson_headers_forwardsAcceptLanguageAndNeverInternalToken() {
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockServerHandlers.expectResponse(
                "POST", MockServerHandlers.LEGAL_PERSONS_PATH, document, 201, "application/json", "{}");

        given()
                    .header("Accept-Language", "pt-BR")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body("{\"document_number\":\"" + document + "\",\"name\":\"Lenha Producoes\"}")
                .when()
                    .post("/legal_persons")
                .then()
                    .extract().response();

        List<Map<String, Object>> requests = MockServerHandlers.retrieveRequests(
                "POST", MockServerHandlers.LEGAL_PERSONS_PATH, document);
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
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockServerHandlers.expectDroppedConnection("POST", MockServerHandlers.LEGAL_PERSONS_PATH, document);

        Response response = given()
                    .header("Accept-Language", "en")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body("{\"document_number\":\"" + document + "\",\"name\":\"Lenha Producoes\"}")
                .when()
                    .post("/legal_persons")
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
        String document = String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
        MockServerHandlers.expectDroppedConnection("POST", MockServerHandlers.LEGAL_PERSONS_PATH, document);

        Response response = given()
                    .header("Accept-Language", "pt-BR")
                    .header("internal-token", "must-not-be-forwarded")
                    .contentType(ContentType.JSON)
                    .body("{\"document_number\":\"" + document + "\",\"name\":\"Lenha Producoes\"}")
                .when()
                    .post("/legal_persons")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getString("message"))
                .isEqualTo("Ocorreu um erro inesperado. Tente novamente mais tarde.");
    }

    @Test
    void createLegalPerson_getMethod_returns405Gen00405() {
        Response response = given().when().get("/legal_persons").then().extract().response();

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00405");
    }

    @Test
    void unknownRoute_getRequest_returns404Gen00404() {
        Response response = given().when().get("/unknown_route").then().extract().response();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00404");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
    }

    @Test
    void preflight_allowedOrigin_returnsAllowOriginHeader() {
        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                .when()
                    .options("/legal_persons")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
    }
}
