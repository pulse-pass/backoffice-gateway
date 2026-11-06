---
description: Local infrastructure, Docker and scripts conventions
paths:
  - "infrastructure/**"
  - "Dockerfile"
  - "README.md"
  - "src/main/resources/application*.properties"
---

# Infrastructure

- **BGW-001** `infrastructure/` holds `docker-compose.yml`, `.env.example`, `service-start.sh` and `service-stop.sh`, plus the external `pulse-pass` network.
- **BGW-002** `MockServerHealthcheck.java` also lives in `infrastructure/`: the MockServer image has no shell or curl, so its healthcheck runs this file with `java`.
- **BGW-003** The gateway has no database, Redis or Kafka, so the scripts have no infrastructure check.
- **BGW-004** The compose declares `name: backoffice-gateway-test` and gives every variable the scripts export (`SERVICE_PREFIX`, `PUBLISHED_API_PORT`, `ONBOARDING_SERVICE_URL`, `ORGANIZATION_SERVICE_URL`, `MOCKSERVER_PORT`) a `test` mode default.
- **BGW-005** Because of those defaults, `docker compose` without the scripts resolves to test mode (the CI log step relies on it); the scripts still pass `-p` and export those variables for the chosen mode.
- **BGW-006** The service keys of the compose are generic (`api`, `mockserver`) and the real name comes from `container_name` with `SERVICE_PREFIX`, because the key becomes a DNS alias on the `pulse-pass` network and both modes run together.
- **BGW-007** Always address another container by its `container_name`, never by the compose key.
- **BGW-008** `test` mode (`./infrastructure/service-start.sh test`, default, used by the CI) is self-sufficient: project `backoffice-gateway-test`, containers `backoffice-gateway-test-api` and `backoffice-gateway-test-mockserver` (profile `test`).
- **BGW-009** In `test` mode the API listens on `API_PORT` (8090) and the MockServer on `MOCKSERVER_PORT` (1090).
- **BGW-010** The MockServer plays the target services: `ONBOARDING_SERVICE_TEST_URL` and `ORGANIZATION_SERVICE_TEST_URL` are both `http://backoffice-gateway-test-mockserver:1080`.
- **BGW-011** `development` mode starts only the API: project `backoffice-gateway`, container `backoffice-gateway-api` (the name other apps use), published on `DEVELOPMENT_API_PORT` (8190).
- **BGW-012** In `development` mode the API calls the real services by container name (`http://onboarding-service-api:8080`, `http://organization-service-api:8080`), so they must be running in `development` mode.
- **BGW-013** Both modes can run at the same time (distinct projects, container names and ports).
- **BGW-014** `service-stop.sh [test|development]` (default `test`) tears down only that mode's project (`down -v --remove-orphans --rmi local`); `service-start.sh <mode>` calls it for the same mode first.
- **BGW-015** The ports 3306, 6379, 9094 and 8180 belong to the shared infrastructure and the service must not publish them.
- **BGW-016** The mode comes from the argument of the scripts, never from editing `.env`.
- **BGW-017** Each external dependency has a `<X>_TEST_URL` / `<X>_DEVELOPMENT_URL` pair in `.env`, and the script exports `<X>_URL`; the test URLs point to the `<repo>-test-mockserver` container.
- **BGW-018** Every new target service adds its `<X>_TEST_URL` / `<X>_DEVELOPMENT_URL` pair to `.env.example` and its `<X>_URL` to the API `environment` in the compose.
- **BGW-019** The start script creates the `pulse-pass` network if missing and copies `.env.example` on first run.
- **BGW-020** An `infrastructure/.env` created before this layout must be deleted so it is recreated from `.env.example`.
- **BGW-021** `JWT_PUBLIC_KEY` and `JWT_ISSUER` in `.env.example` are the same in both modes: the fixed development key pair of the Keycloak realm (`pulse-pass-infrastructure`) and its issuer; the integration tests sign tokens with the matching private key.
- **BGW-022** `env_file: .env` already passes `JWT_PUBLIC_KEY` and `JWT_ISSUER` to the `api` container.
- **BGW-023** Redis, Kafka, Kafka UI, Keycloak, Mailpit and MinIO live in the `pulse-pass-infrastructure` repository.
- **BGW-024** README: running the service only needs Docker (the image builds with its own JDK); Java 21 is only needed to run `./mvnw` (lint and tests) outside the container.
- **BGW-025** No comments in `infrastructure/`, `Dockerfile` or `application.properties`: names and structure must explain them, and decisions go to the rules or the README.
- **BGW-026** Generated or third-party files are kept as they come, comments included (`mvnw`, `checkstyle.xml`).
