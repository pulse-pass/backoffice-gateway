---
description: Routes, JSON, internal routes and pagination of the gateway
paths:
  - "**/api/**"
  - "src/main/resources/application*.properties"
---

# API

- **BGW-027** Route paths in snake_case with underscores, never hyphens.
- **BGW-028** Service-to-service routes live under `/<service>/internal/**` (e.g. `/organization/internal/organizations`).
- **BGW-029** The gateways expose routes without the service prefix and add it when forwarding.
- **BGW-030** The `backoffice-gateway` also exposes some internal routes, without the service prefix as `/internal/**` (e.g. `/internal/organizations`), only to the admin role; no other gateway exposes them.
- **BGW-031** The `/<service>/internal/**` routes of the services are exposed under `/internal/**` without the service prefix (e.g. `GET /internal/organizations` → `/organization/internal/organizations`).
- **BGW-032** Internal routes are forwarded with the `Jwt`, so the service receives `selected-user-roles` with `admin` and accepts it in place of the `internal-token`.
- **BGW-033** Controller methods of internal routes are named `listInternal*`/`readInternal*` (GET), `updateInternal*` (PATCH) and `deleteInternal*` (DELETE).
- **BGW-034** `PATCH /internal/organizations/{organizationKey}` forwards the client body with `MediaType.APPLICATION_JSON`.
- **BGW-035** `DELETE /internal/organizations/{organizationKey}` forwards no body, and the service's 204 comes back as 204 with no body and no `Content-Type`.
- **BGW-036** Internal write routes need the CSRF token like any authenticated write.
- **BGW-037** JSON in snake_case.
- **BGW-038** List routes follow the services' `PaginatedResponse<T>(data, pagination{page, page_size})` and are forwarded unchanged, query string (`page`, `page_size`) included.
