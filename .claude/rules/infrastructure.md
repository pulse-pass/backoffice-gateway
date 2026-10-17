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
- `infrastructure/` holds `docker-compose.yml`, `.env.example`, `service-start.sh`, `service-stop.sh` and `MockServerHealthcheck.java` (the MockServer image has no shell or curl, so its healthcheck runs this file with `java`). The gateway has no database: the compose has only the gateway API, the MockServer (which replaces the target services in `test` mode) and the external `pulse-pass` network.
- The compose fixes the project name (`name: backoffice-gateway`), so the composes of different repositories never take down each other's containers.
- The mode comes from the argument `./infrastructure/service-start.sh [test|development]` (default `test`), never from editing `.env`: each target service has a `<X>_TEST_URL` / `<X>_DEVELOPMENT_URL` pair in `.env` (e.g. `ONBOARDING_SERVICE_TEST_URL` pointing to the MockServer, `ONBOARDING_SERVICE_DEVELOPMENT_URL` to `onboarding-service-api`), and the script exports `<X>_URL`. The MockServer runs under the `test` profile and the API depends on it (`required: false`, so the `development` mode still starts). Every new target service adds its pair to `.env.example` and its `<X>_URL` to the API `environment` in the compose.
- The start script creates the `pulse-pass` network if missing, copies `.env.example` on first run and tears down the previous environment; `service-stop.sh` removes containers, volumes and locally built images.
- Redis, Kafka, Kafka UI, Keycloak, Mailpit and MinIO live in the `pulse-pass-infrastructure` repository.
- README: running the gateway only needs Docker (the image builds with its own JDK); Java 21 is only needed to run `./mvnw` (lint and tests) outside the container.
- Workflows: `pull-request.yml` (name `CI`) and `publish.yml` (name `Publish`, never "release"). Third-party actions are pinned by full commit SHA with the version in a comment (Sonar rule S7637); `actions/*` may use tags. The `sonar` job runs with `-Dsonar.qualitygate.wait=true`, so a failed quality gate fails the job and skips `build`.
- Pipeline: PR with lint (Checkstyle) → unit tests (JaCoCo) → integration tests (through `service-start.sh test`) → SonarQube Cloud → image build; on merge, version from the commits, `vX.Y.Z` tag and image `ghcr.io/pulse-pass/backoffice-gateway` on GHCR.
