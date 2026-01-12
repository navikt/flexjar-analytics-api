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
- 🔐 **Azure AD authentication** - Secure access via Wonderwall

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
| `GET /api/v1/intern/feedback/teams` | List teams and apps |
| `DELETE /api/v1/intern/surveys/{surveyId}` | Delete all feedback for a survey |
| `GET /api/v1/intern/stats` | Get statistics |
| `GET /api/v1/intern/stats/ratings` | Rating distribution |
| `GET /api/v1/intern/stats/timeline` | Timeline data |
| `GET /api/v1/intern/export?format=csv\|json\|excel` | Export data |

### Query Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `team` | string | `flex` | Filter by team |
| `app` | string | - | Filter by app |
| `from` | ISO date | - | Start date |
| `to` | ISO date | - | End date |
| `surveyId` | string | - | Filter by survey ID |
| `medTekst` | boolean | `false` | Only with text feedback |
| `stjerne` | boolean | `false` | Only starred |
| `tags` | string | - | Comma-separated tags |
| `fritekst` | string | - | Free text search |
| `page` | int | last | Page number (0-indexed) |
| `size` | int | `10` | Page size |

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

### For Analytics Dashboard Access {#getting-access}

To access the Flexjar Analytics dashboard, your team must be onboarded.

There are currently two supported approaches:

#### Option A (legacy): AD group allow-listing

This is the current onboarding flow (manual mapping + group allow-list).

#### 1. Add your team's AD group to the configuration

Create a PR or open an issue to add your team's Azure AD group UUID to:
- `flexjar-analytics-api/src/main/kotlin/no/nav/flexjar/config/auth/TeamAuthorization.kt` (GROUP_TO_TEAM mapping)
- `flexjar-analytics-api/nais/app/dev.yaml` and `prod.yaml` (claims.groups)

#### 2. Ensure you're a member of the AD group

You must be a member of your team's AD group to access analytics. Contact your team lead if you're unsure which group to join.

#### 3. Log in with your NAV account

Once your team is onboarded, simply log in at the dashboard URL with your NAV account.

#### Option B (recommended): NAIS API-backed team membership

If enabled, the backend can authorize analytics access by looking up team membership via the NAIS Console GraphQL API, using the user's email from the Azure token claims (e.g. `preferred_username`). This allows scaling without updating `GROUP_TO_TEAM` for every new team.

**Setup:**

1. Create a secret in NAIS Console named `flexjar-analytics-api` (in both dev and prod) with key:
  - `NAIS_API_KEY` (get this from NAIS Console)

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

To test NAIS team lookups locally, you normally need a valid `NAIS_API_KEY` (the client authenticates with `X-Api-Key`).

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
3. Results are cached for 5 minutes to reduce API load
4. If the API call fails, the backend falls back to legacy AD group mapping

**Observability:**

The NAIS API integration exposes Prometheus metrics at `/internal/prometheus`:

| Metric | Description |
|--------|-------------|
| `nais_api_calls_total` | Total number of NAIS API calls |
| `nais_api_errors_total` | Total number of NAIS API errors |
| `nais_api_call_duration_seconds` | Duration of NAIS API calls |
| `nais_api_cache_hits_total` | Number of cache hits |
| `nais_api_cache_misses_total` | Number of cache misses |

**Fallback behavior:**

When NAIS API is enabled but a lookup fails (e.g. missing email claim or NAIS API outage), the backend falls back to the legacy AD group mapping to avoid breaking existing access during rollout.


### ⚠️ AD Group Configuration (Security Notice)

Authorization relies on Azure AD Group IDs. 
**When onboarding a new team:**

| Location | File | Required? | Purpose |
|----------|------|-----------|---------|
| Backend Code | [`TeamAuthorization.kt`](src/main/kotlin/no/nav/flexjar/config/auth/TeamAuthorization.kt) | **Yes** | `GROUP_TO_TEAM` mapping |
| NAIS Config (Dev) | [`nais/app/dev.yaml`](nais/app/dev.yaml) | **Yes** | `azure.application.claims.groups` |
| NAIS Config (Prod) | [`nais/app/prod.yaml`](nais/app/prod.yaml) | **Yes** | `azure.application.claims.groups` |

**Current authorized groups:**
| Group UUID | Team Namespace |
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
