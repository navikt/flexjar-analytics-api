package no.nav.flexjar.config

import com.auth0.jwt.JWT
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import no.nav.security.token.support.v3.TokenValidationContextPrincipal
import no.nav.security.token.support.v3.tokenValidationSupport
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Auth")

const val AZURE_REALM = "azure"

fun Application.configureAuth() {
    val config = AuthConfig.fromEnvironment()
    
    if (config.enabled) {
        install(Authentication) {
            tokenValidationSupport(
                name = AZURE_REALM,
                config = this@configureAuth.environment.config
            )
        }
    } else {
        log.warn("Authentication is DISABLED - running in local/test mode")
        install(Authentication) {
            provider(AZURE_REALM) {
                authenticate { context ->
                    // Return a mock principal for local development
                    object : Principal {}
                }
            }
        }
    }
}

data class AuthConfig(
    val enabled: Boolean,
    val allowedApps: List<AllowedApp>
) {
    companion object {
        fun fromEnvironment(): AuthConfig {
            val cluster = System.getenv("NAIS_CLUSTER_NAME")
            return AuthConfig(
                enabled = cluster != null,
                allowedApps = listOf(
                    AllowedApp(namespace = "flex", app = "flexjar-analytics"),
                    // Keep backwards compatibility with old frontend during migration
                    AllowedApp(namespace = "flex", app = "flexjar-frontend")
                )
            )
        }
    }
}

data class AllowedApp(
    val namespace: String,
    val app: String
) {
    fun matches(azpName: String): Boolean {
        // azpName format: "dev-gcp:namespace:app" or "prod-gcp:namespace:app"
        val parts = azpName.split(":")
        if (parts.size < 3) return false
        return parts[1] == namespace && parts[2] == app
    }
}

fun validateClientApp(azpName: String?, allowedApps: List<AllowedApp>): Boolean {
    if (azpName == null) return false
    return allowedApps.any { it.matches(azpName) }
}

data class CallerIdentity(
    val team: String,
    val app: String,
    val navIdent: String?,
    val name: String?
)

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
