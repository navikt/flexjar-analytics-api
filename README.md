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
| `DELETE /api/v1/intern/feedback/{id}` | Soft delete feedback |
| `POST /api/v1/intern/feedback/{id}/tags` | Add tag |
| `DELETE /api/v1/intern/feedback/{id}/tags?tag=X` | Remove tag |
| `GET /api/v1/intern/feedback/tags` | List all tags |
| `GET /api/v1/intern/feedback/teams` | List teams and apps |
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
| `feedbackId` | string | - | Filter by survey ID |
| `medTekst` | boolean | `false` | Only with text feedback |
| `stjerne` | boolean | `false` | Only starred |
| `tags` | string | - | Comma-separated tags |
| `fritekst` | string | - | Free text search |
| `page` | int | last | Page number (0-indexed) |
| `size` | int | `10` | Page size |

### Submission (For Widget)

| Endpoint | Description |
|----------|-------------|
| `POST /api/v1/feedback` | Submit feedback (legacy) |
| `POST /api/v2/feedback` | Submit feedback (returns ID) |
| `PUT /api/v2/feedback/{id}` | Update feedback |
| `POST /api/v1/feedback/azure` | Azure variant |
| `POST /api/azure/v2/feedback` | Azure variant |
| `PUT /api/azure/v2/feedback/{id}` | Azure update |

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
