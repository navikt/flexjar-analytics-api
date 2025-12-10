package no.nav.flexjar.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("Database")

lateinit var dataSource: DataSource

fun setDataSourceForTesting(ds: DataSource) {
    dataSource = ds
}

fun Application.configureDatabase() {
    val dbConfig = DatabaseConfig.fromEnvironment()
    dataSource = createDataSource(dbConfig)
    
    runMigrations(dataSource)
    
    log.info("Database configured successfully")
}

fun createDataSource(config: DatabaseConfig): HikariDataSource {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.username
        password = config.password
        maximumPoolSize = config.maxPoolSize
        minimumIdle = config.minIdle
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

fun runMigrations(dataSource: DataSource) {
    log.info("Running database migrations...")
    
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .load()
        .migrate()
    
    log.info("Database migrations completed")
}

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 10,
    val minIdle: Int = 2
) {
    companion object {
        fun fromEnvironment(): DatabaseConfig {
            // NAIS provides these environment variables for Cloud SQL
            val host = System.getenv("DB_HOST") ?: "localhost"
            val port = System.getenv("DB_PORT") ?: "5432"
            val database = System.getenv("DB_DATABASE") ?: "flexjar"
            val username = System.getenv("DB_USERNAME") ?: "flexjar"
            val password = System.getenv("DB_PASSWORD") ?: "flexjar"
            
            val jdbcUrl = "jdbc:postgresql://$host:$port/$database"
            
            return DatabaseConfig(
                jdbcUrl = jdbcUrl,
                username = username,
                password = password
            )
        }
    }
}
