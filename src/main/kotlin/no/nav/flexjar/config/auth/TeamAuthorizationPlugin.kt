package no.nav.flexjar.config.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TeamAuthorizationPlugin")

/**
 * Route-scoped plugin that enforces team authorization.
 * 
 * Usage:
 * ```
 * authenticate(AZURE_REALM) {
 *     install(TeamAuthorizationPlugin)
 *     
 *     get("/api/v1/intern/stats") {
 *         val team = call.authorizedTeam  // Already validated
 *         // ... use team
 *     }
 * }
 * ```
 */
val TeamAuthorizationPlugin = createRouteScopedPlugin("TeamAuthorization") {
    
    on(AuthenticationChecked) { call ->
        val principal = call.principal<BrukerPrincipal>()
        
        if (principal == null) {
            log.warn("TeamAuthorization: No principal found")
            call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
            return@on
        }
        
        val authorizedTeams = principal.getAuthorizedTeams()
        
        if (authorizedTeams.isEmpty()) {
            log.warn("TeamAuthorization: User ${principal.navIdent} has no authorized teams")
            call.respond(HttpStatusCode.Forbidden, mapOf(
                "error" to "NO_TEAM_ACCESS",
                "message" to "You don't have access to any teams in Flexjar Analytics",
                "details" to "To get access, your team needs to be onboarded. See the README for instructions.",
                "helpUrl" to "https://github.com/navikt/flexjar-analytics-api#getting-access"
            ))
            return@on
        }
        
        // Get requested team from query parameter
        val requestedTeam = call.request.queryParameters["team"]
        
        // Validate requested team or use primary team
        val team = if (requestedTeam != null && requestedTeam in authorizedTeams) {
            requestedTeam
        } else if (requestedTeam != null && requestedTeam !in authorizedTeams) {
            log.warn("TeamAuthorization: User ${principal.navIdent} requested unauthorized team: $requestedTeam")
            call.respond(HttpStatusCode.Forbidden, mapOf(
                "error" to "TEAM_NOT_AUTHORIZED",
                "message" to "You are not authorized for team: $requestedTeam"
            ))
            return@on
        } else {
            principal.getPrimaryTeam()!!
        }
        
        // Store in call attributes for route handlers
        call.attributes.put(AuthorizedTeamKey, team)
        call.attributes.put(AuthorizedTeamsKey, authorizedTeams)
        call.attributes.put(AuthorizedPrincipalKey, principal)
        
        log.debug("TeamAuthorization: User ${principal.navIdent} authorized for team: $team")
    }
}

// Attribute keys for storing authorization context
private val AuthorizedTeamKey = AttributeKey<String>("authorizedTeam")
private val AuthorizedTeamsKey = AttributeKey<Set<String>>("authorizedTeams")
private val AuthorizedPrincipalKey = AttributeKey<BrukerPrincipal>("authorizedPrincipal")

/**
 * Get the authorized team for this request.
 * Only available after TeamAuthorizationPlugin has run.
 */
val ApplicationCall.authorizedTeam: String
    get() = attributes[AuthorizedTeamKey]

/**
 * Get all teams the user is authorized for.
 * Only available after TeamAuthorizationPlugin has run.
 */
val ApplicationCall.authorizedTeams: Set<String>
    get() = attributes[AuthorizedTeamsKey]

/**
 * Get the authenticated principal.
 * Only available after TeamAuthorizationPlugin has run.
 */
val ApplicationCall.authorizedPrincipal: BrukerPrincipal
    get() = attributes[AuthorizedPrincipalKey]
