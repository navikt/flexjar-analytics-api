package no.nav.flexjar.integrations.nais

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import no.nav.flexjar.config.appMicrometerRegistry
import no.nav.flexjar.integrations.valkey.TeamCache
import no.nav.flexjar.integrations.valkey.ValkeyTeamCache
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

private val log = LoggerFactory.getLogger("NaisGraphQlClient")

/**
 * Result type for NAIS API operations.
 * Distinguishes between successful empty results and errors.
 */
sealed class NaisApiResult<out T> {
    data class Success<T>(val value: T) : NaisApiResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : NaisApiResult<Nothing>()
    
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Error -> default
    }
    
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
}

/**
 * Cache TTL configuration.
 * Different TTLs based on whether user has teams or not.
 */
object CacheTtl {
    /** TTL when user HAS teams - team membership changes rarely */
    val HAS_TEAMS: Duration = Duration.ofHours(1)
    
    /** TTL when user has NO teams - allow quick onboarding */
    val NO_TEAMS: Duration = Duration.ofMinutes(5)
    
    /** Default TTL for in-memory cache (legacy) */
    val DEFAULT: Duration = Duration.ofMinutes(5)
}

/**
 * Client for NAIS Console GraphQL API.
 * 
 * Used to dynamically look up team membership for users, replacing
 * the need for hardcoded AD group → team mappings.
 * 
 * Features:
 * - Valkey cache with fallback to in-memory
 * - Prometheus metrics for monitoring
 * - Health check endpoint
 * - Graceful error handling with Result type
 * - Different TTLs for users with/without teams
 */
