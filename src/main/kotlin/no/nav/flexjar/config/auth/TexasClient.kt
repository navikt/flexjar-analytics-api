package no.nav.flexjar.config.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TexasClient")

/**
 * Client for NAIS Texas sidecar - Token Exchange as a Service.
 * Uses the introspection endpoint to validate tokens.
 * 
 * @param introspectionEndpoint The Texas sidecar introspection URL (from ServerEnv)
 */
class TexasClient(
    private val introspectionEndpoint: String
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }

    /**
     * Introspect a token using the Texas sidecar.
     * Returns the introspection result with claims if valid, or null if invalid.
     */
    suspend fun introspect(token: String): TexasIntrospectionResult? {
        return try {
            log.info("Introspecting token with Texas: $introspectionEndpoint")
            val response = client.post(introspectionEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TexasIntrospectionRequest(
                    identityProvider = "azuread",
                    token = token
                ))
            }
            
            if (response.status != HttpStatusCode.OK) {
                log.error("Texas introspection failed with status: ${response.status}")
                val body = response.body<String>()
                log.error("Response body: $body")
                return null
            }

            val result = response.body<TexasIntrospectionResult>()
            
            if (!result.active) {
                log.warn("Token validation failed - token is not active. Claims: $result")
                return null
            }
            
            result
        } catch (e: Exception) {
            log.error("Failed to introspect token", e)
            null
        }
    }
    
    fun close() {
        client.close()
    }
}

@Serializable
data class TexasIntrospectionRequest(
    @SerialName("identity_provider")
    val identityProvider: String,
    @SerialName("token")
    val token: String
)

@Serializable
data class TexasIntrospectionResult(
    val active: Boolean,
    val sub: String? = null,
    val aud: String? = null,
    val iss: String? = null,
    val exp: Long? = null,
    val iat: Long? = null,
    val azp: String? = null,
    /** The calling application's name in format "cluster:namespace:app" */
    val azp_name: String? = null,
    /** NAV employee identifier */
    val NAVident: String? = null,
    /** User's display name */
    val name: String? = null
)
