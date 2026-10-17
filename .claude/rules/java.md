---
description: Java/Spring Boot code conventions
paths:
  - "src/main/java/**/*.java"
---

# Java code

## Structure
- Java 21, Spring Boot 4.x, Maven (no Gradle, no Lombok), no Spring Cloud Gateway. Package `com.pulsepass.backofficegateway`.
- Packages: `api` (controllers, configurations, exceptions), `infrastructure/proxies` (`ServiceProxy`, `ServiceRequestException` and one folder per target service); `common` only when a language helper is needed.
- The gateway has no domain, no persistence and no schemas/DTOs: it forwards requests raw (see Forwarding).
- `common/` holds only general-purpose language helpers (string, JSON, document math), named `<Subject>Handlers` (`JsonHandlers`, `DocumentHandlers`) and placed directly in `common/`, without a `utils` subfolder. Framework concerns (HTTP clients, timeouts, Spring beans) never go to `common/`: prefer the Spring Boot configuration (properties and auto-configured beans such as `RestClient.Builder`); what is left stays in the class that uses it.
- Always reference the object's own fields with `this.`.

## Method order
- Private methods always **before** public ones, in the order they are used.
- Prefer separate, clear `if`s over compound `||` conditions when each case has its own meaning.
- No defensive validation; `try/catch` only around the call that actually throws (the HTTP call).

## Forwarding
- One `<Service>Controller` per target service (e.g. `OnboardingController`) with explicit routes, and one `<Service>Proxy extends ServiceProxy` per target service (e.g. `OnboardingServiceProxy`). The controller only calls `proxy.forward(method, path, request, body)`.
- The forward is raw, with no schema or DTO: the controller takes `HttpServletRequest` and `@RequestBody(required = false) byte[] body`; the body, the query string and the `Content-Type` and `Accept-Language` headers go to the target.
- `internal-token` and `Authorization` are never forwarded. `/<service>/internal/**` routes are never exposed.
- The response comes back as the target sent it: same status, same body (bytes) and same `Content-Type`, for any status (201, 400, 409, 415, 500...). The proxy uses `RestClient.exchange(...)`, which does not throw on 4xx/5xx.
- The gateway exposes routes without the service prefix (`POST /legal_persons`) and adds it when forwarding (`/onboarding/legal_persons`).
- The base URL comes from `app.<name>.url`, fed by the `<NAME>_URL` variable that `service-start.sh` resolves from the `<NAME>_TEST_URL` / `<NAME>_DEVELOPMENT_URL` pair. Timeouts come from `spring.http.clients.connect-timeout` and `spring.http.clients.read-timeout`, which Spring Boot applies to the auto-configured `RestClient.Builder`; proxies receive the builder injected and never build a request factory by hand. Redirects are not followed (`spring.http.clients.redirects=dont-follow`), so a 3xx of the target reaches the client as it was sent. An empty response body goes back as no body, so Spring does not invent a `Content-Type`.
- A transport failure (`RestClientException`: connection refused, DNS, timeout) is logged in the proxy (`ServiceProxy.forward - Request failed {service, method, path}`, exception as the last argument) and becomes `ServiceRequestException` → 500 `GEN00500` with the generic message. The `try` wraps only the HTTP call.
- CORS comes from `app.cors.allowed-origin` (`BACKOFFICE_APP_URL`), set in a `WebMvcConfigurer`.

## Errors
- Handlers take no `Locale` parameter; `build` reads the request language from `LocaleContextHolder.getLocale()` (fed by `Accept-Language`) to pick `messages.properties` or `messages_pt.properties`.
- Every error produced by the gateway goes through `build` and answers `ErrorResponse(title, message, code, extra_fields)`; `extra_fields` is always present, an empty list here. The gateway does not read the body, so it has no `GEN00400` or `GEN00415`; those come from the target service untouched.
- Never branch on types with `instanceof` (including pattern matching); write one method per type instead (e.g. one `@ExceptionHandler` per exception).
- Exception messages are plain literal text, no string concatenation.
- Codes: generic errors (schema validation, authentication, permission, unknown route or method, unsupported media type and unexpected failures) use `GEN` + the HTTP status (`GEN00400`, `GEN00401`, `GEN00403`, `GEN00404`, `GEN00405`, `GEN00415`, `GEN00500`). Authentication and permission exceptions (e.g. `UnauthorizedInternalRequestException`) are generic too: they use `GEN00401` / `GEN00403` and map to 401 / 403. Every other `DomainException` carries the service code `<SVC>0000N` (e.g. `ORG00001`, `ONB00006`) and maps to a 4xx status in the `STATUS_MAP`. Every `DomainException` must be mapped: the 500 default of `getOrDefault` exists only for a forgotten mapping, which is a bug. 500 is always `GEN00500` with the generic `error.internal` message. The gateway has no domain, so it only answers `GEN00404`, `GEN00405` and `GEN00500`; a transport failure to the target service is an unexpected failure (`GEN00500`).

## API
- Route paths start with the service name (`/onboarding/legal_persons`), in snake_case with underscores, never hyphens. Service-to-service routes live under `/<service>/internal/**` (e.g. `/organization/internal/organizations`) and are never exposed by the gateways. The gateways expose routes without the prefix and add it when forwarding.
- JSON in snake_case (`spring.jackson.property-naming-strategy=SNAKE_CASE`).
- Logs follow `ClassName.method - message`. The message is literal text; data goes in a single `Map` argument (`logger.error("X.y - Request failed {}", Map.of("service", name), e)`), never interpolated in the sentence. The exception is passed as the last argument, never `e.getMessage()`.
- Logs never contain the request or response body (it carries passwords) nor the query string. The only log is the transport failure in `ServiceProxy.forward`.
