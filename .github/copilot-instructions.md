# Flexjar Analytics API – AI Coding Guide

Ktor 3.x backend for Flexjar survey analytics with PostgreSQL storage.

## Architecture

```
src/main/kotlin/no/nav/flexjar/
├── Application.kt        # Entry point, Ktor module setup
├── config/
│   ├── Auth.kt               # Azure AD / TokenX via nav-token-support
│   ├── Database.kt           # HikariCP + Flyway + Exposed
│   ├── Routing.kt            # Route registration + Ktor Resources
│   ├── Metrics.kt            # Micrometer + Prometheus
│   └── Serialization.kt
├── domain/
│   └── Models.kt             # DTOs, query types, FeedbackStatsResult
├── repository/
│   ├── FeedbackTable.kt      # Exposed Table definition + JsonExtract/DateDate helpers
│   ├── FeedbackRepository.kt # CRUD operations (Exposed DSL)
│   ├── FeedbackStatsRepository.kt # Stats/analytics queries (Exposed DSL)
│   └── Extensions.kt         # ResultRow.toDto() extension functions
├── routes/
│   ├── Resources.kt          # Ktor Resources (type-safe routing)
│   ├── SubmissionRoutes.kt   # POST feedback from widget
│   ├── FeedbackRoutes.kt     # GET/DELETE for dashboard
│   ├── StatsRoutes.kt        # Aggregations, timeline
│   ├── ExportRoutes.kt       # CSV/JSON/Excel
│   └── InternalRoutes.kt     # Health checks + /internal/prometheus
└── sensitive/
    ├── SensitiveDataFilter.kt    # Redaction logic
    └── SensitiveDataPatterns.kt  # Regex patterns for PII
```

### Key Technologies
- **Exposed 0.56+**: DSL-based type-safe SQL (no DAO layer)
- **Ktor Resources**: Type-safe routing with `@Resource` annotated classes
- **Micrometer + Prometheus**: Metrics exposed at `/internal/prometheus`

### Key Concepts
- **Sensitive data filtering**: All text responses pass through `SensitiveDataFilter.redact()` before API response
- **Two auth contexts**: Public submission routes (TokenX/anonymous) vs. protected analytics routes (Azure AD)
- **Flyway migrations**: SQL files in `src/main/resources/db/migration/`
- **Soft delete**: Feedback has `deleted_at` column, never hard-deleted

## Commands
```sh
./gradlew run       # Start locally (needs PostgreSQL)
./gradlew build     # Build fat JAR
./gradlew test      # Kotest + Testcontainers
```

## Local Development
```sh
# Start PostgreSQL
docker run -d --name flexjar-db \
  -e POSTGRES_USER=flexjar -e POSTGRES_PASSWORD=flexjar -e POSTGRES_DB=flexjar \
  -p 5432:5432 postgres:15

./gradlew run
```

## Testing Patterns
- **Testcontainers**: Integration tests spin up PostgreSQL
- **mock-oauth2-server**: For authenticated route tests
- **Kotest**: Use `StringSpec` or `FunSpec` styles

## API Conventions
1. **Query params**: `team`, `app`, `from`, `to`, `feedbackId`, `tags`, `fritekst`, `page`, `size`
2. **Response format**: `Page<T>` wrapper with `content`, `totalElements`, `totalPages`
3. **Reserved payload keys**: Widget sends `svar` (rating) and `feedback` (main text)
4. **Error responses**: Use Ktor status pages for consistent JSON errors

## Related Repositories
- **[flexjar-widget](https://github.com/navikt/flexjar-widget)**: Survey widget that POSTs to `/api/v1/feedback`. Sends `svar` (rating) and `feedback` (text) keys.
- **[flexjar-analytics](https://github.com/navikt/flexjar-analytics)**: Dashboard that calls `/api/v1/intern/*` endpoints. DTOs in `domain/` must match `lib/api.ts` types.

## Sensitive Data Patterns
Located in `SensitiveDataPatterns.kt`:
| Pattern | Replacement |
|---------|-------------|
| Fødselsnummer (11 digits) | `[FØDSELSNUMMER FJERNET]` |
| NAVident (letter + 6 digits) | `[NAVIDENT FJERNET]` |
| Email | `[E-POST FJERNET]` |
| Phone (8 digits) | `[TELEFON FJERNET]` |
| Bank card/account | `[KORTNUMMER/KONTONUMMER FJERNET]` |

Adding new patterns: Add to `HIGH_CONFIDENCE_PATTERNS` list with appropriate regex and replacement text.
