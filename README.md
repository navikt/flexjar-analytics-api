# Flexjar Analytics API

Backend API for Flexjar survey analytics. Built with Ktor.

## Quick Start

```bash
# Prerequisites: JDK 21, Docker

# 1. Start PostgreSQL
docker run -d --name flexjar-db \
  -e POSTGRES_USER=flexjar -e POSTGRES_PASSWORD=flexjar -e POSTGRES_DB=flexjar \
  -p 5432:5432 postgres:15

# 2. Run the API
./gradlew run
# API available at http://localhost:8080
# Swagger UI at http://localhost:8080/swagger
```

## Features

- 📊 **Analytics endpoints** - Statistics, aggregations, timeline data
- 🔒 **Sensitive data filtering** - Automatic redaction of PII (fødselsnummer, email, phone, etc.)
- 📤 **Export** - CSV, JSON, and Excel exports
- 📅 **Date range filtering** - Filter feedback by time period
- 🏷️ **Tag management** - Add/remove tags on feedback
- 🔐 **Azure AD authentication** - Secure access via NAIS Texas

## API Endpoints

### Analytics (Protected)

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/intern/feedback` | List feedback with filters |
| `GET /api/v1/intern/feedback/{id}` | Get single feedback |
| `DELETE /api/v1/intern/feedback/{id}` | Delete feedback permanently |
| `POST /api/v1/intern/feedback/{id}/tags` | Add tag |
| `DELETE /api/v1/intern/feedback/{id}/tags?tag=X` | Remove tag |
| `GET /api/v1/intern/feedback/tags` | List all tags |
| `GET /api/v1/intern/teams` | List authorized teams and apps |
| `GET /api/v1/intern/feedback/teams` | List apps for the currently selected team |
| `DELETE /api/v1/intern/surveys/{surveyId}` | Delete all feedback for a survey |
| `GET /api/v1/intern/stats` | Get statistics |
| `GET /api/v1/intern/stats/ratings` | Rating distribution |
| `GET /api/v1/intern/stats/timeline` | Timeline data |
| `GET /api/v1/intern/export?format=csv\|json\|excel` | Export data |

### Query Parameters

All endpoints under `/api/v1/intern/*` are **team-scoped**.

- `team` is an **optional** query parameter.
- If `team` is omitted, the backend picks a stable default authorized team.
- If `team` is provided but not authorized, the backend returns **403**.
- Route handlers always use `call.authorizedTeam` (validated by `TeamAuthorizationPlugin`).

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `team` | string | (selected by backend) | Team scope for the request |
| `app` | string | - | Filter by app |
| `fromDate` | `YYYY-MM-DD` | - | Start date (Europe/Oslo, inclusive) |
| `toDate` | `YYYY-MM-DD` | - | End date (Europe/Oslo, inclusive) |
| `surveyId` | string | - | Filter by survey ID |
| `hasText` | boolean | `false` | Only include feedback with text responses |
| `lowRating` | boolean | `false` | Only include low ratings (1-2) |
| `tag` | string[] | - | Repeated `tag=foo&tag=bar` (also accepts comma-separated values per entry) |
| `query` | string | - | Full-text search query |
| `page` | int | `0` | Page number (0-indexed) |
| `size` | int | `10` | Page size |
| `deviceType` | string | - | `mobile`, `tablet`, `desktop` |
| `segment` | string[] | - | Repeated `segment=key:value` |
| `task` | string | - | Top Tasks drill-down filter (matches option label) |

### Submission (For Widget)

| Endpoint | Description |
|----------|-------------|
| `POST /api/v1/feedback` | Submit feedback (schemaVersion=1, returns created ID) |

## 🔐 Security & Access

This API follows NAIS Zero Trust principles. Both parties must configure access policies.

### For Teams Wanting to Submit Feedback

To submit feedback from your application, you need:

#### 1. Request Access (Our Side)

Open an issue in this repository using the **"Request API Access"** template, or create a PR adding your app to our NAIS config:

```yaml
# In nais/app/dev.yaml and nais/app/prod.yaml
spec:
  azure:
    application:
      accessPolicy:
        inbound:
          rules:
            - application: your-app-name
              namespace: your-team
```

#### 2. Configure Outbound (Your Side)

Add `flexjar-analytics-api` to your app's outbound access policy:

```yaml
# In your app's nais.yaml
spec:
  accessPolicy:
    outbound:
      rules:
        - application: flexjar-analytics-api
          namespace: team-esyfo
```

#### 3. Get Azure AD Token

Your app must obtain an Azure AD token targeting `flexjar-analytics-api` and include it in requests:

```
Authorization: Bearer <token>
```

The token's `azp_name` claim identifies your app and determines which team the feedback belongs to.

### Getting access

This section covers access to the analytics (dashboard) endpoints.

The backend authorizes dashboard access by looking up team membership via the NAIS Console GraphQL API, using the user's email from the Azure token claims (e.g. `preferred_username`).

If NAIS team lookup is not configured, the API fails closed with **503** (`TEAM_LOOKUP_NOT_CONFIGURED`).

#### Setup (NAIS)

**Setup:**

1. Create a secret in NAIS Console named `flexjar-analytics-api` (in both dev and prod) with key:
  - `NAIS_API_KEY` (or `TEAMS_TOKEN`) (get this from NAIS Console)

  The manifests already set `NAIS_API_GRAPHQL_URL` to `https://console.nav.cloud.nais.io/graphql`.
  You may also put `NAIS_API_GRAPHQL_URL` in the same secret, but it is not required.

  Example (dev, run in the correct namespace):

  ```bash
  kubectl create secret generic flexjar-analytics-api \
    --from-literal=NAIS_API_KEY='<paste-api-key-here>'
  ```

2. The manifests already reference this secret via `spec.envFrom`.

  If the secret does not exist, the deployment will fail because Kubernetes cannot mount env vars from a missing secret.

**Local testing:**

To test NAIS team lookups locally, you normally need a valid `NAIS_API_KEY` (or `TEAMS_TOKEN`).
The client authenticates to NAIS Console GraphQL using `Authorization: Bearer <token>`.

If you are logged in with the NAIS CLI, you can also use the local NAIS API proxy to call the GraphQL endpoint without a real key:

```bash
nais login -n
nais alpha api proxy  # listens on localhost:4242

# Proxy forwards to https://console.nav.cloud.nais.io/graphql
export NAIS_API_GRAPHQL_URL='http://localhost:4242/graphql'

# Must be non-empty to enable the integration in this app.
# The proxy accepts requests even if this is not a real key.
export NAIS_API_KEY='dummy'

./gradlew run
```

```bash
export NAIS_API_GRAPHQL_URL='https://console.nav.cloud.nais.io/graphql'
export NAIS_API_KEY='<dev-api-key>'

./gradlew run
```

**How it works:**

1. When a user logs in, the backend extracts their email from the Azure token
2. The NAIS Console API is queried for the user's team memberships
3. Results are cached to reduce API load (separate TTLs for "has teams" vs "no teams")
4. If the API call fails, access is denied (fail closed)

**Observability:**

The NAIS API integration exposes Prometheus metrics at `/internal/prometheus`:

| Metric | Description |
|--------|-------------|
| `nais_api_calls_total` | Total number of NAIS API calls |
| `nais_api_errors_total` | Total number of NAIS API errors |
| `nais_api_call_duration_seconds` | Duration of NAIS API calls |
| `nais_api_cache_hits_total` | Number of cache hits |
| `nais_api_cache_misses_total` | Number of cache misses |

**Notes:**

- Team identifiers in query params are NAIS namespace slugs (e.g. `team-esyfo`).
- The API will return **403** (`NO_TEAM_ACCESS`) if NAIS returns no teams for the user.
|------------|----------------|
| `5066bb56-7f19-4b49-ae48-f1ba66abf546` | `isyfo` |
| `ef4e9824-6f3a-4933-8f40-6edf5233d4d2` | `esyfo` |


## Sensitive Data Filtering

The API automatically redacts sensitive data from feedback text:

| Pattern | Example | Replacement |
|---------|---------|-------------|
| Fødselsnummer | 12345678901 | `[FØDSELSNUMMER FJERNET]` |
| NAVident | A123456 | `[NAVIDENT FJERNET]` |
| Email | test@nav.no | `[E-POST FJERNET]` |
| Phone | 12345678 | `[TELEFON FJERNET]` |
| Bank card | 1234 5678 9012 3456 | `[KORTNUMMER FJERNET]` |
| Bank account | 1234.56.12345 | `[KONTONUMMER FJERNET]` |
| Secret address | "hemmelig adresse" | `[HEMMELIG ADRESSE]` |

## Development

### Prerequisites

- JDK 21
- Docker (for PostgreSQL)

### Run locally

```bash
# Start PostgreSQL
docker run -d --name flexjar-db \
  -e POSTGRES_USER=flexjar \
  -e POSTGRES_PASSWORD=flexjar \
  -e POSTGRES_DB=flexjar \
  -p 5432:5432 \
  postgres:15

# Run the application
./gradlew run
```

### Build

```bash
./gradlew build
```

### Test

```bash
./gradlew test
```

## Deployment

Deployed to NAIS via GitHub Actions.

```bash
# Dev
kubectl apply -f nais/app/dev.yaml

# Prod
kubectl apply -f nais/app/naiserator.yaml
```

## Tech Stack

- **Ktor** - Kotlin async web framework
- **PostgreSQL** - Database
- **Flyway** - Database migrations
- **HikariCP** - Connection pooling
- **kotlinx.serialization** - JSON serialization
- **Apache POI** - Excel export
- **nav-token-support** - Azure AD / TokenX validation
