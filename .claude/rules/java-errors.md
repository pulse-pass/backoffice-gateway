---
description: Errors: handler, generic codes, security errors and extra_fields
paths:
  - "**/exceptions/**"
  - "src/main/resources/messages*.properties"
---

# Errors

- **BGW-052** Handlers take no `Locale` parameter; `build` reads the request language from `LocaleContextHolder.getLocale()` (fed by `Accept-Language`) to pick `messages.properties` or `messages_pt.properties`.
- **BGW-053** Every error goes through `build` and answers `ErrorResponse(title, message, code, extra_fields)`; `extra_fields` is always present, an empty list when there is nothing to add.
- **BGW-054** Never branch on types with `instanceof` (including pattern matching); write one method per type instead (e.g. one `@ExceptionHandler` per exception).
- **BGW-055** Exception messages are plain literal text (e.g. `"Request to the target service failed"`), no string concatenation.
- **BGW-056** Generic errors (authentication, permission, unknown route or method and unexpected failures) use `GEN` + the HTTP status (`GEN00401`, `GEN00403`, `GEN00404`, `GEN00405`, `GEN00500`).
- **BGW-057** The gateway has no domain, so it answers no other code; schema validation and unsupported media type errors come from the target service untouched, because the gateway does not read the body.
- **BGW-058** A missing required cookie (`MissingRequestCookieException`, e.g. the refresh cookie) answers 401 `GEN00401` without calling the proxy.
- **BGW-059** A transport failure to a target service (`ServiceRequestException`) is an unexpected failure: 500 `GEN00500` with the generic message.
- **BGW-060** 500 is always `GEN00500` with the generic `error.internal` message.
- **BGW-061** A missing, malformed, expired, wrong-issuer or wrongly signed token is 401 `GEN00401` (`error.unauthorized`) and a valid token without the role is 403 `GEN00403` (`error.forbidden`).
- **BGW-062** Both are written by `SecurityErrorHandler` (`AuthenticationEntryPoint` + `AccessDeniedHandler`) with the same `ErrorResponse` and `MessageSource` as the rest of the gateway, because they happen in the filter chain, before the controller advice.
