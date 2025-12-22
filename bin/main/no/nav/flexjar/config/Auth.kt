package no.nav.flexjar.config

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking
import no.nav.flexjar.config.auth.BrukerPrincipal
import no.nav.flexjar.config.auth.TexasClient
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Auth")

const val AZURE_REALM = "azure"

// Texas client instance (lazy initialized)
private val texasClient by lazy { 
    val endpoint = ServerEnv.current.nais.tokenIntrospectionEndpoint
        ?: "http://localhost:8080/introspect"
    TexasClient(endpoint)
}

fun Application.configureAuth() {
    val env = ServerEnv.current
    val authEnabled = env.nais.isNais
    
    if (authEnabled) {
        logger.info("Authentication enabled using NAIS Texas sidecar")
        
        // Debug logging for Authorization header presence (skip internal endpoints)
        intercept(ApplicationCallPipeline.Plugins) {
            val path = call.request.uri
            // Skip logging for health check endpoints
            if (path.startsWith("/internal/")) {
                return@intercept
            }
            
            val header = call.request.header(HttpHeaders.Authorization)
            if (header != null) {
                logger.info("Request received with Authorization header (length: ${header.length}). Path: $path")
            } else {
                logger.warn("Request received WITHOUT Authorization header. Path: $path")
            }
        }

        install(Authentication) {
            bearer(AZURE_REALM) {
                realm = "flexjar-analytics-api"
                authenticate { tokenCredential ->
                    logger.info("Incoming request to protected route. Token length: ${tokenCredential.token.length}")
                    val start = System.currentTimeMillis()
                    val principal = validateTokenWithTexas(tokenCredential.token)
                    val duration = System.currentTimeMillis() - start
                    
                    if (principal == null) {
                        logger.error("Authentication failed: validateTokenWithTexas returned null after ${duration}ms")
                    } else {
                        logger.info("Authentication succeeded: User=${principal.navIdent}, Client=${principal.clientId} after ${duration}ms")
                    }
                    
                    principal
                }
            }
        }
    } else {
        logger.warn("Authentication is DISABLED - running in local/test mode")
        install(Authentication) {
            bearer(AZURE_REALM) {
                realm = "flexjar-analytics-test"
                authenticate { tokenCredential ->
                    if (tokenCredential.token.isNotBlank()) {
                        // Return a mock principal for local development
                        BrukerPrincipal(
                            navIdent = "Z999999",
                            name = "Lokal Utvikler",
                            token = "mock-token",
                            clientId = env.auth.flexjarAnalyticsClientId,
                            // Include both groups for local development
                            groups = listOf(
                                "5066bb56-7f19-4b49-ae48-f1ba66abf546", // isyfo
                                "ef4e9824-6f3a-4933-8f40-6edf5233d4d2"  // esyfo
                            )
                        )
                    } else null
                }
            }
        }
    }
}

/**
 * Validate token using NAIS Texas sidecar introspection endpoint.
 */
private fun validateTokenWithTexas(token: String): BrukerPrincipal? {
    return runBlocking {
        val result = texasClient.introspect(token)
        
        if (result == null) {
            logger.warn("Token validation failed - introspection returned null")
            return@runBlocking null
        }
        
        BrukerPrincipal(
            navIdent = result.NAVident,
            name = result.name,
            token = token,
            clientId = result.azp_name,
            groups = result.groups ?: emptyList()
        )
    }
}

/**
 * Extension to extract BrukerPrincipal from the authentication context.
 * Call this in authenticated routes to get the current user.
 */
fun ApplicationCall.getBrukerPrincipal(): BrukerPrincipal? {
    return principal<BrukerPrincipal>()
}

/**
 * Get the allowed client ID for flexjar-analytics frontend.
 * Format: "cluster:namespace:app" e.g. "dev-gcp:team-esyfo:flexjar-analytics"
 */
fun getFlexjarAnalyticsClientId(): String = ServerEnv.current.auth.flexjarAnalyticsClientId

fun getClusterName(): String = ServerEnv.current.nais.clusterName ?: "dev-gcp"

fun isProdEnv(): Boolean = ServerEnv.current.nais.isProd

fun isDev(): Boolean = ServerEnv.current.nais.isLocal

data class CallerIdentity(
    val team: String,
    val app: String,
    val navIdent: String?,
    val name: String?
)

/**
 * Extract caller identity from the principal's clientId (azp_name claim).
 * The clientId format is "cluster:namespace:app", e.g. "dev-gcp:team-esyfo:flexjar-analytics"
 */
fun extractCallerIdentityFromPrincipal(principal: BrukerPrincipal): CallerIdentity? {
    return try {
        val azpName = principal.clientId ?: return null
        val parts = azpName.split(":")
        if (parts.size < 3) return null
        
        CallerIdentity(
            team = parts[1],
            app = parts[2],
            navIdent = principal.navIdent,
            name = principal.name
        )
    } catch (e: Exception) {
        logger.error("Failed to extract caller identity from principal", e)
        null
    }
}

/**
 * Extract caller identity directly from a JWT token string.
 * Used for submission routes where we get the raw token from Authorization header.
 */
fun extractCallerIdentity(token: String): CallerIdentity? {
    return try {
        val decoded = JWT.decode(token)
        val azpName = decoded.getClaim("azp_name")?.asString() ?: return null
        val parts = azpName.split(":")
        if (parts.size < 3) return null
        
        CallerIdentity(
            team = parts[1],
            app = parts[2],
            navIdent = decoded.getClaim("NAVident")?.asString(),
            name = decoded.getClaim("name")?.asString()
        )
    } catch (e: Exception) {
        logger.error("Failed to extract caller identity from token", e)
        null
    }
}
