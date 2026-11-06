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

class TestGetInternalPerson extends BaseIntegrationTest {

    private static final String GET_INTERNAL_PERSON = "/internal/persons/{person_key}";

    @Test
    @SuppressWarnings("unchecked")
    void readInternalPerson_adminCookie_forwardsJwtHeadersAndReturnsTargetBody() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key",
                Map.of("person_key", personKey),
                Map.of("payload", Map.of("person_key", personKey)));

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
                    .get(GET_INTERNAL_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getString("person_key")).isEqualTo(personKey);
        assertThat(response.jsonPath().getString("person_type")).isEqualTo("LEGAL");
        assertThat(response.jsonPath().getString("name")).isEqualTo("Pulse Eventos Ltda");
        assertThat(response.jsonPath().getString("document_number")).isEqualTo("34512889000107");
        assertThat(response.jsonPath().getString("created_at")).isEqualTo("2026-10-09T14:12:00Z");
        assertThat(response.jsonPath().getList("phone_numbers")).containsExactly(
                Map.of("country_code", "55", "area_code", "11", "number", "987654321"));
        assertThat(response.jsonPath().getString("legal_person.email")).isEqualTo("contato@pulseeventos.com.br");
        assertThat(response.jsonPath().getString("legal_person.legal_person_company_type")).isEqualTo("LIMITED");
        assertThat(response.jsonPath().getString("legal_person.monthly_revenue_amount")).isEqualTo("25000.0");
        assertThat(response.jsonPath().getString("legal_person.legal_person_address.postal_code"))
                .isEqualTo("01310100");
        assertThat(response.jsonPath().getString("legal_person.owner")).isNull();
        assertThat(response.jsonPath().getString("natural_person")).isNull();
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "GET", "/onboarding/internal/persons/" + personKey);
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
    void readInternalPerson_keyWithEncodedQuery_reachesTargetAsSingleSegment() {
        MockTools.clearMocker();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key",
                Map.of("person_key", ".+"));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .urlEncodingEnabled(false)
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get("/internal/persons/x%3Fstatus=PENDING")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "GET", "/onboarding/internal/persons/.+");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("queryStringParameters")).isNull();
        assertThat((String) requests.get(0).get("path")).isEqualTo(
                "/onboarding/internal/persons/x%3Fstatus=PENDING");
    }

    @Test
    void readInternalPerson_keyWithEncodedSlashAndDots_doesNotReachOtherTargetRoutes() {
        MockTools.clearMocker();

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .urlEncodingEnabled(false)
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get("/internal/persons/..%2Forganizations")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(MockTools.retrieveRequests("GET", ".*")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void readInternalPerson_keyWithEncodedHash_reachesTargetAsSingleSegment() {
        MockTools.clearMocker();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key",
                Map.of("person_key", ".+"));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .urlEncodingEnabled(false)
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get("/internal/persons/x%23fragment")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "GET", "/onboarding/internal/persons/.+");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("queryStringParameters")).isNull();
        assertThat((String) requests.get(0).get("path")).isEqualTo(
                "/onboarding/internal/persons/x%23fragment");
    }

    @Test
    void readInternalPerson_noCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key",
                Map.of("person_key", personKey));

        Response response = given()
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/persons/" + personKey))
                .isEmpty();
    }

    @Test
    void readInternalPerson_tamperedCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key",
                Map.of("person_key", personKey));
        String signed = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));
        String token = signed.substring(0, signed.lastIndexOf('.') + 1) + "invalidsignature";

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/persons/" + personKey)).isEmpty();
    }

    @Test
    void readInternalPerson_expiredCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key",
                Map.of("person_key", personKey));
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/persons/" + personKey)).isEmpty();
    }

    @Test
    void readInternalPerson_cookieSignedByOtherKey_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key",
                Map.of("person_key", personKey));
        String token = TokenTools.sign(TokenTools.otherPrivateKey(), "http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/persons/" + personKey)).isEmpty();
    }

    @Test
    void readInternalPerson_wrongIssuerCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key",
                Map.of("person_key", personKey));
        String token = TokenTools.sign("http://other.example/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/persons/" + personKey)).isEmpty();
    }

    @Test
    void readInternalPerson_organizerCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key",
                Map.of("person_key", personKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", personKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                    .header("selected-user-roles", "admin")
                    .header("internal-token", "forged-token")
                .when()
                    .get(GET_INTERNAL_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/persons/" + personKey))
                .isEmpty();
    }

    @Test
    void readInternalPerson_buyerCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key",
                Map.of("person_key", personKey));

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
                    .get(GET_INTERNAL_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/internal/persons/" + personKey))
                .isEmpty();
    }

    @Test
    void readInternalPerson_targetNotFound_returnsTargetStatusAndBody() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key_not_found",
                Map.of("person_key", personKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(GET_INTERNAL_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getString("code")).isEqualTo("ONB00009");
        assertThat(response.jsonPath().getString("title")).isEqualTo("Not Found");
        assertThat(response.jsonPath().getString("message")).isEqualTo("The person was not found.");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
    }

    @Test
    void readInternalPerson_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-internal_persons_person_key_dropped_connection",
                Map.of("person_key", personKey));
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_PERSON, personKey)
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
