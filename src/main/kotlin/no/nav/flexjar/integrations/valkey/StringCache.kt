package no.nav.flexjar.integrations.valkey

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import no.nav.flexjar.config.appMicrometerRegistry
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.exceptions.JedisConnectionException
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("StringCache")

/**
 * Simple key/value cache for small string payloads (typically JSON).
 * Implemented with Valkey (Redis) when configured, otherwise in-memory.
 */
interface StringCache {
    fun get(key: String): String?
    fun set(key: String, value: String, ttl: Duration)
    fun isHealthy(): Boolean
    fun clear()
}

class InMemoryStringCache : StringCache {
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

    override fun set(key: String, value: String, ttl: Duration) {
        val expiresAt = System.currentTimeMillis() + ttl.toMillis()
        cache[key] = CacheEntry(expiresAt, value)
    }

    override fun isHealthy(): Boolean = true

    override fun clear() {
        cache.clear()
        log.info("In-memory string cache cleared")
    }
}

class ValkeyStringCache private constructor(
    private val jedis: JedisPooled,
    private val keyPrefix: String,
    private val fallback: InMemoryStringCache = InMemoryStringCache()
) : StringCache {

    private val cacheHitCounter = Counter.builder("string_cache_hits_total")
        .description("Number of string cache hits")
        .register(appMicrometerRegistry)

    private val cacheMissCounter = Counter.builder("string_cache_misses_total")
        .description("Number of string cache misses")
        .register(appMicrometerRegistry)

    private val cacheErrorCounter = Counter.builder("string_cache_errors_total")
        .description("Number of string cache errors")
        .register(appMicrometerRegistry)

    private val cacheOperationTimer = Timer.builder("string_cache_operation_seconds")
        .description("Duration of string cache operations")
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
                value
            }
        } catch (e: JedisConnectionException) {
            cacheErrorCounter.increment()
            log.warn("Failed to get from Valkey string cache, using fallback", e)
            fallback.get(key)
        } catch (e: Exception) {
            cacheErrorCounter.increment()
            log.warn("Unexpected error getting from Valkey string cache", e)
            fallback.get(key)
        }
    }

    override fun set(key: String, value: String, ttl: Duration) {
        val fullKey = keyPrefix + key

        try {
            val startTime = System.nanoTime()
            jedis.setex(fullKey, ttl.seconds, value)
            cacheOperationTimer.record(Duration.ofNanos(System.nanoTime() - startTime))
        } catch (e: JedisConnectionException) {
            cacheErrorCounter.increment()
            log.warn("Failed to set in Valkey string cache, using fallback", e)
            fallback.set(key, value, ttl)
        } catch (e: Exception) {
            cacheErrorCounter.increment()
            log.warn("Unexpected error setting in Valkey string cache", e)
            fallback.set(key, value, ttl)
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
            log.info("Valkey string cache cleared (${keys.size} keys)")
        } catch (e: Exception) {
            log.warn("Failed to clear Valkey string cache", e)
            fallback.clear()
        }
    }

    companion object {
        /**
         * Create a ValkeyStringCache from NAIS environment variables.
         * Returns InMemoryStringCache if Valkey is not configured.
         */
        fun fromEnvOrFallback(keyPrefix: String): StringCache {
            val uri = System.getenv("VALKEY_URI_FLEXJAR_CACHE")
                ?: System.getenv("REDIS_URI_FLEXJAR_CACHE")

            if (uri.isNullOrBlank()) {
                log.info("Valkey not configured for string cache, using in-memory cache")
                return InMemoryStringCache()
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
                    JedisPooled(URI(normalizedUri))
                }

                jedis.ping()
                log.info("Valkey string cache connected successfully")
                ValkeyStringCache(jedis, keyPrefix)
            } catch (e: Exception) {
                log.error("Failed to connect to Valkey for string cache, using in-memory fallback", e)
                InMemoryStringCache()
            }
        }

        private fun normalizeUri(baseUri: String, username: String, password: String): URI {
            val uri = URI(baseUri)
            return URI(
                uri.scheme,
                "$username:$password",
                uri.host,
                uri.port,
                uri.path,
                uri.query,
                uri.fragment
            )
        }
    }
}
