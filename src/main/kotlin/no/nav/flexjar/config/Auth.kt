package no.nav.flexjar.config

import com.auth0.jwt.JWT
import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.flexjar.config.auth.BrukerPrincipal
import no.nav.security.token.support.v3.TokenValidationContextPrincipal
import no.nav.security.token.support.v3.tokenValidationSupport
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Auth")

const val AZURE_REALM = "azure"

// Environment variables for allowed client IDs
const val FLEXJAR_ANALYTICS_CLIENT_ID_ENV = "FLEXJAR_ANALYTICS_CLIENT_ID"

fun Application.configureAuth() {
    val authEnabled = !isDev()
    
    if (authEnabled) {
        install(Authentication) {
            tokenValidationSupport(
                name = AZURE_REALM,
                config = this@configureAuth.environment.config
            )
        }
    } else {
        log.warn("Authentication is DISABLED - running in local/test mode")
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
                            clientId = getFlexjarAnalyticsClientId()
                        )
                    } else null
                }
            }
        }
    }
}

/**
 * Extension to extract BrukerPrincipal from the token validation context.
 * Call this in authenticated routes to get the current user.
 */
fun ApplicationCall.getBrukerPrincipal(): BrukerPrincipal? {
    // First check if it's a BrukerPrincipal (local dev mode)
    principal<BrukerPrincipal>()?.let { return it }
    
    // Otherwise, extract from TokenValidationContextPrincipal (prod mode)
    val tokenContext = principal<TokenValidationContextPrincipal>()?.context ?: return null
    val jwtToken = tokenContext.getJwtToken(AZURE_REALM) ?: return null
    val claims = jwtToken.jwtTokenClaims
    
    return BrukerPrincipal(
        navIdent = claims.getStringClaim("NAVident"),
        name = claims.getStringClaim("name"),
        token = jwtToken.encodedToken,
        clientId = claims.getStringClaim("azp_name")
    )
}

/**
 * Get the allowed client ID for flexjar-analytics frontend.
 * Format: "cluster:namespace:app" e.g. "dev-gcp:flex:flexjar-analytics"
 */
fun getFlexjarAnalyticsClientId(): String {
    return System.getenv(FLEXJAR_ANALYTICS_CLIENT_ID_ENV)
        ?: "${getClusterName()}:flex:flexjar-analytics"
}

fun getClusterName(): String = System.getenv("NAIS_CLUSTER_NAME") ?: "dev-gcp"

fun isProdEnv(): Boolean = getClusterName() == "prod-gcp"

fun isDev(): Boolean = System.getenv("NAIS_CLUSTER_NAME") == null

data class CallerIdentity(
    val team: String,
    val app: String,
    val navIdent: String?,
    val name: String?
)

/**
 * Extract caller identity from the principal's clientId (azp_name claim).
 * The clientId format is "cluster:namespace:app", e.g. "dev-gcp:flex:flexjar-analytics"
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
        log.error("Failed to extract caller identity from principal", e)
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
        log.error("Failed to extract caller identity from token", e)
        null
    }
}
