package no.nav.flexjar.config.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking
import no.nav.flexjar.config.ServerEnv
import no.nav.flexjar.config.exception.ApiErrorException
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SubmissionAuthPlugin")

/**
 * Authentication plugin for submission routes.
 * 
 * This plugin:
 * 1. Requires a valid Azure AD token (via Texas introspection)
 * 2. Extracts caller identity from the token
 * 3. Allows ANY authenticated NAIS app to submit (no whitelist)
 * 
 * The caller's identity (team/app) is stored as request attributes
 * for use by the route handlers.
 */
class SubmissionAuthPluginConfig {
    // No configuration needed - we allow all authenticated apps
}

val SubmissionAuthPlugin = createRouteScopedPlugin(
    name = "SubmissionAuthPlugin",
    createConfiguration = ::SubmissionAuthPluginConfig,
) {
    val env = ServerEnv.current
    val isNais = env.nais.isNais
    
    val texasClient = if (isNais && env.nais.tokenIntrospectionEndpoint != null) {
        TexasClient(env.nais.tokenIntrospectionEndpoint)
    } else {
        null
    }
    
    onCall { call ->
        if (!isNais) {
            // Local dev - create mock identity
            log.warn("SubmissionAuthPlugin: Running in local mode, using mock identity")
            call.attributes.put(CallerIdentityKey, CallerIdentity(
                team = "local-dev",
                app = "local-app",
                navIdent = null,
                name = null
            ))
            return@onCall
        }
        
        // Production - require valid token
        val authHeader = call.request.header(HttpHeaders.Authorization)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("SubmissionAuthPlugin: Missing or invalid Authorization header")
            throw ApiErrorException.UnauthorizedException("Authorization header required")
        }
        
        val token = authHeader.removePrefix("Bearer ")
        
        // Validate token via Texas
        val introspectionResult = runBlocking {
            texasClient?.introspect(token)
        }
        
        if (introspectionResult == null || !introspectionResult.active) {
            log.warn("SubmissionAuthPlugin: Token validation failed")
            throw ApiErrorException.UnauthorizedException("Invalid or expired token")
        }
        
        // Extract caller identity from azp_name claim
        // Format: "cluster:namespace:app" e.g. "dev-gcp:team-esyfo:my-app"
        val azpName = introspectionResult.azp_name
        if (azpName.isNullOrBlank()) {
            log.error("SubmissionAuthPlugin: Missing azp_name claim in token")
            throw ApiErrorException.UnauthorizedException("Missing caller identity in token")
        }
        
        val parts = azpName.split(":")
        if (parts.size < 3) {
            log.error("SubmissionAuthPlugin: Invalid azp_name format: $azpName")
            throw ApiErrorException.UnauthorizedException("Invalid caller identity format")
        }
        
        val identity = CallerIdentity(
            team = parts[1],  // namespace = team
            app = parts[2],
            navIdent = introspectionResult.NAVident,
            name = introspectionResult.name
        )
        
        log.info("SubmissionAuthPlugin: Authenticated submission from team=${identity.team} app=${identity.app}")
        
        // Store identity in request attributes for route handlers
        call.attributes.put(CallerIdentityKey, identity)
    }
}

/**
 * Attribute key for storing caller identity in request.
 */
val CallerIdentityKey = io.ktor.util.AttributeKey<CallerIdentity>("CallerIdentity")

/**
 * Extension to get caller identity from the request.
 * Throws if not authenticated.
 */
fun ApplicationCall.getCallerIdentity(): CallerIdentity {
    return attributes.getOrNull(CallerIdentityKey)
        ?: throw ApiErrorException.UnauthorizedException("Caller identity not found - authentication required")
}

