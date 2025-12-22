package no.nav.flexjar.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("Database")

/**
 * Application-level database holder.
 * Initialized during application startup.
 */
object DatabaseHolder {
    private var _dataSource: DataSource? = null
    
    val dataSource: DataSource
        get() = _dataSource ?: throw IllegalStateException(
            "Database not initialized. Call configureDatabase() first."
        )
    
    /**
     * Initialize for production use with ServerEnv configuration.
     */
    fun initialize(env: ServerEnv.DatabaseEnv) {
        if (_dataSource != null) {
            log.warn("Database already initialized, skipping")
            return
        }
        _dataSource = createDataSource(env)
        log.info("Database initialized with production configuration")
    }
    
    /**
     * Initialize for testing with a pre-configured DataSource.
     */
    fun initializeForTesting(dataSource: DataSource) {
        _dataSource = dataSource
        log.info("Database initialized with test configuration")
    }
    
    /**
     * Reset the database holder (for testing purposes only).
     */
    fun reset() {
        (_dataSource as? HikariDataSource)?.close()
        _dataSource = null
    }
}

/**
 * Ktor module extension to configure the database.
 * Uses ServerEnv for type-safe configuration.
 */
fun Application.configureDatabase() {
    val env = ServerEnv.current
    
    DatabaseHolder.initialize(env.database)
    
    runMigrations(DatabaseHolder.dataSource)
    
    Database.connect(DatabaseHolder.dataSource)
    
    log.info("Database configured successfully")
}

/**
 * Create a HikariCP DataSource from environment configuration.
 */
fun createDataSource(config: ServerEnv.DatabaseEnv): HikariDataSource {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.getConnectionUrl()
        username = config.username
        password = config.password
        maximumPoolSize = 10
        minimumIdle = 2
        idleTimeout = 30000
        connectionTimeout = 30000
        maxLifetime = 1800000
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
        poolName = "flexjar-analytics-pool"
        
        // Validation
        connectionTestQuery = "SELECT 1"
        validationTimeout = 5000
    }
    
    return HikariDataSource(hikariConfig)
}

/**
 * Run Flyway database migrations.
 */
fun runMigrations(dataSource: DataSource) {
    log.info("Running database migrations...")
    
    try {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
        
        val info = flyway.info()
        log.info("Flyway info - Current version: ${info.current()?.version ?: "NONE"}")
        log.info("Flyway info - Pending migrations: ${info.pending().size}")
        
        info.pending().forEach { pending ->
            log.info("  Pending: ${pending.version} - ${pending.description}")
        }
        
        val result = flyway.migrate()
        
        log.info("Database migrations completed!")
        log.info("  Migrations executed: ${result.migrationsExecuted}")
        log.info("  Target schema version: ${result.targetSchemaVersion ?: "NONE"}")
        
        // Verify table exists
        dataSource.connection.use { conn ->
            val rs = conn.metaData.getTables(null, null, "feedback", null)
            if (rs.next()) {
                log.info("✓ Verified 'feedback' table exists in database")
            } else {
                log.error("✗ ERROR: 'feedback' table does NOT exist after migration!")
            }
        }
    } catch (e: Exception) {
        log.error("Failed to run database migrations", e)
        throw e
    }
}
