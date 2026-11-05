package com.pulsepass.backofficegateway.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.common.CsrfTools;
import com.pulsepass.backofficegateway.common.MockTools;
import com.pulsepass.backofficegateway.common.TokenTools;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestDeleteInternalOrganization extends BaseIntegrationTest {

    private static final String DELETE_INTERNAL_ORGANIZATION = "/internal/organizations/{organization_key}";

    @Test
    @SuppressWarnings("unchecked")
    void deleteInternalOrganization_adminCookie_forwardsJwtHeadersAndReturns204WithoutBody() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/delete-organization_internal_organizations_organization_key",
                Map.of("organization_key", organizationKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", personKey,
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("selected-user-roles", "forged-role")
                    .header("selected-user-key", "forged-user")
                    .header("selected-agent-key", "forged-agent")
                    .header("internal-token", "forged-token")
                .when()
                    .delete(DELETE_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.asByteArray()).isEmpty();
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "DELETE", "/organization/internal/organizations/" + organizationKey);
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
    void deleteInternalOrganization_targetNotFound_returnsTargetStatusAndBody() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/delete-organization_internal_organizations_organization_key_not_found",
                Map.of("organization_key", organizationKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                .when()
                    .delete(DELETE_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Not Found",
                "message", "Resource not found.",
                "code", "GEN00404",
                "extra_fields", List.of()));
    }

    @Test
    void deleteInternalOrganization_keyWithEncodedHash_reachesTargetAsSingleSegment() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation(
                "organization-service/delete-organization_internal_organizations_organization_key",
                Map.of("organization_key", ".+"));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .urlEncodingEnabled(false)
                    .cookie("pulsepass_access_token", token)
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                .when()
                    .delete("/internal/organizations/x%23fragment")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(204);
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "DELETE", "/organization/internal/organizations/.+");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("queryStringParameters")).isNull();
        assertThat((String) requests.get(0).get("path")).isEqualTo(
                "/organization/internal/organizations/x%23fragment");
    }

    @Test
    void deleteInternalOrganization_noCookie_returns401Gen00401WithoutCallingTarget() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/delete-organization_internal_organizations_organization_key",
                Map.of("organization_key", organizationKey));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                .when()
                    .delete(DELETE_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("DELETE", "/organization/internal/organizations/" + organizationKey))
                .isEmpty();
    }

    @Test
    void deleteInternalOrganization_organizerCookie_returns403Gen00403WithoutCallingTarget() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/delete-organization_internal_organizations_organization_key",
                Map.of("organization_key", organizationKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("organizer"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                    .header("selected-user-roles", "admin")
                .when()
                    .delete(DELETE_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("DELETE", "/organization/internal/organizations/" + organizationKey))
                .isEmpty();
    }

    @Test
    void deleteInternalOrganization_adminWithoutCsrfToken_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/delete-organization_internal_organizations_organization_key",
                Map.of("organization_key", organizationKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                .when()
                    .delete(DELETE_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("DELETE", "/organization/internal/organizations/" + organizationKey))
                .isEmpty();
    }

    @Test
    void deleteInternalOrganization_targetDropsConnection_returns500Gen00500WithoutDetails() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/delete-organization_internal_organizations_organization_key_dropped_connection",
                Map.of("organization_key", organizationKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                .when()
                    .delete(DELETE_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Internal Server Error",
                "message", "An unexpected error occurred. Please try again later.",
                "code", "GEN00500",
                "extra_fields", List.of()));
    }

    @Test
    void preflight_delete_returnsAllowedOriginCredentialsAndHeaders() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "DELETE")
                    .header("Access-Control-Request-Headers", "content-type, x-xsrf-token")
                .when()
                    .options("/internal/organizations/any-key")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Headers")).isEqualTo("content-type, x-xsrf-token");
    }
}
