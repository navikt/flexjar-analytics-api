package no.nav.flexjar.config

import org.slf4j.LoggerFactory

/**
 * Type-safe environment configuration for the application.
 * 
 * All environment variables are validated at startup - the application
 * will fail fast if required variables are missing in production.
 * 
 * Local development uses explicit defaults via [forLocal].
 */
data class ServerEnv(
    val database: DatabaseEnv,
    val nais: NaisEnv,
    val auth: AuthEnv
) {
    /**
     * Database connection configuration.
     * NAIS provides these with envVarPrefix: DB
     */
    data class DatabaseEnv(
        /** Complete JDBC URL with SSL certificates (NAIS-provided) */
        val jdbcUrl: String?,
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String
    ) {
        /**
         * Returns the JDBC URL to use for database connection.
         * Prefers NAIS-provided URL (includes SSL certs), falls back to constructed URL.
         */
        fun getConnectionUrl(): String {
            return jdbcUrl ?: "jdbc:postgresql://$host:$port/$database"
        }
        
        companion object {
            /**
             * Load from NAIS environment variables.
             * Requires DB_USERNAME and DB_PASSWORD to be set.
             */
            fun fromNais(): DatabaseEnv {
                return DatabaseEnv(
                    jdbcUrl = requireEnv("DB_JDBC_URL"),
                    host = requireEnv("DB_HOST"),
                    port = requireEnv("DB_PORT").toInt(),
                    database = requireEnv("DB_DATABASE"),
                    username = requireEnv("DB_USERNAME"),
                    password = requireEnv("DB_PASSWORD")
                )
            }
            
            /**
             * Configuration for local development with Docker Compose or local Postgres.
             */
            fun forLocal(
                host: String = "localhost",
                port: Int = 5432,
                database: String = "flexjar",
                username: String = "flexjar",
                password: String = "flexjar"
            ) = DatabaseEnv(
                jdbcUrl = null,
                host = host,
                port = port,
                database = database,
                username = username,
                password = password
            )
            
            private fun requireEnv(name: String): String =
                System.getenv(name) ?: throw IllegalStateException("Required env var $name is not set")
        }
    }
    
    /**
     * NAIS platform environment configuration.
     */
    data class NaisEnv(
        /** Current cluster: dev-gcp, prod-gcp, or null for local */
        val clusterName: String?,
        /** Token introspection endpoint for Texas sidecar */
        val tokenIntrospectionEndpoint: String?
    ) {
        val isLocal: Boolean get() = clusterName == null
        val isDev: Boolean get() = clusterName == "dev-gcp"
        val isProd: Boolean get() = clusterName == "prod-gcp"
        val isNais: Boolean get() = clusterName != null
        
        companion object {
            fun fromEnvironment() = NaisEnv(
                clusterName = System.getenv("NAIS_CLUSTER_NAME"),
                tokenIntrospectionEndpoint = System.getenv("NAIS_TOKEN_INTROSPECTION_ENDPOINT")
            )
            
            fun forLocal() = NaisEnv(
                clusterName = null,
                tokenIntrospectionEndpoint = null
            )
        }
    }
    
    /**
     * Authentication configuration.
     */
    data class AuthEnv(
        /** Allowed client ID for flexjar-analytics frontend */
        val flexjarAnalyticsClientId: String
    ) {
        companion object {
            fun fromEnvironment(clusterName: String?) = AuthEnv(
                flexjarAnalyticsClientId = System.getenv("FLEXJAR_ANALYTICS_CLIENT_ID")
                    ?: "${clusterName ?: "dev-gcp"}:team-esyfo:flexjar-analytics"
            )
            
            fun forLocal() = AuthEnv(
                flexjarAnalyticsClientId = "dev-gcp:team-esyfo:flexjar-analytics"
            )
        }
    }
    
    companion object {
        private val log = LoggerFactory.getLogger("ServerEnv")
        
        // Singleton instance (lazy-loaded)
        val current: ServerEnv by lazy { fromEnvironment() }
        
        /**
         * Load environment configuration.
         * Automatically detects NAIS vs local and loads appropriate config.
         */
        fun fromEnvironment(): ServerEnv {
            val nais = NaisEnv.fromEnvironment()
            
            return if (nais.isNais) {
                log.info("Running in NAIS cluster: ${nais.clusterName}")
                forProduction(nais)
            } else {
                log.info("Running in local development mode")
                forLocal()
            }
        }
        
        /**
         * Production configuration - requires all environment variables.
         */
        private fun forProduction(nais: NaisEnv): ServerEnv {
            log.info("Loading production database configuration from NAIS env vars")
            return ServerEnv(
                database = DatabaseEnv.fromNais(),
                nais = nais,
                auth = AuthEnv.fromEnvironment(nais.clusterName)
            )
        }
        
        /**
         * Local development configuration with sensible defaults.
         * Expects local Postgres (Docker Compose or native).
         */
        fun forLocal(): ServerEnv {
            log.info("Using local development defaults")
            return ServerEnv(
                database = DatabaseEnv.forLocal(),
                nais = NaisEnv.forLocal(),
                auth = AuthEnv.forLocal()
            )
        }
        
        /**
         * Create a test configuration with custom database settings.
         */
        fun forTesting(
            jdbcUrl: String,
            username: String = "test",
            password: String = "test"
        ): ServerEnv {
            return ServerEnv(
                database = DatabaseEnv(
                    jdbcUrl = jdbcUrl,
                    host = "localhost",
                    port = 5432,
                    database = "test",
                    username = username,
                    password = password
                ),
                nais = NaisEnv.forLocal(),
                auth = AuthEnv.forLocal()
            )
        }
    }
}
