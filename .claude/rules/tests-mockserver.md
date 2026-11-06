---
description: MockServer expectations and MockTools
paths:
  - "src/test/**"
---

# Tests with MockServer

- **BGW-153** External services are played by the MockServer of the compose, driven by `MockTools` in `src/test/java/.../common/`.
- **BGW-154** `MockTools` offers `clearMocker()`, `addExpectation(mockCode)`, `addExpectation(mockCode, pathParameters)`, `addExpectation(mockCode, pathParameters, expectationChanges)` and `retrieveRequests(method, path)`.
- **BGW-155** Each external route has a default expectation in `src/test/resources/expectations/<api>/<method>-<name>.json`, in the MockServer format: `httpRequest` with `{param}` placeholders in the path and `httpResponse` with a realistic default body.
- **BGW-156** Other responses of the same route are variants in their own file (`<method>-<name>_<variant>.json`, e.g. `onboarding-service/post-legal_persons_server_error.json`).
- **BGW-157** A dropped connection is a template variant with `httpError` (`dropConnection`) instead of `httpResponse`.
- **BGW-158** `addExpectation` loads the template, sets `pathParameters` on the request and applies `expectationChanges` to the response: `payload` overrides keys of the body, `status_code` the status and `headers` the headers.
- **BGW-159** Every integration test starts with `MockTools.clearMocker()` and registers only the expectations it needs, with the values (ids, keys, tokens) declared in the test itself.
- **BGW-160** No expectations in `@BeforeAll`, none shared between tests, and no `priority` or body matching to tell tests apart.
- **BGW-161** Integration tests run sequentially because they share the MockServer.
- **BGW-162** When a prerequisite needs another route, the methods of its tool register on the MockServer the expectations of the route they call, and clear the MockServer before registering their own expectations.
- **BGW-163** A test that uses a prerequisite tool calls it first and only then its own `MockTools.clearMocker()`, followed by its expectations (the prerequisite call, `MockTools.clearMocker()`, `MockTools.addExpectation(...)`), so the setup expectations never answer the calls of the test.
- **BGW-164** `MockTools` has no route-specific methods and no constants other than its configurable address: paths live in the templates, and tests write the external paths inline in `retrieveRequests`.
