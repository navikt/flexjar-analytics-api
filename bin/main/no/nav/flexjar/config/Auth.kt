package no.nav.flexjar.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking
import no.nav.flexjar.config.auth.BrukerPrincipal
import no.nav.flexjar.config.auth.CallerIdentity
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
        
        install(Authentication) {
            bearer(AZURE_REALM) {
                realm = "flexjar-analytics-api"
                authenticate { tokenCredential ->
                    validateTokenWithTexas(tokenCredential.token)
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
        
        val groups = result.groups ?: emptyList()
        logger.debug("Authenticated user ${result.NAVident} with ${groups.size} groups")
        
        BrukerPrincipal(
            navIdent = result.NAVident,
            name = result.name,
            token = token,
            clientId = result.azp_name,
            groups = groups
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

/**
 * Extract caller identity from the principal's clientId (azp_name claim).
 * The clientId format is "cluster:namespace:app", e.g. "dev-gcp:team-esyfo:flexjar-analytics"
 * 
 * NOTE: Always use this function instead of decoding JWT tokens directly.
 * The principal has already been validated by the Texas sidecar.
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
