---
description: Testing strategy and conventions
paths:
  - "src/test/**"
---

# Tests

- Unit tests (JUnit 5 + Mockito + AssertJ) in `src/test/java/.../unit/`, mirroring the main packages: **one test class per production class**, never grouping several classes in one test.
- Test names `method_scenario_expectedResult`.
- Controllers are tested with `@WebMvcTest` (MockMvc) and the proxy mocked with `@MockitoBean`; proxies are tested against `StubHttpServer`, a local HTTP server that records what it receives (status, body and `Content-Type` passed through, `Accept-Language`/`Content-Type`/query forwarded, `internal-token` and `Authorization` absent, connection refused and timeout → `ServiceRequestException`).
- Black-box integration tests with REST Assured against the gateway running in Docker Compose (no Testcontainers), in `src/test/java/.../integration/`: `BaseIntegrationTest` (URL and ports from environment variables: `API_BASE_URL`, `API_PORT`, `MOCKSERVER_PORT`) and one class per route `Test<Method><Action>` (e.g. `TestPostCreateLegalPerson`). They run with `./mvnw test -Pintegration`; `./mvnw test` runs only the unit tests.
- The target services are played by the MockServer (see MockServer below). Each test asserts that status, body and `Content-Type` come back identical; a dropped connection is a template variant with `httpError` (`dropConnection`) instead of `httpResponse`.
- Integration test classes have no constants other than the route URL (e.g. `private static final String POST_LEGAL_PERSONS = "/legal_persons";`) and no helper methods of any visibility: each test declares its own data (names, documents, paths, tokens, timestamps), builds its own payload, makes its own call (REST Assured) and writes its own assertions inline, even when that repeats code across tests. Stateless tools (`JsonMapper`) are fine as instance fields, and `BaseIntegrationTest` keeps its shared constants and fields (URL, ports). Unit tests may keep constants, `@BeforeEach` setup and private helpers; instance fields for injected or per-test objects (`MockMvc`, mocks, a stub server opened in `@BeforeEach`) are fine.
- Shared helpers (`MockTools`) live in `src/test/java/.../common/`, plus the expectation templates in `src/test/resources/expectations/`, and are the only reusable test code. The no-constants rule applies to them too; stateless tools (`JsonMapper`) are fine as fields, and `MockTools` keeps only its configurable address (see MockServer).
- REST Assured must be 6.x: Spring Boot 4 brings Groovy 5 and Jackson 3, which REST Assured 5.x cannot use.
- Integration tests are always run against the real compose (`./infrastructure/service-start.sh test` + `./mvnw test -Pintegration`) before a task is closed.
- Each test generates its own data; no teardown.
- Every route has success and error tests, including the transport failure (500 `GEN00500`) and the CORS preflight.
- No comments in test code (`//`, `/* */`, Javadoc).

## MockServer
- Target services are played by the MockServer of the compose, driven by `MockTools` in `src/test/java/.../common/`: `clearMocker()`, `addExpectation(mockCode)`, `addExpectation(mockCode, pathParameters)`, `addExpectation(mockCode, pathParameters, expectationChanges)` and `retrieveRequests(method, path)`.
- Each external route has a default expectation in `src/test/resources/expectations/<api>/<method>-<name>.json`, in the MockServer format: `httpRequest` with `{param}` placeholders in the path and `httpResponse` with a realistic default body. Other responses of the same route are variants in their own file (`<method>-<name>_<variant>.json`, e.g. `onboarding-service/post-legal_persons_server_error.json`).
- `addExpectation` loads the template, sets `pathParameters` on the request and applies `expectationChanges` to the response: `payload` overrides keys of the body, `status_code` the status and `headers` the headers.
- Every integration test starts with `MockTools.clearMocker()` and registers only the expectations it needs, with the values (ids, keys, tokens) declared in the test itself. No expectations in `@BeforeAll`, none shared between tests, and no `priority` or body matching to tell tests apart. Integration tests run sequentially because they share the MockServer.
- `MockTools` has no route-specific methods and no constants: paths live in the templates, and tests write the external paths inline in `retrieveRequests`.
