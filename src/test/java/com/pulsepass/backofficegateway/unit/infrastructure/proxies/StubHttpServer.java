package com.pulsepass.backofficegateway.unit.infrastructure.proxies;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class StubHttpServer implements AutoCloseable {

    public record Response(int status, Map<String, String> headers, String body) {

        public static Response of(int status, String body) {
            return new Response(status, Map.of("Content-Type", "application/json"), body);
        }

        public static Response empty(int status) {
            return new Response(status, Map.of(), "");
        }
    }

    public record Received(String method, String path, String query, Map<String, String> headers, String body) {
    }

    private final HttpServer server;
    private final Map<String, Response> routes = new ConcurrentHashMap<>();
    private final Map<String, Long> delays = new ConcurrentHashMap<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();

    public StubHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
        received.add(new Received(exchange.getRequestMethod(), path, query, headers, body));

        sleep(delays.getOrDefault(exchange.getRequestMethod() + " " + path, 0L));
        Response response = routes.getOrDefault(exchange.getRequestMethod() + " " + path, Response.empty(404));
        response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
        if (payload.length == 0) {
            exchange.sendResponseHeaders(response.status(), -1);
        } else {
            exchange.sendResponseHeaders(response.status(), payload.length);
            exchange.getResponseBody().write(payload);
        }
        exchange.close();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void on(String method, String path, Response response) {
        routes.put(method + " " + path, response);
    }

    public void delay(String method, String path, long millis) {
        delays.put(method + " " + path, millis);
    }

    public List<Received> received() {
        return received;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
