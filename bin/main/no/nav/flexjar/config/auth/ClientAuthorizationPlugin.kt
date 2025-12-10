package no.nav.flexjar.config.auth

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import no.nav.flexjar.config.AZURE_REALM
import no.nav.flexjar.config.exception.ApiErrorException
import no.nav.flexjar.config.isProdEnv
import no.nav.security.token.support.v3.TokenValidationContextPrincipal
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ClientAuthorizationPlugin")

class ClientAuthorizationPluginConfig {
    lateinit var allowedClientId: String
}

val ClientAuthorizationPlugin = createRouteScopedPlugin(
    name = "ClientAuthorizationPlugin",
    createConfiguration = ::ClientAuthorizationPluginConfig,
) {
    val clientId = pluginConfig.allowedClientId
    
    // In dev, also allow token generators for testing
    val allowedClients = if (isProdEnv()) {
        listOf(clientId)
    } else {
        listOf(clientId, "dev-gcp:nais:tokenx-token-generator", "dev-gcp:nais:azure-token-generator")
    }
    
    onCall { call ->
        call.requireClient(allowedClients)
    }
}

private fun ApplicationCall.requireClient(allowedClients: List<String>) {
    // First check if it's a BrukerPrincipal (local dev mode)
    val callerClientId = principal<BrukerPrincipal>()?.clientId
        // Otherwise, extract from TokenValidationContextPrincipal (prod mode)
        ?: principal<TokenValidationContextPrincipal>()?.context?.getJwtToken(AZURE_REALM)
            ?.jwtTokenClaims?.getStringClaim("azp_name")
        ?: throw ApiErrorException.UnauthorizedException("Missing azp_name claim in token")
    
    if (!allowedClients.contains(callerClientId)) {
        log.error(
            "Client authorization failed - expected: $allowedClients, actual: $callerClientId, path: ${request.uri}"
        )
        throw ApiErrorException.ForbiddenException("Caller is not authorized for this endpoint")
    }
}
