package no.nav.flexjar.config.auth

import io.ktor.server.application.*
import io.ktor.util.*
import no.nav.flexjar.config.getBrukerPrincipal
import no.nav.flexjar.config.exception.ApiErrorException

// Re-declare keys here to access via getOrNull (avoids internal implementation coupling)
private val AuthorizedTeamKey = AttributeKey<String>("authorizedTeam")
private val AuthorizedTeamsKey = AttributeKey<Set<String>>("authorizedTeams")

/**
 * Extension to get the authorized team for this call.
 * This team is resolved and validated by TeamAuthorizationPlugin.
 * 
 * Throws ForbiddenException if no team is authorized.
 */
val ApplicationCall.authorizedTeamSafe: String
    get() = attributes.getOrNull(AuthorizedTeamKey)
        ?: throw ApiErrorException.ForbiddenException("Access denied: No authorized team found for this request")

/**
 * Extension to get all teams the user is authorized for.
 * Returns empty set if authorization context is not available.
 */
val ApplicationCall.authorizedTeamsSafe: Set<String>
    get() = attributes.getOrNull(AuthorizedTeamsKey) ?: emptySet()

/**
 * Extension to get the validated principal.
 */
val ApplicationCall.brukerPrincipalSafe: BrukerPrincipal
    get() = this.getBrukerPrincipal() 
        ?: throw ApiErrorException.UnauthorizedException("Authentication required")

/**
 * Extension to get the validated caller identity for submission routes.
 */
val ApplicationCall.callerIdentitySafe: CallerIdentity
    get() = this.getCallerIdentity()
