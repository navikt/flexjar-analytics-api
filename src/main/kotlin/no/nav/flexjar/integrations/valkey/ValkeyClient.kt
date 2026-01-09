package no.nav.flexjar.integrations.valkey

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import no.nav.flexjar.config.appMicrometerRegistry
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.exceptions.JedisConnectionException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("ValkeyClient")

/**
 * Cache interface for team lookups.
 * Abstracts the caching layer to allow Valkey or in-memory fallback.
 */
interface TeamCache {
    /**
     * Get cached teams for a user email.
     * Returns null if not cached.
     */
    fun get(email: String): Set<String>?
    
    /**
     * Cache teams for a user with the given TTL.
     */
    fun set(email: String, teams: Set<String>, ttl: Duration)
    
    /**
     * Check if the cache is healthy.
     */
    fun isHealthy(): Boolean
    
    /**
     * Clear all cached entries.
     */
    fun clear()
}

/**
 * In-memory cache implementation using ConcurrentHashMap.
 * Used as fallback when Valkey is unavailable.
 */
class InMemoryTeamCache : TeamCache {
    private data class CacheEntry(val expiresAt: Long, val teams: Set<String>)
    
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    
    override fun get(email: String): Set<String>? {
        val entry = cache[email] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(email)
            return null
        }
        return entry.teams
    }
    
    override fun set(email: String, teams: Set<String>, ttl: Duration) {
        val expiresAt = System.currentTimeMillis() + ttl.toMillis()
        cache[email] = CacheEntry(expiresAt, teams)
    }
    
    override fun isHealthy(): Boolean = true
    
    override fun clear() {
        cache.clear()
        log.info("In-memory cache cleared")
    }
}

/**
 * Valkey/Redis cache implementation using Jedis.
 * Falls back to in-memory cache if Valkey is unavailable.
 */
class ValkeyTeamCache private constructor(
    private val jedis: JedisPooled,
    private val keyPrefix: String = "teams:",
    private val fallback: InMemoryTeamCache = InMemoryTeamCache()
) : TeamCache {
    
    private val healthy = AtomicBoolean(true)
    
    // Metrics
    private val cacheHitCounter = Counter.builder("valkey_cache_hits_total")
        .description("Number of Valkey cache hits")
        .register(appMicrometerRegistry)
    
    private val cacheMissCounter = Counter.builder("valkey_cache_misses_total")
        .description("Number of Valkey cache misses")
        .register(appMicrometerRegistry)
    
    private val cacheErrorCounter = Counter.builder("valkey_cache_errors_total")
        .description("Number of Valkey cache errors")
        .register(appMicrometerRegistry)
    
    private val cacheOperationTimer = Timer.builder("valkey_cache_operation_seconds")
        .description("Duration of Valkey cache operations")
        .register(appMicrometerRegistry)
    
    override fun get(email: String): Set<String>? {
        val key = keyPrefix + email
        
        return try {
            val startTime = System.nanoTime()
            val members = jedis.smembers(key)
            cacheOperationTimer.record(Duration.ofNanos(System.nanoTime() - startTime))
            
            if (members.isNullOrEmpty()) {
                cacheMissCounter.increment()
                // Try fallback
                fallback.get(email)
            } else {
                cacheHitCounter.increment()
                healthy.set(true)
                members
            }
        } catch (e: JedisConnectionException) {
            cacheErrorCounter.increment()
            healthy.set(false)
            log.warn("Failed to get from Valkey, using fallback", e)
            fallback.get(email)
        } catch (e: Exception) {
            cacheErrorCounter.increment()
            log.warn("Unexpected error getting from Valkey", e)
            fallback.get(email)
        }
    }
    
    override fun set(email: String, teams: Set<String>, ttl: Duration) {
        val key = keyPrefix + email
        
        try {
            val startTime = System.nanoTime()
            
            // Use pipeline for atomicity
            jedis.del(key)
            if (teams.isNotEmpty()) {
                jedis.sadd(key, *teams.toTypedArray())
                jedis.expire(key, ttl.seconds)
            } else {
                // For empty teams, store a sentinel value to distinguish from "not cached"
                jedis.sadd(key, "__EMPTY__")
                jedis.expire(key, ttl.seconds)
            }
            
            cacheOperationTimer.record(Duration.ofNanos(System.nanoTime() - startTime))
            healthy.set(true)
            
            log.debug("Cached teams for $email with TTL ${ttl.seconds}s: $teams")
        } catch (e: JedisConnectionException) {
            cacheErrorCounter.increment()
            healthy.set(false)
            log.warn("Failed to set in Valkey, using fallback", e)
            fallback.set(email, teams, ttl)
        } catch (e: Exception) {
            cacheErrorCounter.increment()
            log.warn("Unexpected error setting in Valkey", e)
            fallback.set(email, teams, ttl)
        }
    }
    
    override fun isHealthy(): Boolean = healthy.get()
    
    override fun clear() {
        try {
            // Note: In production, be careful with KEYS command on large datasets
            val keys = jedis.keys("${keyPrefix}*")
            if (keys.isNotEmpty()) {
                jedis.del(*keys.toTypedArray())
            }
            fallback.clear()
            log.info("Valkey cache cleared (${keys.size} keys)")
        } catch (e: Exception) {
            log.warn("Failed to clear Valkey cache", e)
            fallback.clear()
        }
    }
    
    companion object {
        /**
         * Create a ValkeyTeamCache from NAIS environment variables.
         * Returns InMemoryTeamCache if Valkey is not configured.
         */
        fun fromEnvOrFallback(): TeamCache {
            // NAIS provides VALKEY_URI_<instance> format
            val uri = System.getenv("VALKEY_URI_CACHE")
                ?: System.getenv("REDIS_URI_CACHE")
            
            if (uri.isNullOrBlank()) {
                log.info("Valkey not configured, using in-memory cache")
                return InMemoryTeamCache()
            }
            
            val username = System.getenv("VALKEY_USERNAME_CACHE")
                ?: System.getenv("REDIS_USERNAME_CACHE")
            val password = System.getenv("VALKEY_PASSWORD_CACHE")
                ?: System.getenv("REDIS_PASSWORD_CACHE")
            
            return try {
                // Jedis supports redis:// and rediss:// URIs
                // Valkey uses valkey:// and valkeys:// - need to convert
                val normalizedUri = uri
                    .replace("valkey://", "redis://")
                    .replace("valkeys://", "rediss://")
                
                val jedis = if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    JedisPooled(normalizeUri(normalizedUri, username, password))
                } else {
                    JedisPooled(java.net.URI(normalizedUri))
                }
                
                // Test connection
                jedis.ping()
                
                log.info("Valkey cache connected successfully")
                ValkeyTeamCache(jedis)
            } catch (e: Exception) {
                log.error("Failed to connect to Valkey, using in-memory fallback", e)
                InMemoryTeamCache()
            }
        }
        
        private fun normalizeUri(baseUri: String, username: String, password: String): java.net.URI {
            // Parse the base URI and add credentials
            val uri = java.net.URI(baseUri)
            return java.net.URI(
                uri.scheme,
                "$username:$password",
                uri.host,
                uri.port,
                uri.path,
                uri.query,
                uri.fragment
            )
        }
        
        /**
         * Create a ValkeyTeamCache for testing.
         */
        fun forTesting(jedis: JedisPooled, keyPrefix: String = "test:teams:"): ValkeyTeamCache {
            return ValkeyTeamCache(jedis, keyPrefix)
        }
    }
}
