package com.pulsepass.backofficegateway.infrastructure.proxies;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public abstract class ServiceProxy {

    private static final Logger logger = LoggerFactory.getLogger(ServiceProxy.class);

    private static final List<String> FORWARDED_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT_LANGUAGE
    );

    private final String serviceName;
    private final String baseUrl;
    private final RestClient restClient;

    protected ServiceProxy(String serviceName, String baseUrl, RestClient.Builder restClientBuilder) {
        this.serviceName = serviceName;
        this.baseUrl = baseUrl;
        this.restClient = restClientBuilder.build();
    }

    private URI targetUri(String path, String query) {
        if (query == null) {
            return URI.create(this.baseUrl + path);
        }
        return URI.create(this.baseUrl + path + "?" + query);
    }

    @SuppressWarnings("unchecked")
    private void copyHeaders(HttpServletRequest request, MediaType contentType, Jwt jwt, HttpHeaders headers) {
        for (String name : FORWARDED_HEADERS) {
            String value = request.getHeader(name);
            if (value != null) {
                headers.set(name, value);
            }
        }
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        if (jwt != null) {
            headers.set("selected-user-key", jwt.getClaimAsString("person_key"));
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            headers.set("selected-user-roles", String.join(",", (List<String>) realmAccess.get("roles")));
            String legalPersonKey = jwt.getClaimAsString("legal_person_key");
            if (legalPersonKey != null) {
                headers.set("selected-agent-key", legalPersonKey);
            }
        }
    }

    public ResponseEntity<byte[]> forward(
            HttpMethod method,
            String path,
            HttpServletRequest request,
            MediaType contentType,
            byte[] body,
            Jwt jwt
    ) {
        RestClient.RequestBodySpec spec = this.restClient.method(method)
                .uri(this.targetUri(path, request.getQueryString()))
                .headers(headers -> this.copyHeaders(request, contentType, jwt, headers));
        if (body != null) {
            spec.body(body);
        }

        try {
            return spec.exchange((forwardedRequest, response) -> {
                byte[] responseBody = response.getBody().readAllBytes();
                return ResponseEntity
                        .status(response.getStatusCode())
                        .contentType(response.getHeaders().getContentType())
                        .body(responseBody.length == 0 ? null : responseBody);
            });
        } catch (RestClientException ex) {
            logger.error(
                    "ServiceProxy.forward - Request failed {}",
                    Map.of("service", this.serviceName, "method", method.name(),
                            "path", path),
                    ex
            );
            throw new ServiceRequestException(ex);
        }
    }
}
