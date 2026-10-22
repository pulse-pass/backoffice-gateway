package com.pulsepass.backofficegateway.common;

import static io.restassured.RestAssured.given;

public final class CsrfTools {

    private CsrfTools() {
    }

    public static String fetchToken() {
        return given()
            .when()
                .get("/actuator/health")
            .then()
                .statusCode(200)
                .extract().cookie("XSRF-TOKEN");
    }
}
