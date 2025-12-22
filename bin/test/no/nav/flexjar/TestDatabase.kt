package no.nav.flexjar

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.flexjar.config.DatabaseHolder
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import javax.sql.DataSource

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:17-alpine")

/**
 * Test database configuration using Testcontainers.
 * Provides a clean, isolated PostgreSQL instance for each test run.
 */
object TestDatabase {
    private val container: PsqlContainer by lazy {
        PsqlContainer().apply {
            withDatabaseName("flexjar_test")
            withUsername("test")
            withPassword("test")
            setWaitStrategy(HostPortWaitStrategy())
            start()
        }
    }
    
    private var _dataSource: HikariDataSource? = null
    
    val dataSource: DataSource
        get() = _dataSource ?: createDataSource().also { 
            _dataSource = it
            // Initialize the production DatabaseHolder with test datasource
            DatabaseHolder.initializeForTesting(it)
        }
    
    private fun createDataSource(): HikariDataSource {
        return HikariDataSource(HikariConfig().apply {
            jdbcUrl = container.jdbcUrl
            username = container.username
            password = container.password
            maximumPoolSize = 3
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            poolName = "flexjar-test-pool"
        })
    }
    
    /**
     * Initialize the test database: run migrations and connect Exposed.
     */
    fun initialize() {
        // Run migrations
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()
            .migrate()
            
        Database.connect(dataSource)
    }
    
    /**
     * Clear all data from the database (for test isolation).
     */
    fun clearAllData() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE feedback CASCADE")
            }
            conn.commit()
        }
    }
    
    /**
     * Reset the database holder after tests.
     */
    fun reset() {
        DatabaseHolder.reset()
        _dataSource?.close()
        _dataSource = null
    }
}
