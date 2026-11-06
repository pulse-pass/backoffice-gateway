---
description: CI, main ruleset, SonarQube and branch conventions
paths:
  - ".github/**"
---

# Pipeline

- **BGW-140** Polyrepo in the `pulse-pass` GitHub organization, `main` only.
- **BGW-141** PRs come from `feature/<ID>-<slug>`; merge commits only (squash and rebase disabled).
- **BGW-142** `main` ruleset: active on the default branch, no bypass, blocks deletion and force push.
- **BGW-143** The ruleset requires a PR (0 approvals, merge method `merge`).
- **BGW-144** The ruleset requires every CI job to pass (`lint`, `unit-tests`, `integration-tests`, `sonar`, `build`).
- **BGW-145** It can be created with `gh api -X POST repos/pulse-pass/<repo>/rulesets`.
- **BGW-146** SonarQube Cloud: organization `pulse-pass` (Free plan), project `pulse-pass_<repo>` with GitHub Actions analysis.
- **BGW-147** `SONAR_TOKEN` is an organization secret shared by all repositories.
- **BGW-148** Workflows: `pull-request.yml` (name `CI`) and `publish.yml` (name `Publish`, never "release").
- **BGW-149** Third-party actions are pinned by full commit SHA (Sonar rule S7637) with the version in a comment; `actions/*` may use tags. That version is the only comment allowed in workflows.
- **BGW-150** The `sonar` job runs with `-Dsonar.qualitygate.wait=true`, so a failed quality gate fails the job and skips `build`.
- **BGW-151** Pipeline: PR with lint (Checkstyle) → unit tests (JaCoCo) → integration tests (through `service-start.sh test`) → SonarQube Cloud → image build.
- **BGW-152** On merge: version from the commits, `vX.Y.Z` tag and image on GHCR.