class NaisGraphQlClient private constructor(
    private val graphqlUrl: String,
    private val apiKey: String,
    private val teamCache: TeamCache,
    private val clock: Clock = Clock.systemUTC(),
    private val client: HttpClient = defaultHttpClient()
) {
    // Health tracking
    private val lastSuccessfulCall = AtomicReference<Instant?>(null)
    private val lastError = AtomicReference<String?>(null)
    
    // Metrics
    private val apiCallTimer = Timer.builder("nais_api_call_duration_seconds")
        .description("Duration of NAIS API calls")
        .tag("operation", "team_lookup")
        .register(appMicrometerRegistry)
    
    private val apiCallCounter = Counter.builder("nais_api_calls_total")
        .description("Total number of NAIS API calls")
        .tag("operation", "team_lookup")
        .register(appMicrometerRegistry)
    
    private val apiErrorCounter = Counter.builder("nais_api_errors_total")
        .description("Total number of NAIS API errors")
        .tag("operation", "team_lookup")
        .register(appMicrometerRegistry)
    
    private val cacheHitCounter = Counter.builder("nais_api_cache_hits_total")
        .description("Number of cache hits for NAIS API lookups")
        .register(appMicrometerRegistry)
    
    private val cacheMissCounter = Counter.builder("nais_api_cache_misses_total")
        .description("Number of cache misses for NAIS API lookups")
        .register(appMicrometerRegistry)

    /**
     * Get team slugs for a user by email.
     * Returns a set of team slugs (NAIS namespace names).
     */
    suspend fun getTeamSlugsForUser(email: String): Set<String> {
        return getTeamSlugsForUserResult(email).getOrDefault(emptySet())
    }
    
    /**
     * Get team slugs for a user by email with detailed result.
     * Allows callers to distinguish between "user has no teams" and "API error".
     */
    suspend fun getTeamSlugsForUserResult(email: String): NaisApiResult<Set<String>> {
        // Check cache first
        val cachedTeams = teamCache.get(email)
        if (cachedTeams != null) {
            cacheHitCounter.increment()
            // Filter out sentinel value for empty teams
            val teams = cachedTeams.filterNot { it == "__EMPTY__" }.toSet()
            log.debug("Cache hit for user teams (email=$email): $teams")
            return NaisApiResult.Success(teams)
        }
        
        cacheMissCounter.increment()
        apiCallCounter.increment()
        
        val startTime = System.nanoTime()
        
        val response = try {
            client.post(graphqlUrl) {
                contentType(ContentType.Application.Json)
                header("X-Api-Key", apiKey)
                setBody(
                    GraphQlRequest(
                        query = USER_TEAMS_QUERY,
                        variables = UserTeamsVariables(email = email)
                    )
                )
            }
        } catch (e: Exception) {
            recordError("Failed to call NAIS GraphQL for user teams (email=$email)", e)
            return NaisApiResult.Error("API call failed", e)
        } finally {
            apiCallTimer.record(Duration.ofNanos(System.nanoTime() - startTime))
        }

        if (response.status != HttpStatusCode.OK) {
            val errorMsg = "NAIS GraphQL returned non-OK status: ${response.status}"
            recordError(errorMsg)
            return NaisApiResult.Error(errorMsg)
        }

        val body = try {
            response.body<GraphQlResponse<UserTeamsData>>()
        } catch (e: Exception) {
            recordError("Failed to parse NAIS GraphQL response (email=$email)", e)
            return NaisApiResult.Error("Response parsing failed", e)
        }

        if (!body.errors.isNullOrEmpty()) {
            val errorMsg = "NAIS GraphQL returned errors: ${body.errors.joinToString { it.message }}"
            recordError(errorMsg)
            return NaisApiResult.Error(errorMsg)
        }

        val teams = body.data?.user?.teams?.nodes
            ?.mapNotNull { it.team.slug }
            ?.toSet()
            ?: emptySet()

        // Cache with appropriate TTL based on result
        val ttl = if (teams.isNotEmpty()) CacheTtl.HAS_TEAMS else CacheTtl.NO_TEAMS
        teamCache.set(email, teams, ttl)
        recordSuccess()
        
        log.debug("Fetched teams from NAIS API for user (email=$email): $teams (TTL: ${ttl.toMinutes()}m)")
        return NaisApiResult.Success(teams)
    }

    /**
     * Get team slugs for the current "viewer" (authenticated API key owner).
     * This is a fallback when email lookup doesn't work.
     */
    suspend fun getTeamSlugsForViewer(): Set<String> {
        return getTeamSlugsForViewerResult().getOrDefault(emptySet())
    }
    
    /**
     * Get team slugs for viewer with detailed result.
     */
    suspend fun getTeamSlugsForViewerResult(): NaisApiResult<Set<String>> {
        val viewerCacheKey = "__viewer__"
        
        val cachedTeams = teamCache.get(viewerCacheKey)
        if (cachedTeams != null) {
            cacheHitCounter.increment()
            val teams = cachedTeams.filterNot { it == "__EMPTY__" }.toSet()
            return NaisApiResult.Success(teams)
        }
        
        cacheMissCounter.increment()
        apiCallCounter.increment()
        
        val startTime = System.nanoTime()

        val response = try {
            client.post(graphqlUrl) {
                contentType(ContentType.Application.Json)
                header("X-Api-Key", apiKey)
                setBody(GraphQlRequest(query = VIEWER_TEAMS_QUERY, variables = EmptyVariables()))
            }
        } catch (e: Exception) {
            recordError("Failed to call NAIS GraphQL for viewer teams", e)
            return NaisApiResult.Error("API call failed", e)
        } finally {
            apiCallTimer.record(Duration.ofNanos(System.nanoTime() - startTime))
        }

        if (response.status != HttpStatusCode.OK) {
            val errorMsg = "NAIS GraphQL returned non-OK status: ${response.status}"
            recordError(errorMsg)
            return NaisApiResult.Error(errorMsg)
        }

        val body = try {
            response.body<GraphQlResponse<ViewerTeamsData>>()
        } catch (e: Exception) {
            recordError("Failed to parse NAIS GraphQL response for viewer teams", e)
            return NaisApiResult.Error("Response parsing failed", e)
        }

        if (!body.errors.isNullOrEmpty()) {
            val errorMsg = "NAIS GraphQL returned errors: ${body.errors.joinToString { it.message }}"
            recordError(errorMsg)
            return NaisApiResult.Error(errorMsg)
        }

        val me = body.data?.me
        val teams = if (me?.typename == "User") {
            me.teams?.nodes
                ?.mapNotNull { it.team.slug }
                ?.toSet()
                ?: emptySet()
        } else {
            log.debug("NAIS API 'me' query did not return a User type (was: ${me?.typename})")
            emptySet()
        }

        val ttl = if (teams.isNotEmpty()) CacheTtl.HAS_TEAMS else CacheTtl.NO_TEAMS
        teamCache.set(viewerCacheKey, teams, ttl)
        recordSuccess()
        
        return NaisApiResult.Success(teams)
    }
    
    /**
     * Check if the NAIS API is healthy.
     * Returns true if we've had a successful call recently (within 2 hours).
     */
    fun isHealthy(): Boolean {
        val lastSuccess = lastSuccessfulCall.get() ?: return true // No calls yet is OK
        val threshold = Instant.now(clock).minus(Duration.ofHours(2))
        return lastSuccess.isAfter(threshold)
    }
    
    /**
     * Get health status details for diagnostics.
     */
    fun getHealthStatus(): Map<String, Any?> {
        return mapOf(
            "healthy" to isHealthy(),
            "cacheHealthy" to teamCache.isHealthy(),
            "lastSuccessfulCall" to lastSuccessfulCall.get()?.toString(),
            "lastError" to lastError.get(),
            "graphqlUrl" to graphqlUrl.take(50) + "..."
        )
    }
    
    /**
     * Clear the cache. Useful for testing.
     */
    fun clearCache() {
        teamCache.clear()
        log.info("NAIS API cache cleared")
    }
    
    private fun recordSuccess() {
        lastSuccessfulCall.set(Instant.now(clock))
        lastError.set(null)
    }
    
    private fun recordError(message: String, cause: Throwable? = null) {
        apiErrorCounter.increment()
        lastError.set(message)
        if (cause != null) {
            log.warn(message, cause)
        } else {
            log.warn(message)
        }
    }

    companion object {
        private val USER_TEAMS_QUERY = """
            query UserTeams(${'$'}email: String) {
                user(email: ${'$'}email) {
                    teams(first: 200) {
                        nodes {
                            team {
                                slug
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        private val VIEWER_TEAMS_QUERY = """
            query ViewerTeams {
                me {
                    __typename
                    ... on User {
                        teams(first: 200) {
                            nodes {
                                team {
                                    slug
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        /**
         * Create a client from environment variables, or null if not configured.
         * 
         * Required env vars:
         * - NAIS_API_GRAPHQL_URL: The GraphQL endpoint (e.g., https://console.nav.cloud.nais.io/graphql)
         * - NAIS_API_KEY: API key for authentication
         * 
         * Optional env vars (for Valkey cache):
         * - VALKEY_URI_CACHE: Valkey connection URI
         * - VALKEY_USERNAME_CACHE: Valkey username
         * - VALKEY_PASSWORD_CACHE: Valkey password
         */
        fun fromEnvOrNull(): NaisGraphQlClient? {
            val url = System.getenv("NAIS_API_GRAPHQL_URL")?.takeIf { it.isNotBlank() }
            val key = System.getenv("NAIS_API_KEY")?.takeIf { it.isNotBlank() }

            if (url == null && key == null) {
                log.debug("NAIS API integration not configured (NAIS_API_GRAPHQL_URL and NAIS_API_KEY not set)")
                return null
            }

            require(!url.isNullOrBlank()) {
                "NAIS_API_GRAPHQL_URL must be set when NAIS API integration is enabled"
            }
            require(!key.isNullOrBlank()) {
                "NAIS_API_KEY must be set when NAIS API integration is enabled"
            }

            // Create cache (Valkey if configured, otherwise in-memory)
            val teamCache = ValkeyTeamCache.fromEnvOrFallback()
            
            log.info("NAIS API integration enabled (endpoint: ${url.take(50)}...)")
            return NaisGraphQlClient(graphqlUrl = url, apiKey = key, teamCache = teamCache)
        }
        
        /**
         * Create a client for testing with custom dependencies.
         */
        fun forTesting(
            graphqlUrl: String,
            apiKey: String,
            teamCache: TeamCache,
            clock: Clock = Clock.systemUTC(),
            client: HttpClient = defaultHttpClient()
        ): NaisGraphQlClient {
            return NaisGraphQlClient(
                graphqlUrl = graphqlUrl,
                apiKey = apiKey,
                teamCache = teamCache,
                clock = clock,
                client = client
            )
        }

        private fun defaultHttpClient(): HttpClient {
            return HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        }
                    )
                }
            }
        }
    }
}

@Serializable
private data class GraphQlRequest<V>(
    val query: String,
    val variables: V
)

@Serializable
private data class GraphQlResponse<D>(
    val data: D? = null,
    val errors: List<GraphQlError>? = null
)

@Serializable
private data class GraphQlError(
    val message: String
)

@Serializable
private data class UserTeamsVariables(
    val email: String
)

@Serializable
private class EmptyVariables

@Serializable
private data class UserTeamsData(
    val user: UserNode? = null
)

@Serializable
private data class ViewerTeamsData(
    val me: ViewerNode? = null
)

@Serializable
private data class ViewerNode(
    @SerialName("__typename")
    val typename: String,
    val teams: TeamMemberConnection? = null
)

@Serializable
private data class UserNode(
    val teams: TeamMemberConnection? = null
)

@Serializable
private data class TeamMemberConnection(
    val nodes: List<TeamMemberNode>? = null
)

@Serializable
private data class TeamMemberNode(
    val team: TeamNode
)

@Serializable
private data class TeamNode(
    val slug: String? = null,
    @SerialName("externalResources")
    val externalResources: JsonObject? = null
)
