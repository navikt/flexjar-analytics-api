package no.nav.flexjar.config.auth

import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.flexjar.config.getBrukerPrincipal
import no.nav.flexjar.config.exception.ApiErrorException

/**
 * Extension to get the authorized team for this call.
 * This team is resolved and validated by TeamAuthorizationPlugin.
 * 
 * Throws ForbiddenException if no team is authorized.
 */
val ApplicationCall.authorizedTeamSafe: String
    get() = try {
        this.authorizedTeam
    } catch (e: IllegalStateException) {
        throw ApiErrorException.ForbiddenException("Access denied: No authorized team found for this request")
    }

/**
 * Extension to get all teams the user is authorized for.
 */
val ApplicationCall.authorizedTeamsSafe: Set<String>
    get() = try {
        this.authorizedTeams
    } catch (e: IllegalStateException) {
        emptySet()
    }

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
