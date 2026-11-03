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

class TestGetInternalOrganizations extends BaseIntegrationTest {

    private static final String GET_INTERNAL_ORGANIZATIONS = "/internal/organizations";

    @Test
    @SuppressWarnings("unchecked")
    void listInternalOrganizations_adminCookie_forwardsQueryAndJwtHeadersAndReturnsTargetBody() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organizations");
        String personKey = UUID.randomUUID().toString();

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
                    .queryParam("organization_status", "PENDING")
                    .queryParam("name", "Pulse")
                    .queryParam("document_number", "34512889000107")
                    .queryParam("page", 2)
                    .queryParam("page_size", 10)
                .when()
                    .get(GET_INTERNAL_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "data", List.of(Map.of(
                        "organization_key", "organization-from-target",
                        "legal_person_key", "legal-person-from-target",
                        "name", "Pulse Eventos Ltda",
                        "document_number", "34512889000107",
                        "organization_status", "REJECTED",
                        "organization_rejection_reason", "INACTIVE_DOCUMENT",
                        "organization_rejection_reason_description", "CNPJ inapto.",
                        "created_at", "2026-10-09T14:12:00Z",
                        "updated_at", "2026-10-10T09:00:00Z")),
                "pagination", Map.of("page", 1, "page_size", 30)));
        List<Map<String, Object>> requests = MockTools.retrieveRequests("GET", "/organization/internal/organizations");
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
        assertThat(query.get("organization_status")).containsExactly("PENDING");
        assertThat(query.get("name")).containsExactly("Pulse");
        assertThat(query.get("document_number")).containsExactly("34512889000107");
        assertThat(query.get("page")).containsExactly("2");
        assertThat(query.get("page_size")).containsExactly("10");
    }

    @Test
    void listInternalOrganizations_noCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organizations");

        Response response = given()
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/organization/internal/organizations")).isEmpty();
    }

    @Test
    void listInternalOrganizations_tamperedCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organizations");
        String signed = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));
        String token = signed.substring(0, signed.lastIndexOf('.') + 1) + "invalidsignature";

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/organization/internal/organizations")).isEmpty();
    }

    @Test
    void listInternalOrganizations_expiredCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organizations");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/organization/internal/organizations")).isEmpty();
    }

    @Test
    void listInternalOrganizations_cookieSignedByOtherKey_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organizations");
        String token = TokenTools.sign(TokenTools.otherPrivateKey(), "http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/organization/internal/organizations")).isEmpty();
    }

    @Test
    void listInternalOrganizations_wrongIssuerCookie_returns401Gen00401WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organizations");
        String token = TokenTools.sign("http://other.example/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("GET", "/organization/internal/organizations")).isEmpty();
    }

    @Test
    void listInternalOrganizations_organizerCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organizations");

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(), "legal_person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                    .header("selected-user-roles", "admin")
                    .header("internal-token", "forged-token")
                .when()
                    .get(GET_INTERNAL_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("GET", "/organization/internal/organizations")).isEmpty();
    }

    @Test
    void listInternalOrganizations_buyerCookie_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organizations");

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
                    .get(GET_INTERNAL_ORGANIZATIONS)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("GET", "/organization/internal/organizations")).isEmpty();
    }

    @Test
    void listInternalOrganizations_targetDropsConnection_returns500Gen00500WithoutDetails() {
        MockTools.clearMocker();
        MockTools.addExpectation("organization-service/get-organization_internal_organizations_dropped_connection");
        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .get(GET_INTERNAL_ORGANIZATIONS)
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
