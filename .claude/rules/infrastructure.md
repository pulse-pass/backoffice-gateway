---
description: Local infrastructure, Docker and pipeline conventions
paths:
  - "infrastructure/**"
  - "Dockerfile"
  - ".github/**"
  - "README.md"
---

# Infrastructure and pipeline

- Polyrepo in the `pulse-pass` GitHub organization, `main` only, PRs from `feature/<ID>-<slug>`, merge commits only (squash and rebase disabled).
- `main` ruleset: active on the default branch, no bypass, blocks deletion and force push, requires a PR (0 approvals, merge method `merge`) and requires **every** CI job to pass (`lint`, `unit-tests`, `integration-tests`, `sonar`, `build`). It can be created with `gh api -X POST repos/pulse-pass/<repo>/rulesets`.
- SonarQube Cloud: organization `pulse-pass` (Free plan), project `pulse-pass_backoffice-gateway` with GitHub Actions analysis; `SONAR_TOKEN` is an organization secret shared by all repositories.
- `infrastructure/` holds `docker-compose.yml`, `.env.example`, `service-start.sh`, `service-stop.sh` and `MockServerHealthcheck.java` (the MockServer image has no shell or curl, so its healthcheck runs this file with `java`), plus the external `pulse-pass` network. The gateway has no database, Redis or Kafka.
- The compose declares `name: backoffice-gateway-test` and defaults with the test values in every variable the scripts export (`${SERVICE_PREFIX:-backoffice-gateway-test}`, `${PUBLISHED_API_PORT:-8090}`, `ONBOARDING_SERVICE_URL`, `${MOCKSERVER_PORT:-1090}`), so a manual `docker compose` and the CI logs step behave as `test` mode; the scripts still pass `-p` and export those variables per mode. The service keys of the compose are generic (`api`, `mockserver`) and the real name comes from `container_name` with `SERVICE_PREFIX`, because the key becomes a DNS alias on the `pulse-pass` network and both modes run together. Always address another container by its `container_name`, never by its compose key.
- `test` mode (`./infrastructure/service-start.sh test`, default, used by the CI) is self-sufficient: project `backoffice-gateway-test`, containers `backoffice-gateway-test-api` and `backoffice-gateway-test-mockserver` (profile `test`, with the Java healthcheck), API on `API_PORT` (8090) and MockServer on `MOCKSERVER_PORT` (1090). The MockServer plays the target services (`ONBOARDING_SERVICE_TEST_URL=http://backoffice-gateway-test-mockserver:1080`).
- `development` mode starts only the API: project `backoffice-gateway`, container `backoffice-gateway-api` (the name other apps use), published on `DEVELOPMENT_API_PORT` (8190), calling the real services by container name (`ONBOARDING_SERVICE_DEVELOPMENT_URL=http://onboarding-service-api:8080`), so the target services must be running in `development` mode. There is no infrastructure check because the gateway has no database or broker.
- Both modes can run at the same time (distinct projects, container names and ports). `service-stop.sh [test|development]` (default `test`) tears down only that mode's project (`down -v --remove-orphans --rmi local`); `service-start.sh <mode>` calls it for the same mode first.
- The ports 3306, 6379, 9094 and 8180 belong to the shared infrastructure and the gateway must not publish them.
- The mode comes from the argument of the scripts, never from editing `.env`: each target service has a `<X>_TEST_URL` / `<X>_DEVELOPMENT_URL` pair in `.env`, and the script exports `<X>_URL`. The test URLs point to the `backoffice-gateway-test-mockserver` container. Every new target service adds its pair to `.env.example` and its `<X>_URL` to the API `environment` in the compose.
- The start script creates the `pulse-pass` network if missing and copies `.env.example` on first run. An `infrastructure/.env` created before this layout must be deleted so it is recreated from `.env.example`.
- `JWT_PUBLIC_KEY` and `JWT_ISSUER` in `.env.example` are the same in both modes: the fixed development key pair of the Keycloak realm (`pulse-pass-infrastructure`) and its issuer; the integration tests sign tokens with the matching private key. `env_file: .env` already passes them to the `api` container.
- Redis, Kafka, Kafka UI, Keycloak, Mailpit and MinIO live in the `pulse-pass-infrastructure` repository.
- README: running the gateway only needs Docker (the image builds with its own JDK); Java 21 is only needed to run `./mvnw` (lint and tests) outside the container.
- Workflows: `pull-request.yml` (name `CI`) and `publish.yml` (name `Publish`, never "release"). Third-party actions are pinned by full commit SHA with the version in a comment (Sonar rule S7637); `actions/*` may use tags. The `sonar` job runs with `-Dsonar.qualitygate.wait=true`, so a failed quality gate fails the job and skips `build`.
- Pipeline: PR with lint (Checkstyle) → unit tests (JaCoCo) → integration tests (through `service-start.sh test`) → SonarQube Cloud → image build; on merge, version from the commits, `vX.Y.Z` tag and image `ghcr.io/pulse-pass/backoffice-gateway` on GHCR.
- No comments in `infrastructure/`, `Dockerfile`, workflows or `application.properties`: names and structure must explain them, and decisions go to the rules or the README. The only exceptions are the version next to a workflow action pinned by SHA and generated or third-party files kept as they come (`mvnw`, `checkstyle.xml`).
