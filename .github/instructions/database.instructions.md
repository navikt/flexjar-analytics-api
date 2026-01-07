---
applyTo: "**/db/migration/**/*.sql"
---

# Database Migration Standards (Flyway)

## Migration File Naming

Follow Flyway naming convention: `V{version}__{description}.sql`

### Examples

```
V1__Initial_schema.sql
V2__Text_themes.sql
```

### Rules

- Version numbers must be sequential (1, 2, 3, ...)
- Use double underscore `__` between version and description
- Description should be lowercase with underscores
- **NEVER modify existing migrations** - always create new ones

## Migration File Structure

```sql
-- V1__Initial_schema.sql

CREATE TABLE IF NOT EXISTS feedback
(
    id            VARCHAR DEFAULT gen_random_uuid() PRIMARY KEY,
    opprettet     TIMESTAMP WITH TIME ZONE NOT NULL,
    feedback_json TEXT NOT NULL,
    team          VARCHAR(255) NOT NULL,
    app           VARCHAR(255) NOT NULL,
    tags          TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_feedback_opprettet ON feedback(opprettet);
CREATE INDEX IF NOT EXISTS idx_feedback_team ON feedback(team);
CREATE INDEX IF NOT EXISTS idx_feedback_team_app ON feedback(team, app);
```

## Best Practices

### Primary Keys

```sql
-- Use BIGSERIAL for auto-incrementing primary keys
id BIGSERIAL PRIMARY KEY,

-- Use UUID for distributed systems
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
```

### Timestamps

- Prefer `TIMESTAMP WITH TIME ZONE` (`TIMESTAMPTZ`) when storing timestamps.
- Follow existing naming conventions in this repo (e.g. `opprettet`).

### Indexes

```sql
-- Index foreign keys
CREATE INDEX idx_user_id ON orders(user_id);

-- Index frequently queried columns
CREATE INDEX idx_created_at ON orders(created_at);

-- Composite indexes for multi-column queries
CREATE INDEX idx_user_status ON orders(user_id, status);

-- Partial indexes for filtered queries
CREATE INDEX idx_active_orders ON orders(user_id)
WHERE status = 'active';
```

Note on `CREATE INDEX CONCURRENTLY`:
- It cannot run inside a transaction.
- Flyway typically runs migrations in transactions.
- Only use `CONCURRENTLY` if you intentionally configure Flyway to run that migration non-transactionally.

### Constraints

```sql
-- Foreign keys with ON DELETE CASCADE
user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

-- Check constraints
CONSTRAINT check_positive_amount CHECK (amount > 0),
CONSTRAINT check_valid_status CHECK (status IN ('pending', 'active', 'completed')),

-- Unique constraints
CONSTRAINT unique_email UNIQUE (email),
CONSTRAINT unique_user_period UNIQUE (user_id, period_id)
```

### Data Types

```sql
-- Prefer specific types
VARCHAR(n)      -- For strings with known max length
TEXT            -- For strings with unknown length
BIGINT          -- For large numbers
NUMERIC(10,2)   -- For decimal numbers (money)
TIMESTAMP       -- For date/time
DATE            -- For dates only
BOOLEAN         -- For true/false
UUID            -- For unique identifiers
JSONB           -- For structured JSON data
```

## Migration Patterns

### Adding a Column

```sql
-- V3__add_star_column.sql

ALTER TABLE feedback
ADD COLUMN stjerne BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_feedback_stjerne ON feedback(stjerne);
```

### Adding a Table with Foreign Key

If you need a new table, follow `V2__Text_themes.sql` as an example (simple table + indexes + team scoping).

### Altering a Column

```sql
-- V4__alter_ident_length.sql

ALTER TABLE rapporteringsperiode
ALTER COLUMN ident TYPE VARCHAR(20);
```

## Kotlin Integration

Migrations run on startup in `configureDatabase()` using `DatabaseHolder`.

## Testing Migrations

```kotlin
@Testcontainers
class MigrationTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Test
    fun `migrations should run successfully`() {
        val dataSource = HikariDataSource().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
        }

        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .load()

        val result = flyway.migrate()
        result.migrationsExecuted shouldBeGreaterThan 0
    }
}
```

## Boundaries

### ✅ Always

- Follow V{n}\_\_{description}.sql naming
- Add indexes for foreign keys
- Follow existing column naming conventions in this repo
- Use appropriate data types
- Test migrations in dev environment first

### ⚠️ Ask First

- Schema changes affecting multiple tables
- Dropping columns or tables
- Changing primary keys
- Large data migrations

### 🚫 Never

- Modify existing migration files
- Skip version numbers
- Use single underscore in naming
- Deploy untested migrations to production
- Commit migration files without testing
