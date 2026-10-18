package com.pulsepass.backofficegateway.common;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public final class MockTools {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static String baseUri = "http://localhost";
    private static int port = 1090;

    private MockTools() {
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildExpectation(
            String mockCode,
            Map<String, String> pathParameters,
            Map<String, Object> expectationChanges
    ) {
        Map<String, Object> expectation = loadDefaultExpectation(mockCode);
        Map<String, Object> httpRequest = (Map<String, Object>) expectation.get("httpRequest");
        Map<String, Object> httpResponse = (Map<String, Object>) expectation.get("httpResponse");
        if (!pathParameters.isEmpty()) {
            Map<String, List<String>> parameters = new HashMap<>();
            pathParameters.forEach((name, value) -> parameters.put(name, List.of(value)));
            httpRequest.put("pathParameters", parameters);
        }
        if (expectationChanges.containsKey("payload")) {
            Map<String, Object> body = (Map<String, Object>) httpResponse
                    .computeIfAbsent("body", key -> new HashMap<String, Object>());
            body.putAll((Map<String, Object>) expectationChanges.get("payload"));
        }
        if (expectationChanges.containsKey("status_code")) {
            httpResponse.put("statusCode", expectationChanges.get("status_code"));
        }
        if (expectationChanges.containsKey("headers")) {
            Map<String, Object> headers = (Map<String, Object>) httpResponse
                    .computeIfAbsent("headers", key -> new HashMap<String, Object>());
            headers.putAll((Map<String, Object>) expectationChanges.get("headers"));
        }
        return expectation;
    }

    private static Map<String, Object> loadDefaultExpectation(String mockCode) {
        try (InputStream stream = MockTools.class.getResourceAsStream("/expectations/" + mockCode + ".json")) {
            Objects.requireNonNull(stream, "Expectation template not found");
            return MAPPER.readValue(stream, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Expectation template not found", e);
        }
    }

    public static void configure(String newBaseUri, int newPort) {
        baseUri = newBaseUri;
        port = newPort;
    }

    public static void clearMocker() {
        given()
                .baseUri(baseUri).port(port)
            .when()
                .put("/mockserver/reset")
            .then()
                .statusCode(200);
    }

    public static void addExpectation(String mockCode) {
        addExpectation(mockCode, Map.of(), Map.of());
    }

    public static void addExpectation(String mockCode, Map<String, String> pathParameters) {
        addExpectation(mockCode, pathParameters, Map.of());
    }

    public static void addExpectation(
            String mockCode,
            Map<String, String> pathParameters,
            Map<String, Object> expectationChanges
    ) {
        given()
                .baseUri(baseUri).port(port)
                .contentType(ContentType.JSON)
                .body(buildExpectation(mockCode, pathParameters, expectationChanges))
            .when()
                .put("/mockserver/expectation")
            .then()
                .statusCode(201);
    }

    public static List<Map<String, Object>> retrieveRequests(String method, String path) {
        return given()
                .baseUri(baseUri).port(port)
                .queryParam("type", "REQUESTS")
                .contentType(ContentType.JSON)
                .body(Map.of("method", method, "path", path))
            .when()
                .put("/mockserver/retrieve")
            .then()
                .statusCode(200)
                .extract().jsonPath().getList("$");
    }
}
