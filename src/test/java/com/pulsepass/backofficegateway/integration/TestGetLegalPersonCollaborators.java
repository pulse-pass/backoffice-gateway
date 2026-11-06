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

class TestGetLegalPersonCollaborators extends BaseIntegrationTest {

    private static final String ROUTE = "/legal_persons/{legal_person_key}/collaborators";

    @Test
    @SuppressWarnings("unchecked")
    void listLegalPersonCollaborators_organizerCookie_forwardsQueryAndGeneratedHeadersAndReturnsTargetBody() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        String userKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key_collaborators",
                Map.of("legal_person_key", legalPersonKey));
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", userKey, "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("selected-agent-key", "forged-agent")
                    .header("selected-user-key", "forged-user")
                    .queryParam("collaborator_role", "OWNER")
                    .queryParam("page", 2)
                    .queryParam("page_size", 10)
                .when()
                    .get(ROUTE, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getString("data[0].legal_person_collaborator_key"))
                .isEqualTo("collaborator-from-target");
        assertThat(response.jsonPath().getString("data[0].collaborator_role")).isEqualTo("OWNER");
        assertThat(response.jsonPath().getString("data[0].natural_person.person.name")).isEqualTo("Maria Souza");
        assertThat(response.jsonPath().getInt("pagination.page_size")).isEqualTo(10);
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "GET", "/onboarding/legal_persons/%s/collaborators".formatted(legalPersonKey));
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-user-key")).containsExactly(userKey);
        assertThat(headers.get("selected-agent-key")).containsExactly(legalPersonKey);
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Cookie");
        Map<String, List<String>> query = (Map<String, List<String>>) requests.get(0).get("queryStringParameters");
        assertThat(query.get("collaborator_role")).containsExactly("OWNER");
        assertThat(query.get("page")).containsExactly("2");
        assertThat(query.get("page_size")).containsExactly("10");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listLegalPersonCollaborators_keyWithEncodedQuery_reachesTargetAsSingleSegment() {
        MockTools.clearMocker();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key_collaborators",
                Map.of("legal_person_key", ".+"));
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .urlEncodingEnabled(false)
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get("/legal_persons/x%3Fstatus=PENDING/collaborators")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "GET", "/onboarding/legal_persons/.+/collaborators");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("queryStringParameters")).isNull();
        assertThat((String) requests.get(0).get("path")).isEqualTo(
                "/onboarding/legal_persons/x%3Fstatus=PENDING/collaborators");
    }

    @Test
    void listLegalPersonCollaborators_noCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key_collaborators",
                Map.of("legal_person_key", legalPersonKey));

        Response response = given()
                    .header("Accept-Language", "en")
                .when()
                    .get(ROUTE, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests(
                "GET", "/onboarding/legal_persons/%s/collaborators".formatted(legalPersonKey))).isEmpty();
    }

    @Test
    void listLegalPersonCollaborators_adminCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key_collaborators",
                Map.of("legal_person_key", legalPersonKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(ROUTE, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests(
                "GET", "/onboarding/legal_persons/%s/collaborators".formatted(legalPersonKey))).isEmpty();
    }

    @Test
    void listLegalPersonCollaborators_targetNotFound_returnsTargetStatusAndBody() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key_collaborators_not_found",
                Map.of("legal_person_key", legalPersonKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(ROUTE, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.jsonPath().getString("code")).isEqualTo("ONB00009");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
    }

    @Test
    void listLegalPersonCollaborators_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "onboarding-service/get-legal_persons_legal_person_key_collaborators_dropped_connection",
                Map.of("legal_person_key", legalPersonKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(ROUTE, legalPersonKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00500");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
        assertThat(response.asString()).doesNotContain("onboarding", "mockserver", "Connection", "Exception");
    }
}
