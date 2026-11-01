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

class TestGetOrganizations extends BaseIntegrationTest {

    private static final String GET_ORGANIZATIONS = "/organizations";

    @Test
    @SuppressWarnings("unchecked")
    void listOrganizations_organizerCookie_forwardsQueryAndAgentKeyAndReturnsTargetBody() {
        MockTools.clearMocker();
        String legalPersonKey = UUID.randomUUID().toString();
        MockTools.addExpectation("organization-service/get-organization_organizations");

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", legalPersonKey,
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("selected-agent-key", "forged-agent")
                    .queryParam("page", 2)
                    .queryParam("page_size", 10)
                .when()
                    .get(GET_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getString("data[0].organization_key")).isEqualTo("organization-from-target");
        assertThat(response.jsonPath().getInt("pagination.page")).isEqualTo(1);
        assertThat(response.jsonPath().getInt("pagination.page_size")).isEqualTo(30);
        List<Map<String, Object>> requests = MockTools.retrieveRequests("GET", "/organization/organizations");
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-agent-key")).containsExactly(legalPersonKey);
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Cookie");
        Map<String, List<String>> query = (Map<String, List<String>>) requests.get(0).get("queryStringParameters");
        assertThat(query.get("page")).containsExactly("2");
        assertThat(query.get("page_size")).containsExactly("10");
    }

    @Test
    void listOrganizations_noCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_organizations");

        Response response = given()
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/organization/organizations")).isEmpty();
    }

    @Test
    void listOrganizations_adminCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_organizations");

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("GET", "/organization/organizations")).isEmpty();
    }

    @Test
    void listOrganizations_targetServerError_returnsTargetStatusAndBody() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_organizations_server_error");

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                .when()
                    .get(GET_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00500");
        assertThat(response.jsonPath().getString("title")).isEqualTo("Internal Server Error");
        assertThat(response.jsonPath().getString("message")).isEqualTo("Unexpected error.");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
    }

    @Test
    void listOrganizations_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_organizations_dropped_connection");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00500");
        assertThat(response.jsonPath().getList("extra_fields")).isEmpty();
        assertThat(response.asString()).doesNotContain("organization", "mockserver", "Connection", "Exception");
    }
}
