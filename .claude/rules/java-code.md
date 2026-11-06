---
description: Java code style: method order, flow, naming, ifs, catch, comments
paths:
  - "src/main/java/**/*.java"
---

# Java code

## Method order and flow

- **BGW-039** Private methods always before public ones, in the order they are used.
- **BGW-040** In controllers, public methods are grouped by resource and, inside each resource, go: create, then update, then delete, then get/list.
- **BGW-041** The flow of a public method stays inline and linear, in the method itself: never extract its branches to a private method (e.g. the status branches of `refreshSession`), even when lines repeat, and never to shorten the method.
- **BGW-042** Prefer separate, clear `if`s over compound `||` conditions when each case has its own meaning.
- **BGW-043** One `catch` per different handling: two `catch` blocks that do the same thing become a single `catch (Exception ex)`. A separate `catch` exists only when that exception has specific handling.
- **BGW-044** `try/catch` only around the call that actually throws (the HTTP call).

## Style

- **BGW-045** No defensive validation (e.g. checking that a value from the environment is not empty).
- **BGW-046** Keep it simple: compare strings with `equals`, no `MessageDigest.isEqual` or similar tricks.
- **BGW-047** No comments in Java code (Javadoc included): names and structure must explain the code, and decisions go to the rules, the README or the task notes.
- **BGW-048** Always reference the object's own fields with `this.`.

## Conversions and naming

- **BGW-049** `to*`/`from*` methods exist only in classes that represent data, converting themselves, and in the static helpers of `common/` (`toJson`).
- **BGW-050** The gateway's components (proxies, controllers, configurations, handlers) never have a `to*`/`from*` method.
- **BGW-051** A private step that checks a value is `validate*` and returns nothing; one that builds something is named after the action (`build*`, `copy*`).
