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
    
    log.info("ClientAuthorizationPlugin initialized with allowedClients: $allowedClients")
    
    on(AuthenticationChecked) { call ->
        call.requireClient(allowedClients)
    }
}

private fun ApplicationCall.requireClient(allowedClients: List<String>) {
    val principal = principal<BrukerPrincipal>()
    
    if (principal == null) {
        log.error("No BrukerPrincipal found in request to ${request.uri}")
        throw ApiErrorException.UnauthorizedException("Missing authentication principal")
    }
    
    val callerClientId = principal.clientId
    if (callerClientId == null) {
        log.error("BrukerPrincipal has null clientId. NAVident=${principal.navIdent}, name=${principal.name}, path=${request.uri}")
        throw ApiErrorException.UnauthorizedException("Missing client identity (azp_name) in token")
    }
    
    if (!allowedClients.contains(callerClientId)) {
        log.error(
            "Client authorization failed - expected: $allowedClients, actual: $callerClientId, path: ${request.uri}"
        )
        throw ApiErrorException.ForbiddenException("Caller is not authorized for this endpoint")
    }
    
    log.debug("Client authorized: $callerClientId for ${request.uri}")
}

