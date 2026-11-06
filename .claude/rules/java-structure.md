---
description: Stack, packages, shared code and common/
paths:
  - "src/main/java/**/*.java"
---

# Java structure

- **BGW-130** Java 21, Spring Boot 4.x, Maven (no Gradle, no Lombok).
- **BGW-131** Package `com.pulsepass.backofficegateway`.
- **BGW-132** No Spring Cloud Gateway.
- **BGW-133** Packages: `api` (controllers, configurations, exceptions) and `infrastructure/proxies` (`ServiceProxy`, `ServiceRequestException` and one folder per target service, with its `dtos` subfolder); `common` only when a language helper is needed.
- **BGW-134** The gateway has no domain and no persistence, and it forwards requests raw (see java-forwarding).
- **BGW-135** DTOs exist only for the formats the gateway reads from or writes to a target service, in `infrastructure/proxies/<service>/dtos`, named `<Provider><Action><Resource>Request/Response` (provider = target service, e.g. `OnboardingServiceCreateSessionResponse`).
- **BGW-136** Code the gateway shares with the services (`ErrorResponse`, the generic error codes and messages of `GlobalExceptionHandler`) matches them except for the package.
- **BGW-137** A change to one of them is applied to every repository that has it in the same task.
- **BGW-138** `common/` holds only general-purpose language helpers (string, JSON, document math), named `<Subject>Handlers` (`JsonHandlers`, `DocumentHandlers`) and placed directly in `common/`, without a `utils` subfolder.
- **BGW-139** Framework concerns (HTTP clients, timeouts, Spring beans) never go to `common/`: prefer the Spring Boot configuration (properties and auto-configured beans such as `RestClient.Builder`); what is left stays in the class that uses it.
