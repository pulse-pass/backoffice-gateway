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

class TestGetPerson extends BaseIntegrationTest {

    private static final String GET_PERSON = "/persons/{person_key}";

    @Test
    @SuppressWarnings("unchecked")
    void readPerson_organizerCookie_forwardsAgentKeyAndReturnsTargetBody() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        String ownerKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-persons_person_key",
                Map.of("person_key", personKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", ownerKey, "legal_person_key", personKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("selected-agent-key", "forged-agent")
                    .header("selected-user-key", "forged-user")
                .when()
                    .get(GET_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getString("person_key")).isEqualTo("person-from-target");
        assertThat(response.jsonPath().getString("person_type")).isEqualTo("LEGAL");
        assertThat(response.jsonPath().getString("legal_person.email")).isEqualTo("contato@pulseeventos.com.br");
        assertThat(response.jsonPath().getString("legal_person.owner")).isNull();
        assertThat(response.jsonPath().getString("natural_person")).isNull();
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "GET", "/onboarding/persons/" + personKey);
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-agent-key")).containsExactly(personKey);
        assertThat(headers.get("selected-user-key")).containsExactly(ownerKey);
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Cookie");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readPerson_keyWithEncodedQuery_reachesTargetAsSingleSegment() {
        MockTools.clearMocker();
        MockTools.addExpectation(
                "onboarding-service/get-persons_person_key",
                Map.of("person_key", ".+"));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .urlEncodingEnabled(false)
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get("/persons/x%3Fstatus=PENDING")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> requests = MockTools.retrieveRequests("GET", "/onboarding/persons/.+");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("queryStringParameters")).isNull();
        assertThat((String) requests.get(0).get("path")).isEqualTo(
                "/onboarding/persons/x%3Fstatus=PENDING");
    }

    @Test
    void readPerson_noCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-persons_person_key",
                Map.of("person_key", personKey));

        Response response = given()
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/persons/" + personKey)).isEmpty();
    }

    @Test
    void readPerson_adminCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-persons_person_key",
                Map.of("person_key", personKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", personKey,
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("GET", "/onboarding/persons/" + personKey)).isEmpty();
    }

    @Test
    void readPerson_targetNotFound_returnsTargetStatusAndBody() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-persons_person_key_not_found",
                Map.of("person_key", personKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", personKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(GET_PERSON, personKey)
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
    void readPerson_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-persons_person_key_dropped_connection",
                Map.of("person_key", personKey));
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", personKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_PERSON, personKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00500");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
        assertThat(response.asString()).doesNotContain("onboarding", "mockserver", "Connection", "Exception");
    }
}
