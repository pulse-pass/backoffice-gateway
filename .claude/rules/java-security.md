---
description: JWT validation, roles, session cookies, CSRF and CORS
paths:
  - "**/api/configurations/**"
  - "**/api/controllers/**"
  - "**/infrastructure/proxies/**"
  - "src/main/resources/application*.properties"
---

# Security

## Token validation

- **BGW-100** The gateway validates the JWT locally in `SecurityConfiguration` (`spring-boot-starter-oauth2-resource-server`, stateless, no form login or basic auth); Keycloak is never called to validate a token.
- **BGW-101** The `JwtDecoder` is `NimbusJwtDecoder.withPublicKey` with the RSA public key of `app.security.jwt.public-key` (`JWT_PUBLIC_KEY`, base64 of the X.509 DER in one line), the default validators and the issuer of `app.security.jwt.issuer` (`JWT_ISSUER`).
- **BGW-102** Roles come from `realm_access.roles` of the token and become `ROLE_<ROLE>` in `JwtRolesConverter`.
- **BGW-103** Every token issued by the realm carries `person_key` (the onboarding writes the attribute at self-registration, the admin seed has it, and the mapper is in the `onboarding-service` client).
- **BGW-104** A `forward` with a `Jwt` therefore writes `selected-user-key` without checking for its absence (no defensive validation); only `legal_person_key` is optional.

## Routes and roles

- **BGW-105** Public routes: `POST /legal_persons`, `POST /auth/login`, `POST /auth/refresh`, `OPTIONS /**` and `GET /actuator/health/**`, declared once as a `RequestMatcher` (`publicRoutes`).
- **BGW-106** `publicRoutes` is used both by `permitAll()` and by `CookieBearerTokenResolver`, which returns null on them: public routes ignore the session cookie, so an expired or invalid one there is not a 401.
- **BGW-107** `POST /auth/logout` and `GET /auth/me` require only a valid token of any role (`authenticated()`), so a buyer who tried the backoffice can still end the session and read who they are.
- **BGW-108** Everything else requires `ORGANIZER` or `ADMIN`; a new public route is added explicitly to `publicRoutes`, never by loosening `anyRequest`.
- **BGW-109** Routes resolved by the token (`GET /organizations`, `GET /persons/{person_key}`, `GET /legal_persons/{legal_person_key}/collaborators`) require `hasRole("ORGANIZER")`, declared before `anyRequest()`: no token is 401 and another role is 403.
- **BGW-110** Internal routes (`/internal/**`, any method) require `hasRole("ADMIN")`, declared before `anyRequest()`: no token is 401 and an organizer or buyer is 403.

## Headers to the services

- **BGW-111** Authenticated routes receive `@AuthenticationPrincipal Jwt jwt` in the controller and pass it to `forward`.
- **BGW-112** With a `Jwt`, `forward` adds `selected-user-key` (claim `person_key`), `selected-user-roles` (roles joined by comma) and `selected-agent-key` (claim `legal_person_key`, only when present; the admin has none).
- **BGW-113** Public routes pass a null `jwt`, so no `selected-*` header goes out.
- **BGW-114** `selected-*` headers sent by the client are never copied.
- **BGW-115** `internal-token` and `Authorization` are never forwarded; the gateway never sends the `internal-token`.
- **BGW-116** The `selected-*` header names and their claim names are written in place, with no constants.

## Session cookies

- **BGW-117** The session lives in `httpOnly` cookies managed by the gateway (BFF pattern): the browser never sees the tokens.
- **BGW-118** `pulsepass_access_token`: `HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=expires_in`.
- **BGW-119** `pulsepass_refresh_token`: the same attributes, `Path=/auth`, `Max-Age=refresh_expires_in`.
- **BGW-120** Both are built in `OnboardingServiceProxy` with `ResponseCookie` (private `buildCookie`); clearing uses the same name, path and attributes with `Max-Age=0` and an empty value.
- **BGW-121** The cookie names are written in place where they are used (the proxy, `CookieBearerTokenResolver` and `@CookieValue`), with no constants.
- **BGW-122** The access token is read from the `pulsepass_access_token` cookie by `CookieBearerTokenResolver`; the `Authorization` header is not accepted (a valid Bearer without the cookie is 401).
- **BGW-123** The `BearerTokenAuthenticationFilter` is registered by hand instead of `oauth2ResourceServer(...)`, which adds every request with a bearer token to the CSRF ignore list and would switch CSRF off for every request with the cookie.

## CSRF and CORS

- **BGW-124** CSRF is on: `csrf(csrf -> csrf.spa())` (`CookieCsrfTokenRepository.withHttpOnlyFalse()` + SPA request handler; cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`), never `disable` or a matcher that skips it (`java:S4502`).
- **BGW-125** The token is deferred by default, so `CsrfCookieFilter` (registered after `CsrfFilter`) calls `getToken()` on every request: any response, a 401 included, carries the `XSRF-TOKEN` cookie, so no route exists just to fetch it.
- **BGW-126** The front gets the cookie from its first call (`GET /auth/me` on hydration) and axios sends the header on every POST.
- **BGW-127** A CSRF failure goes to the `AccessDeniedHandler` → 403 `GEN00403`; since the CSRF filter runs before authentication, a protected POST without CSRF and without session is 403, not 401.
- **BGW-128** CORS comes from `app.cors.allowed-origin` (`BACKOFFICE_APP_URL`), set in a `WebMvcConfigurer`; the security chain reuses it with `cors(Customizer.withDefaults())`, so the preflight is answered before authentication.
- **BGW-129** CORS allows credentials (`allowCredentials(true)`) with the explicit origin and the headers `Content-Type`, `Accept-Language` and `X-XSRF-TOKEN`.
