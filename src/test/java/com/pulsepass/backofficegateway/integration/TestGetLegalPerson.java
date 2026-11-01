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

class TestGetLegalPerson extends BaseIntegrationTest {

    private static final String GET_LEGAL_PERSON = "/legal_persons/{legal_person_key}";

    @Test
    @SuppressWarnings("unchecked")
    void readLegalPerson_organizerCookie_forwardsAgentKeyAndReturnsTargetBody() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("selected-agent-key", "forged-agent")
                .when()
                    .get(GET_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getString("legal_person_key")).isEqualTo("legal-person-from-target");
        assertThat(response.jsonPath().getString("owner.name")).isEqualTo("Maria Souza");
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "GET", "/onboarding/legal_persons/" + legalPersonKey);
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-agent-key")).containsExactly(legalPersonKey);
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Cookie");
    }

    @Test
    void readLegalPerson_noCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey));

        Response response = given()
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/legal_persons/" + legalPersonKey)).isEmpty();
    }

    @Test
    void readLegalPerson_adminCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key",
                Map.of("legal_person_key", legalPersonKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/legal_persons/" + legalPersonKey)).isEmpty();
    }

    @Test
    void readLegalPerson_targetNotFound_returnsTargetStatusAndBody() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key_not_found",
                Map.of("legal_person_key", legalPersonKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(GET_LEGAL_PERSON, legalPersonKey)
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
    void readLegalPerson_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key_dropped_connection",
                Map.of("legal_person_key", legalPersonKey));
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_LEGAL_PERSON, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00500");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
        assertThat(response.asString()).doesNotContain("onboarding", "mockserver", "Connection", "Exception");
    }
}
