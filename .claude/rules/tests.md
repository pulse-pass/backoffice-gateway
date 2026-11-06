---
description: Unit and integration testing strategy and conventions
paths:
  - "src/test/**"
---

# Tests

## Unit tests

- **BGW-185** Unit tests (JUnit 5 + Mockito + AssertJ) in `src/test/java/.../unit/`, mirroring the main packages: one test class per production class, never grouping several classes in one test.
- **BGW-186** Test names `method_scenario_expectedResult`.
- **BGW-187** Controllers are tested with `@WebMvcTest` (MockMvc) and the proxy mocked with `@MockitoBean`.
- **BGW-188** Plain forwarding is verified with `eq(...)`, `isNull()` and `any(...)` on the arguments of `forward` (`argThat` only for the `byte[]` body).
- **BGW-189** The session routes only verify the delegation to the proxy method, plus the cases rejected before reaching it (CSRF, missing refresh cookie, no token).
- **BGW-190** Proxies are tested against `StubHttpServer`, a local HTTP server that records what it receives.
- **BGW-191** Proxy tests assert status, body and `Content-Type` passed through, `Accept-Language`/`Content-Type`/query forwarded, `internal-token` and `Authorization` absent, and connection refused and timeout → `ServiceRequestException`.
- **BGW-192** The specific proxy methods (e.g. `OnboardingServiceProxy.createSession`) are tested the same way, with a snake_case `JsonMapper`.
- **BGW-193** They assert what reached the target (path, body, `Content-Type`, `selected-*` headers) and the response built (status, body, `Set-Cookie` that write and clear the session).
- **BGW-194** Unit tests may keep constants, `@BeforeEach` setup and private helpers.
- **BGW-195** Instance fields for injected or per-test objects (`MockMvc`, mocks, a stub server opened in `@BeforeEach`) are fine in unit tests.

## Integration tests

- **BGW-196** Black-box integration tests with REST Assured against the gateway running in Docker Compose (no Testcontainers), in `src/test/java/.../integration/`, one class per route.
- **BGW-197** `BaseIntegrationTest` takes the URL and ports from environment variables: `API_BASE_URL`, `API_PORT`, `MOCKSERVER_PORT`.
- **BGW-198** Classes are `Test<Method><Action>` for routes that change something (e.g. `TestPostCreateLegalPerson`) and `Test<Method><Resource>` for GET routes, with no `Read` (e.g. `TestGetOrganizations`).
- **BGW-199** They run with `./mvnw test -Pintegration`; `./mvnw test` runs only the unit tests.
- **BGW-200** REST Assured must be 6.x: Spring Boot 4 brings Groovy 5 and Jackson 3, which REST Assured 5.x cannot use.
- **BGW-201** Integration tests are always run against the real compose (`./infrastructure/service-start.sh test` + `./mvnw test -Pintegration`) before a task is closed.
- **BGW-202** Integration tests only go through the HTTP API: never open a database connection (JDBC, `DriverManager`, SQL strings) or touch the schema to set up state.
- **BGW-203** A state that no route can produce yet (e.g. a rejected organization before the rejection route exists) is covered by unit tests, and its integration test comes with the route that produces it.
- **BGW-204** Each test asserts that status, body and `Content-Type` come back identical to what the target sent.
- **BGW-205** Integration test classes have no constants other than the URLs of the routes the class calls directly: the tested route and any other route used to check the result (e.g. `private static final String POST_LEGAL_PERSONS = "/legal_persons";`).
- **BGW-206** When a prerequisite needs another route (e.g. creating a legal person before reading it), a shared tool in `common/` makes that call, keeps that route URL and has no helper methods of any visibility.
- **BGW-207** Each test declares its own data (names, documents, tokens), builds its own payload, makes its own call (REST Assured) and writes its own assertions inline, even when that repeats code across tests.
- **BGW-208** Stateless tools (`JsonMapper`) are fine as instance fields, and `BaseIntegrationTest` keeps its shared constants and fields (URL, ports).
- **BGW-209** Request payloads in integration tests are declared in a single expression with `Map.of` and `List.of`, nested objects and lists included, never with `new HashMap<>()` plus `put`, `new ArrayList<>(List.of(...))` or JSON concatenated in a string.
- **BGW-210** Above 10 entries use `Map.ofEntries(Map.entry(...), ...)`. `HashMap` is only for a test that needs an explicit `null` value or a mutable map.
- **BGW-211** Each test generates its own data; no teardown.
- **BGW-212** Every route has success and error tests, including the transport failure (500 `GEN00500`) and the CORS preflight.
- **BGW-213** No comments in test code (`//`, `/* */`, Javadoc).

## Shared helpers

- **BGW-214** Shared helpers (`MockTools`, `TokenTools`, `CsrfTools` and, when a prerequisite needs another route, the tool that makes that call) live in `src/test/java/.../common/` and are the only reusable test code.
- **BGW-215** The expectation templates live in `src/test/resources/expectations/`.
- **BGW-216** The no-constants rule applies to them too, except for the URL and path of the route a prerequisite tool calls; stateless tools (`JsonMapper`) are fine as fields.
- **BGW-217** The methods of that prerequisite tool (e.g. `createLegalPerson(ownerDocumentNumber)`) receive the data the test needs to control, make the call and have a single assertion, on the response status, so a broken prerequisite fails at the setup line instead of later in the test.
- **BGW-218** A test never relies on seed data.
