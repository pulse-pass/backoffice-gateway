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

class TestGetInternalOrganizationRejectionReasons extends BaseIntegrationTest {

    private static final String GET_INTERNAL_REJECTION_REASONS = "/internal/organization_rejection_reasons";
    private static final String TARGET_PATH = "/organization/internal/organization_rejection_reasons";

    private String token(String role, String personKey) {
        return TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", personKey, "realm_access", Map.of("roles", List.of(role))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listInternalOrganizationRejectionReasons_adminCookie_forwardsQueryAndJwtHeadersAndReturnsTargetBody() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organization_rejection_reasons");
        String personKey = UUID.randomUUID().toString();

        Response response = given()
                    .cookie("pulsepass_access_token", this.token("admin", personKey))
                    .header("selected-user-roles", "forged-role")
                    .header("selected-user-key", "forged-user")
                    .header("selected-agent-key", "forged-agent")
                    .header("internal-token", "forged-token")
                    .queryParam("page", 2)
                    .queryParam("page_size", 10)
                .when()
                    .get(GET_INTERNAL_REJECTION_REASONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "data", List.of(Map.of(
                        "organization_rejection_reason", "INACTIVE_DOCUMENT",
                        "organization_rejection_reason_description", "CNPJ inapto.")),
                "pagination", Map.of("page", 1, "page_size", 30)));
        List<Map<String, Object>> requests = MockTools.retrieveRequests("GET", TARGET_PATH);
        assertThat(requests).hasSize(1);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("selected-user-roles")).containsExactly("admin");
        assertThat(headers.get("selected-user-key")).containsExactly(personKey);
        assertThat(headers).doesNotContainKey("selected-agent-key");
        assertThat(headers).doesNotContainKey("internal-token");
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Cookie");
        Map<String, List<String>> query = (Map<String, List<String>>) requests.get(0).get("queryStringParameters");
        assertThat(query.get("page")).containsExactly("2");
        assertThat(query.get("page_size")).containsExactly("10");
    }

    @Test
    void listInternalOrganizationRejectionReasons_noCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organization_rejection_reasons");

        Response response = given()
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_REJECTION_REASONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", TARGET_PATH)).isEmpty();
    }

    @Test
    void listInternalOrganizationRejectionReasons_organizerCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organization_rejection_reasons");

        Response response = given()
                    .cookie("pulsepass_access_token", this.token("organizer", UUID.randomUUID().toString()))
                    .header("Accept-Language", "en")
                    .header("selected-user-roles", "admin")
                    .header("internal-token", "forged-token")
                .when()
                    .get(GET_INTERNAL_REJECTION_REASONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("GET", TARGET_PATH)).isEmpty();
    }

    @Test
    void listInternalOrganizationRejectionReasons_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        MockTools.addExpectation(
                "organization-service/get-organization_internal_organization_rejection_reasons_dropped_connection");

        Response response = given()
                    .cookie("pulsepass_access_token", this.token("admin", UUID.randomUUID().toString()))
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_REJECTION_REASONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Internal Server Error",
                "message", "An unexpected error occurred. Please try again later.",
                "code", "GEN00500",
                "extra_fields", List.of()));
    }
}
