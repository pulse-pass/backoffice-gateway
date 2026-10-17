package com.pulsepass.backofficegateway.common;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MockServerHandlers {

    public static final String LEGAL_PERSONS_PATH = "/onboarding/legal_persons";

    private static String baseUri = "http://localhost";
    private static int port = 1090;

    private MockServerHandlers() {
    }

    private static Map<String, Object> request(String method, String path, String bodyContent) {
        Map<String, Object> request = new HashMap<>();
        request.put("method", method);
        request.put("path", path);
        request.put("body", Map.of("type", "REGEX", "regex", "(?s).*" + bodyContent + ".*"));
        return request;
    }

    private static void expect(Map<String, Object> request, String responseKey, Object response) {
        given()
                .baseUri(baseUri).port(port)
                .contentType(ContentType.JSON)
                .body(Map.of("httpRequest", request, responseKey, response))
            .when()
                .put("/mockserver/expectation")
            .then()
                .statusCode(201);
    }

    public static void configure(String newBaseUri, int newPort) {
        baseUri = newBaseUri;
        port = newPort;
    }

    public static void expectResponse(
            String method,
            String path,
            String bodyContent,
            int status,
            String contentType,
            String responseBody
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", status);
        response.put("headers", Map.of("Content-Type", List.of(contentType)));
        response.put("body", responseBody);
        expect(request(method, path, bodyContent), "httpResponse", response);
    }

    public static void expectDroppedConnection(String method, String path, String bodyContent) {
        expect(request(method, path, bodyContent), "httpError", Map.of("dropConnection", true));
    }

    public static int countRequests(String method, String path, String bodyContent) {
        return retrieveRequests(method, path, bodyContent).size();
    }

    public static boolean verifyReceived(String method, String path, String bodyContent) {
        int status = given()
                .baseUri(baseUri).port(port)
                .contentType(ContentType.JSON)
                .body(Map.of("httpRequest", request(method, path, bodyContent), "times", Map.of("atLeast", 1)))
                .when()
                .put("/mockserver/verify")
                .then()
                .extract().statusCode();
        return status == 202;
    }

    public static List<Map<String, Object>> retrieveRequests(String method, String path, String bodyContent) {
        Response response = given()
                .baseUri(baseUri).port(port)
                .queryParam("type", "REQUESTS")
                .contentType(ContentType.JSON)
                .body(request(method, path, bodyContent))
                .when()
                .put("/mockserver/retrieve")
                .then()
                .statusCode(200)
                .extract().response();
        return response.jsonPath().getList("$");
    }
}
