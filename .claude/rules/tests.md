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
- The target services are played by the MockServer. Each test builds its own payload with a unique value (random CNPJ, UUID), registers an expectation that matches only that value in the body, and asserts that status, body and `Content-Type` come back identical. Expectations that drop the connection must match only the body of their own test, so they never affect the others.
- Test classes have no constants and no private helper methods: each test declares its own data (names, documents, paths, tokens, timestamps), builds its own payload, makes its own call and writes its own assertions inline, even when that repeats code across tests. Instance fields for injected or per-test objects (`MockMvc`, mocks, a stub server opened in `@BeforeEach`) are fine.
- Shared helpers (`MockServerHandlers`) live in `src/test/java/.../common/` and are the only reusable test code.
- REST Assured must be 6.x: Spring Boot 4 brings Groovy 5 and Jackson 3, which REST Assured 5.x cannot use.
- Integration tests are always run against the real compose (`./infrastructure/service-start.sh test` + `./mvnw test -Pintegration`) before a task is closed.
- Each test generates its own data; no teardown.
- Every route has success and error tests, including the transport failure (500 `GEN00500`) and the CORS preflight.
