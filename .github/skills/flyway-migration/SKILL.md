---
name: flyway-migration
description: Database migration patterns using Flyway with versioned SQL scripts
---

# Flyway Migration Skill

This skill provides patterns for managing database schema changes with Flyway.

## Migration File Naming

```text
db/migration/V{version}__{description}.sql
```

Examples:

- `V1__Initial_schema.sql`
- `V2__Text_themes.sql`
- `V3__add_star_column.sql`

## Creating Tables

```sql
-- V1__Initial_schema.sql

CREATE TABLE IF NOT EXISTS feedback
(
    id            VARCHAR DEFAULT gen_random_uuid() PRIMARY KEY,
    opprettet     TIMESTAMPTZ NOT NULL,
    feedback_json TEXT NOT NULL,
    team          VARCHAR(255) NOT NULL,
    app           VARCHAR(255) NOT NULL,
    tags          TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_feedback_opprettet ON feedback(opprettet);
CREATE INDEX IF NOT EXISTS idx_feedback_team ON feedback(team);
CREATE INDEX IF NOT EXISTS idx_feedback_team_app ON feedback(team, app);
```

## Adding Columns

```sql
-- V3__add_star_column.sql
ALTER TABLE feedback ADD COLUMN stjerne BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_feedback_stjerne ON feedback(stjerne);
```

## Creating Indexes

Avoid `CREATE INDEX CONCURRENTLY` unless you intentionally configure Flyway to run that migration non-transactionally.

## Adding Foreign Keys

Follow `V2__Text_themes.sql` in this repo for a “new table + indexes” example.

## Data Migrations

```sql
-- V5__set_default_status.sql
UPDATE users
SET status = 'active'
WHERE status IS NULL;

ALTER TABLE users
ALTER COLUMN status SET NOT NULL;
```

## Kotlin Integration

```kotlin
import org.flywaydb.core.Flyway
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

fun createDataSource(jdbcUrl: String): HikariDataSource {
    val config = HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        username = System.getenv("DATABASE_USERNAME")
        password = System.getenv("DATABASE_PASSWORD")
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 60000
        maxLifetime = 600000
    }

    return HikariDataSource(config)
}

fun runMigrations(dataSource: HikariDataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}

// In main()
fun main() {
    val dataSource = createDataSource(env.databaseUrl)
    runMigrations(dataSource)

    logger.info("Database migrations completed")
}
```

## Best Practices

1. **Never modify existing migrations**: Create a new migration instead
2. **Prefer simple transactional migrations**: Keep migrations safe to run via Flyway defaults
3. **Test migrations on dev first**: Always test before production
4. **Keep migrations small**: One logical change per migration
5. **Use transactions**: Wrap changes in BEGIN/COMMIT when possible
6. **Add rollback notes**: Comment how to manually rollback if needed
