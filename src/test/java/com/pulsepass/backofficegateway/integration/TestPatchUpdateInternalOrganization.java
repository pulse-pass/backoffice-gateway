package com.pulsepass.backofficegateway.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.pulsepass.backofficegateway.common.CsrfTools;
import com.pulsepass.backofficegateway.common.MockTools;
import com.pulsepass.backofficegateway.common.TokenTools;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestPatchUpdateInternalOrganization extends BaseIntegrationTest {

    private static final String PATCH_INTERNAL_ORGANIZATION = "/internal/organizations/{organization_key}";

    @Test
    @SuppressWarnings("unchecked")
    void updateInternalOrganization_adminCookie_forwardsBodyAndJwtHeadersAndReturnsTargetBody() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        String personKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/patch-organization_internal_organizations_organization_key",
                Map.of("organization_key", organizationKey),
                Map.of("payload", Map.of("organization_key", organizationKey)));

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
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "organization_status", "REJECTED",
                            "organization_rejection_reason", "INACTIVE_DOCUMENT"))
                .when()
                    .patch(PATCH_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "organization_key", organizationKey,
                "organization_status", "REJECTED",
                "organization_rejection_reason", "INACTIVE_DOCUMENT",
                "organization_rejection_reason_description", "CNPJ inapto."));
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "PATCH", "/organization/internal/organizations/" + organizationKey);
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("body").toString())
                .contains("REJECTED", "INACTIVE_DOCUMENT");
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll((Map<String, List<String>>) requests.get(0).get("headers"));
        assertThat(headers.get("Content-Type").get(0)).startsWith("application/json");
        assertThat(headers.get("selected-user-roles")).containsExactly("admin");
        assertThat(headers.get("selected-user-key")).containsExactly(personKey);
        assertThat(headers).doesNotContainKey("selected-agent-key");
        assertThat(headers).doesNotContainKey("internal-token");
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Cookie");
    }

    @Test
    void updateInternalOrganization_targetUnprocessable_returnsTargetStatusAndBody() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/patch-organization_internal_organizations_organization_key_unprocessable_content",
                Map.of("organization_key", organizationKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "organization_status", "REJECTED",
                            "organization_rejection_reason", "INACTIVE_DOCUMENT"))
                .when()
                    .patch(PATCH_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.jsonPath().getMap("$")).isEqualTo(Map.of(
                "title", "Unprocessable Content",
                "message", "The organization status cannot be changed.",
                "code", "ORG00003",
                "extra_fields", List.of()));
    }

    @Test
    void updateInternalOrganization_keyWithEncodedHash_reachesTargetAsSingleSegment() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        MockTools.addExpectation(
                "organization-service/patch-organization_internal_organizations_organization_key",
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
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "organization_status", "REJECTED",
                            "organization_rejection_reason", "INACTIVE_DOCUMENT"))
                .when()
                    .patch("/internal/organizations/x%23fragment")
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> requests = MockTools.retrieveRequests(
                "PATCH", "/organization/internal/organizations/.+");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("queryStringParameters")).isNull();
        assertThat((String) requests.get(0).get("path")).isEqualTo(
                "/organization/internal/organizations/x%23fragment");
    }

    @Test
    void updateInternalOrganization_noCookie_returns401Gen00401WithoutCallingTarget() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/patch-organization_internal_organizations_organization_key",
                Map.of("organization_key", organizationKey));

        Response response = given()
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "organization_status", "REJECTED",
                            "organization_rejection_reason", "INACTIVE_DOCUMENT"))
                .when()
                    .patch(PATCH_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00401");
        assertThat(MockTools.retrieveRequests("PATCH", "/organization/internal/organizations/" + organizationKey))
                .isEmpty();
    }

    @Test
    void updateInternalOrganization_organizerCookie_returns403Gen00403WithoutCallingTarget() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/patch-organization_internal_organizations_organization_key",
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
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "organization_status", "REJECTED",
                            "organization_rejection_reason", "INACTIVE_DOCUMENT"))
                .when()
                    .patch(PATCH_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("PATCH", "/organization/internal/organizations/" + organizationKey))
                .isEmpty();
    }

    @Test
    void updateInternalOrganization_adminWithoutCsrfToken_returns403Gen00403WithoutCallingTarget() {
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/patch-organization_internal_organizations_organization_key",
                Map.of("organization_key", organizationKey));

        String token = TokenTools.sign("http://pulse-pass-keycloak:8080/realms/pulse-pass",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300),
                Map.of("person_key", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("admin"))));

        Response response = given()
                    .cookie("pulsepass_access_token", token)
                    .header("Accept-Language", "en")
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "organization_status", "REJECTED",
                            "organization_rejection_reason", "INACTIVE_DOCUMENT"))
                .when()
                    .patch(PATCH_INTERNAL_ORGANIZATION, organizationKey)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.jsonPath().getString("code")).isEqualTo("GEN00403");
        assertThat(MockTools.retrieveRequests("PATCH", "/organization/internal/organizations/" + organizationKey))
                .isEmpty();
    }

    @Test
    void updateInternalOrganization_targetDropsConnection_returns500Gen00500WithoutDetails() {
        String csrf = CsrfTools.fetchToken();
        MockTools.clearMocker();
        String organizationKey = UUID.randomUUID().toString();
        MockTools.addExpectation(
                "organization-service/patch-organization_internal_organizations_organization_key_dropped_connection",
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
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "organization_status", "REJECTED",
                            "organization_rejection_reason", "INACTIVE_DOCUMENT"))
                .when()
                    .patch(PATCH_INTERNAL_ORGANIZATION, organizationKey)
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
    void preflight_patch_returnsAllowedOriginCredentialsAndHeaders() {
        MockTools.clearMocker();

        Response response = given()
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "PATCH")
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
