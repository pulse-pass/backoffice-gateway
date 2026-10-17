package com.pulsepass.backofficegateway.integration;

import com.pulsepass.backofficegateway.common.MockServerHandlers;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;

public abstract class BaseIntegrationTest {

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = env("API_BASE_URL", "http://localhost");
        RestAssured.port = Integer.parseInt(env("API_PORT", "8090"));
        MockServerHandlers.configure(
                env("API_BASE_URL", "http://localhost"),
                Integer.parseInt(env("MOCKSERVER_PORT", "1090"))
        );
    }
}
