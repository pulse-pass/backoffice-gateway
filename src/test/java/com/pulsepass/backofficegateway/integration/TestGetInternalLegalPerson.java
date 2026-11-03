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

class TestGetInternalLegalPerson extends BaseIntegrationTest {

    private static final String GET_INTERNAL_LEGAL_PERSON = "/internal/legal_persons/{legal_person_key}";

    @Test
    @SuppressWarnings("unchecked")
    void readInternalLegalPerson_adminCookie_forwardsJwtHeadersAndReturnsTargetBody() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey),
                Map.of("payload", Map.of("legal_person_key", legalPersonKey)));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", personKey,
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("selected-user-roles", "forged-role")
                    .header("selected-user-key", "forged-user")
                    .header("selected-agent-key", "forged-agent")
                    .header("internal-token", "forged-token")
                .when()
                    .get(GET_INTERNAL_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "legal_person_key", legalPersonKey,
                "name", "Pulse Eventos Ltda",
                "document_number", "34512889000107",
                "email", "contato@pulseeventos.com.br",
                "created_at", "2026-10-09T14:12:00Z",
                "phone_numbers", List.of(Map.of("country_code", "55", "area_code", "11", "number", "987654321")),
                "owner", Map.of(
                        "person_key", "person-from-target",
                        "name", "Maria Souza",
                        "document_number", "04833127012",
                        "email", "maria@pulseeventos.com.br")));
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "GET", "/onboarding/internal/legal_persons/" + legalPersonKey);
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-user-roles")).containsExactly("admin");
        assertThat(headers.get("selected-user-key")).containsExactly(personKey);
        assertThat(headers).doesNotContainKey("selected-agent-key");
        assertThat(headers).doesNotContainKey("internal-token");
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Cookie");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readInternalLegalPerson_keyWithEncodedQuery_reachesTargetAsSingleSegment() {
        MockTools.clearMocker();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key",
                Map.of("legal_person_key", ".+"));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .urlEncodingEnabled(false)
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get("/internal/legal_persons/x%3Fstatus=PENDING")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "GET", "/onboarding/internal/legal_persons/.+");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("queryStringParameters")).isNull();
        assertThat((String) requests.get(0).get("path")).isEqualTo(
                "/onboarding/internal/legal_persons/x%3Fstatus=PENDING");
    }

    @Test
    void readInternalLegalPerson_keyWithEncodedSlashAndDots_doesNotReachOtherTargetRoutes() {
        MockTools.clearMocker();

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .urlEncodingEnabled(false)
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get("/internal/legal_persons/..%2Forganizations")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(MockTools.retrieveRequests("GET", ".*")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void readInternalLegalPerson_keyWithEncodedHash_reachesTargetAsSingleSegment() {
        MockTools.clearMocker();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key",
                Map.of("legal_person_key", ".+"));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .urlEncodingEnabled(false)
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get("/internal/legal_persons/x%23fragment")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "GET", "/onboarding/internal/legal_persons/.+");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("queryStringParameters")).isNull();
        assertThat((String) requests.get(0).get("path")).isEqualTo(
                "/onboarding/internal/legal_persons/x%23fragment");
    }

    @Test
    void readInternalLegalPerson_noCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey));

        Response response = given()
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/legal_persons/" + legalPersonKey))
                .isEmpty();
    }

    @Test
    void readInternalLegalPerson_tamperedCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey));
        String signed = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));
        String token = signed.substring(0, signed.lastIndexOf('.') + 1) + "invalidsignature";

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/legal_persons/" + legalPersonKey)).isEmpty();
    }

    @Test
    void readInternalLegalPerson_expiredCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey));
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/legal_persons/" + legalPersonKey)).isEmpty();
    }

    @Test
    void readInternalLegalPerson_cookieSignedByOtherKey_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey));
        String token = TokenTools.sign(TokenTools.otherPrivateKey(), "http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/legal_persons/" + legalPersonKey)).isEmpty();
    }

    @Test
    void readInternalLegalPerson_wrongIssuerCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey));
        String token = TokenTools.sign("http://other.example/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/legal_persons/" + legalPersonKey)).isEmpty();
    }

    @Test
    void readInternalLegalPerson_organizerCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                    .header("selected-user-roles", "admin")
                    .header("internal-token", "forged-token")
                .when()
                    .get(GET_INTERNAL_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/legal_persons/" + legalPersonKey))
                .isEmpty();
    }

    @Test
    void readInternalLegalPerson_buyerCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("buyer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                    .header("selected-user-roles", "admin")
                    .header("internal-token", "forged-token")
                .when()
                    .get(GET_INTERNAL_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/legal_persons/" + legalPersonKey))
                .isEmpty();
    }

    @Test
    void readInternalLegalPerson_targetNotFound_returnsTargetStatusAndBody() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key_not_found",
                Map.of("legal_person_key", legalPersonKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(GET_INTERNAL_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00404");
        assertThat(response.jsonPath().getString("title")).isEqualTo("Not Found");
        assertThat(response.jsonPath().getString("message")).isEqualTo("Resource not found.");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
    }

    @Test
    void readInternalLegalPerson_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_legal_persons_legal_person_key_dropped_connection",
                Map.of("legal_person_key", legalPersonKey));
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Internal Server Error",
                "message", "An unexpected error occurred. Please try again later.",
                "code", "GEN00500",
                "extra_fields", List.of()));
    }
}
