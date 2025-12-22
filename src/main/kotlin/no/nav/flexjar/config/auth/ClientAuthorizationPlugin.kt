package no.nav.flexjar.config.auth

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import no.nav.flexjar.config.exception.ApiErrorException
import no.nav.flexjar.config.isProdEnv
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
    // With Texas, the BrukerPrincipal is now always set by the bearer auth
    val callerClientId = principal<BrukerPrincipal>()?.clientId
        ?: throw ApiErrorException.UnauthorizedException("Missing client identity in token")
    
    if (!allowedClients.contains(callerClientId)) {
        log.error(
            "Client authorization failed - expected: $allowedClients, actual: $callerClientId, path: ${request.uri}"
        )
        throw ApiErrorException.ForbiddenException("Caller is not authorized for this endpoint")
    }
}
