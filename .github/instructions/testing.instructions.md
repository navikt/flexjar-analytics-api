---
applyTo: "**/*.test.{ts,tsx,kt,kts}"
---

# Testing Standards (flexjar-analytics-api)

This repo is Kotlin/Ktor-only. Tests use **Kotest** + Ktor’s `testApplication {}` and Testcontainers for PostgreSQL.

## How to Run

```bash
./gradlew test
```

## Test Structure

Prefer Kotest `FunSpec` (matches existing tests).

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExampleTest : FunSpec({
    test("example") {
        1 + 1 shouldBe 2
    }
})
```

## Ktor Route Tests

Use `testApplication` and the repo’s `testModule()` helper (bypasses Texas auth by installing a test bearer realm).

```kotlin
testApplication {
    application { testModule() }

    val response = client.get("/internal/isAlive")
    response.status shouldBe HttpStatusCode.OK
}
```

## Database Tests (Testcontainers + Flyway)

This repo already provides:
- `TestDatabase` (starts Postgres 17 testcontainer, runs Flyway)
- `DatabaseHolder.initializeForTesting(...)`

Patterns:

```kotlin
beforeSpec { TestDatabase.initialize() }
beforeTest { TestDatabase.clearAllData() }
```

✅ Always:
- Clear tables between tests for isolation.
- Run migrations via Flyway (do not hand-create schema in tests).

## Authentication in Tests

Production uses **NAIS Texas introspection**. Tests should not call Texas.

- Prefer `testModule()` which creates a `BrukerPrincipal` for any bearer token.
- Test authorization behavior via group membership and the `TeamAuthorizationPlugin`.

## Coverage Expectations (pragmatic)

- New repository queries: cover happy path + edge cases (empty, pagination, filters).
- Routes: cover 401 (no auth) + 200 with auth.
- Sensitive data redaction: add regression tests for new patterns.

### What to Cover

- **Routes**: 401/403 vs 200/404
- **Repositories**: CRUD + filtering + sorting/pagination
- **Migrations**: run cleanly in Testcontainers
- **PII redaction**: expected replacements

## Test Naming

```kotlin
// ✅ Good - describes behavior
`should create user when valid data provided`
`should throw exception when email is invalid`
`should return 401 without auth`
`should return paginated feedback results`

// ❌ Bad - not descriptive
`test1`
`createUserTest`
`testValidation`
```

## Boundaries

### ✅ Always

- Write tests for new code before committing
- Test both success and error cases
- Use descriptive test names
- Clean up test data after each test
- Run `./gradlew test` before pushing

### ⚠️ Ask First

- Changing test framework or structure
- Adding complex test fixtures
- Modifying shared test utilities
- Disabling or skipping tests

### 🚫 Never

- Commit failing tests
- Skip tests without good reason
- Test implementation details
- Share mutable state between tests
- Commit without running tests
