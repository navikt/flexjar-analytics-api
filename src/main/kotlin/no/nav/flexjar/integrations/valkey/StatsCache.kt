package no.nav.flexjar.integrations.valkey

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.flexjar.config.appMicrometerRegistry
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.exceptions.JedisConnectionException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("StatsCache")

/**
 * Cache interface for stats data.
 * Abstracts the caching layer to allow Valkey or in-memory fallback.
 */
interface StatsCache {
    /**
     * Get cached stats for a cache key.
     * Returns null if not cached.
     */
    fun get(key: String): String?
    
    /**
     * Cache stats JSON with the given TTL.
     */
    fun set(key: String, jsonValue: String, ttl: Duration = Duration.ofMinutes(5))
    
    /**
     * Check if the cache is healthy.
     */
    fun isHealthy(): Boolean
    
    /**
     * Clear all cached stats.
     */
    fun clear()
}

/**
 * In-memory stats cache implementation.
 * Used as fallback when Valkey is unavailable.
 */
class InMemoryStatsCache : StatsCache {
    private data class CacheEntry(val expiresAt: Long, val value: String)
    
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    
    override fun get(key: String): String? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(key)
            return null
        }
        return entry.value
    }
    
    override fun set(key: String, jsonValue: String, ttl: Duration) {
        val expiresAt = System.currentTimeMillis() + ttl.toMillis()
        cache[key] = CacheEntry(expiresAt, jsonValue)
    }
    
    override fun isHealthy(): Boolean = true
    
    override fun clear() {
        cache.clear()
        log.info("In-memory stats cache cleared")
    }
}

/**
 * Valkey/Redis stats cache implementation using Jedis.
 * Falls back to in-memory cache if Valkey is unavailable.
 * 
 * Default TTL: 5 minutes for stats data.
 */
class ValkeyStatsCache private constructor(
    private val jedis: JedisPooled,
    private val keyPrefix: String = "stats:",
    private val fallback: InMemoryStatsCache = InMemoryStatsCache()
) : StatsCache {
    
    // Metrics
    private val cacheHitCounter = Counter.builder("stats_cache_hits_total")
        .description("Number of stats cache hits")
        .register(appMicrometerRegistry)
    
    private val cacheMissCounter = Counter.builder("stats_cache_misses_total")
        .description("Number of stats cache misses")
        .register(appMicrometerRegistry)
    
    private val cacheErrorCounter = Counter.builder("stats_cache_errors_total")
        .description("Number of stats cache errors")
        .register(appMicrometerRegistry)
    
    private val cacheOperationTimer = Timer.builder("stats_cache_operation_seconds")
        .description("Duration of stats cache operations")
        .register(appMicrometerRegistry)
    
    override fun get(key: String): String? {
        val fullKey = keyPrefix + key
        
        return try {
            val startTime = System.nanoTime()
            val value = jedis.get(fullKey)
            cacheOperationTimer.record(Duration.ofNanos(System.nanoTime() - startTime))
            
            if (value.isNullOrEmpty()) {
                cacheMissCounter.increment()
                fallback.get(key)
            } else {
                cacheHitCounter.increment()
                log.debug("Stats cache hit for key: $key")
                value
            }
        } catch (e: JedisConnectionException) {
            cacheErrorCounter.increment()
            log.warn("Failed to get from Valkey stats cache, using fallback", e)
            fallback.get(key)
        } catch (e: Exception) {
            cacheErrorCounter.increment()
            log.warn("Unexpected error getting from Valkey stats cache", e)
            fallback.get(key)
        }
    }
    
    override fun set(key: String, jsonValue: String, ttl: Duration) {
        val fullKey = keyPrefix + key
        
        try {
            val startTime = System.nanoTime()
            jedis.setex(fullKey, ttl.seconds, jsonValue)
            cacheOperationTimer.record(Duration.ofNanos(System.nanoTime() - startTime))
            
            log.debug("Cached stats for key: $key (TTL: ${ttl.seconds}s)")
        } catch (e: JedisConnectionException) {
            cacheErrorCounter.increment()
            log.warn("Failed to set in Valkey stats cache, using fallback", e)
            fallback.set(key, jsonValue, ttl)
        } catch (e: Exception) {
            cacheErrorCounter.increment()
            log.warn("Unexpected error setting in Valkey stats cache", e)
            fallback.set(key, jsonValue, ttl)
        }
    }
    
    override fun isHealthy(): Boolean {
        return try {
            jedis.ping() == "PONG"
        } catch (e: Exception) {
            false
        }
    }
    
    override fun clear() {
        try {
            val keys = jedis.keys("${keyPrefix}*")
            if (keys.isNotEmpty()) {
                jedis.del(*keys.toTypedArray())
            }
            fallback.clear()
            log.info("Valkey stats cache cleared (${keys.size} keys)")
        } catch (e: Exception) {
            log.warn("Failed to clear Valkey stats cache", e)
            fallback.clear()
        }
    }
    
    companion object {
        /**
         * Create a ValkeyStatsCache from NAIS environment variables.
         * Returns InMemoryStatsCache if Valkey is not configured.
         */
        fun fromEnvOrFallback(): StatsCache {
            val uri = System.getenv("VALKEY_URI_FLEXJAR_CACHE")
                ?: System.getenv("REDIS_URI_FLEXJAR_CACHE")
            
            if (uri.isNullOrBlank()) {
                log.info("Valkey not configured for stats cache, using in-memory cache")
                return InMemoryStatsCache()
            }
            
            val username = System.getenv("VALKEY_USERNAME_FLEXJAR_CACHE")
                ?: System.getenv("REDIS_USERNAME_FLEXJAR_CACHE")
            val password = System.getenv("VALKEY_PASSWORD_FLEXJAR_CACHE")
                ?: System.getenv("REDIS_PASSWORD_FLEXJAR_CACHE")
            
            return try {
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
                
                log.info("Valkey stats cache connected successfully")
                ValkeyStatsCache(jedis)
            } catch (e: Exception) {
                log.error("Failed to connect to Valkey for stats cache, using in-memory fallback", e)
                InMemoryStatsCache()
            }
        }
        
        private fun normalizeUri(baseUri: String, username: String, password: String): java.net.URI {
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
         * Create for testing with custom Jedis.
         */
        fun forTesting(jedis: JedisPooled, keyPrefix: String = "test:stats:"): ValkeyStatsCache {
            return ValkeyStatsCache(jedis, keyPrefix)
        }
    }
}
