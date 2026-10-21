package com.pulsepass.backofficegateway.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

class TestGetReadHealth extends BaseIntegrationTest {

    private static final String GET_HEALTH = "/actuator/health";

    @Test
    void readHealth_noToken_returns200Up() {
        Response response = given().when().get(GET_HEALTH).then().extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
    }

    @Test
    void readHealth_invalidBearerToken_isIgnored() {
        Response response = given()
                    .header("Authorization", "Bearer not-a-jwt")
                .when()
                    .get(GET_HEALTH)
                .then()
                    .extract().response();

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
