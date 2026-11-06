---
description: Test conventions for cookies, CSRF and signed tokens
paths:
  - "src/test/**"
---

# Tests of security

## Cookies and CSRF

- **BGW-165** Integration tests authenticate with the `pulsepass_access_token` cookie (token signed by `TokenTools`) and never with `Authorization`; a valid `Authorization: Bearer` without the cookie is 401.
- **BGW-166** The refresh and logout routes also need the `pulsepass_refresh_token` cookie.
- **BGW-167** Every POST goes through CSRF: the test sends the `XSRF-TOKEN` cookie plus the `X-XSRF-TOKEN` header, and `String csrf = CsrfTools.fetchToken();` is the first line of the test.
- **BGW-168** `CsrfTools.fetchToken()` calls `GET /actuator/health` (single assertion, status 200; any response carries the cookie through `CsrfCookieFilter`) and returns the `XSRF-TOKEN` cookie value.
- **BGW-169** Tests without the header, or with a different one, expect 403 `GEN00403` and no call to the MockServer.
- **BGW-170** Session responses are asserted on the `Set-Cookie` headers (`response.headers().getValues("Set-Cookie")`): name, value, `Max-Age`, `Path`, `HttpOnly`, `Secure` and `SameSite=Strict`, and 204 with no body.
- **BGW-171** A 403 for CSRF may carry an `XSRF-TOKEN` `Set-Cookie`, so do not assert that it has none.
- **BGW-172** Unit tests with MockMvc do not use `jwt()` from `spring-security-test`: it calls `CsrfFilter.skipRequest`, so CSRF is not checked and `CsrfCookieFilter` finds no token.
- **BGW-173** An authenticated request in a unit test sends a `pulsepass_access_token` cookie signed with `TokenTools.sign`.
- **BGW-174** Unit tests with MockMvc do not use `csrf()` from `spring-security-test`: it replaces the `CsrfTokenRepository` of the shared filter chain for the following tests of the same context and breaks the ones that check the `XSRF-TOKEN` cookie.
- **BGW-175** Unit tests send a literal `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header through a private `RequestPostProcessor`.

## Tokens

- **BGW-176** The test key pair is the fixed development pair of the Keycloak realm: the private key is `src/test/resources/keys/jwt-private-key.pem` and the public key is in `JWT_PUBLIC_KEY` (`.env.example`) and in `src/test/resources/security-test.properties`.
- **BGW-177** Tests sign RS256 tokens with Nimbus through `TokenTools` (`common/`): `sign(issuer, issuedAt, expiresAt, claims)` signs with the test key and `sign(signingKey, ...)` with another key.
- **BGW-178** `signHmac(secret, ...)` signs HS256, `unsigned(...)` builds an `alg=none` token (algorithm-confusion regressions) and `otherPrivateKey()` generates a key the gateway does not trust.
- **BGW-179** `TokenTools` has no data: each test passes its issuer (`http://pulse-pass-keycloak:8080/realms/pulse-pass`), timestamps and claims (`person_key`, `legal_person_key`, `realm_access.roles`), so odd tokens are built inline in the test.
- **BGW-180** Unit tests of controllers import `SecurityConfiguration` and `SecurityErrorHandler`, with `@TestPropertySource(locations = "classpath:security-test.properties")`.
- **BGW-181** `SecurityConfigurationTest` uses real signed tokens, sent in the access cookie, to exercise the decoder.
- **BGW-182** Every protected route is tested without token, tampered, expired, with another key, with the wrong issuer, with a role that is not allowed (403) and with each allowed role.
- **BGW-183** Routes open to any valid token (`POST /auth/logout`, `GET /auth/me`) have no role-based 403 case; only unknown routes and wrong-method calls (e.g. `GET /auth/logout`) do.
- **BGW-184** A public route is tested to work without any token and with an expired or tampered access cookie.
