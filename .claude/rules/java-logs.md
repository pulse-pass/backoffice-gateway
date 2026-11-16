---
description: Log format, Map argument, S5145 and what never to log
paths:
  - "src/main/java/**/*.java"
---

# Logs

- **BGW-094** Logs follow `ClassName.method - message`.
- **BGW-095** The message is literal text; data goes in a single `Map` argument (`logger.error("ServiceProxy.forward - Request failed {}", Map.of("service", name, "failure", ex.getClass().getSimpleName()))`), never interpolated in the sentence.
- **BGW-096** The exception is passed as the last argument, never `e.getMessage()`, except where a gateway rule forbids passing it because its message embeds a body or a URL.
- **BGW-097** A log never receives a value that came straight from the request (path variable, query string, body), which Sonar flags as log injection (`javasecurity:S5145`) and fails the quality gate.
- **BGW-098** Logs never contain the request or response body (it carries passwords and tokens), cookies, tokens, the `Authorization` header nor the query string.
- **BGW-099** Logs never receive a DTO, schema, response or entity: only the single `Map` with the chosen fields.
- **BGW-220** `ServiceProxy.forward` never passes the exception to the logger, because `RestClientException` messages embed the target URL with the query string: it logs the service and the exception class name.
