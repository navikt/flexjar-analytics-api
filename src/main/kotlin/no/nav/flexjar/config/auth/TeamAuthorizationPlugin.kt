package no.nav.flexjar.config.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import no.nav.flexjar.integrations.nais.NaisApiResult
import no.nav.flexjar.integrations.nais.NaisGraphQlClient
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TeamAuthorizationPlugin")

/**
 * Minimal abstraction so TeamAuthorizationPlugin can be tested without calling the real NAIS API.
 */
interface NaisTeamLookup {
    suspend fun getTeamSlugsForUserResult(email: String): NaisApiResult<Set<String>>
    suspend fun getTeamSlugsForViewerResult(): NaisApiResult<Set<String>>

    suspend fun getTeamSlugsForUser(email: String): Set<String> =
        getTeamSlugsForUserResult(email).getOrDefault(emptySet())

    suspend fun getTeamSlugsForViewer(): Set<String> =
        getTeamSlugsForViewerResult().getOrDefault(emptySet())
}

private class NaisGraphQlTeamLookup(private val client: NaisGraphQlClient) : NaisTeamLookup {
    override suspend fun getTeamSlugsForUserResult(email: String): NaisApiResult<Set<String>> =
        client.getTeamSlugsForUserResult(email)

    override suspend fun getTeamSlugsForViewerResult(): NaisApiResult<Set<String>> =
        client.getTeamSlugsForViewerResult()
}

class TeamAuthorizationConfig {
    /**
     * Provides a NAIS team lookup implementation (or null to disable NAIS lookup).
     * Defaults to env-based NAIS GraphQL client when configured.
     */
    var naisTeamLookupProvider: () -> NaisTeamLookup? = {
        NaisGraphQlClient.fromEnvOrNull()?.let { NaisGraphQlTeamLookup(it) }
    }
}

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
val TeamAuthorizationPlugin = createRouteScopedPlugin("TeamAuthorization", ::TeamAuthorizationConfig) {

    val naisLookup = pluginConfig.naisTeamLookupProvider()
    
    on(AuthenticationChecked) { call ->
        val principal = call.principal<BrukerPrincipal>()
        
        if (principal == null) {
            log.warn("TeamAuthorization: No principal found")
            call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
            return@on
        }
        
        val authorizedTeams = resolveAuthorizedTeams(principal, naisLookup)
        
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
            // Prefer the legacy primary team if it exists, otherwise pick a stable team from the resolved set.
            principal.getPrimaryTeam()?.takeIf { it in authorizedTeams }
                ?: authorizedTeams.sorted().first()
        }
        
        // Store in call attributes for route handlers
        call.attributes.put(AuthorizedTeamKey, team)
        call.attributes.put(AuthorizedTeamsKey, authorizedTeams)
        call.attributes.put(AuthorizedPrincipalKey, principal)
        
        log.debug("TeamAuthorization: User ${principal.navIdent} authorized for team: $team")
    }
}

private suspend fun resolveAuthorizedTeams(
    principal: BrukerPrincipal,
    naisLookup: NaisTeamLookup?
): Set<String> {
    if (naisLookup == null) {
        val teams = principal.getAuthorizedTeams()
        log.debug("NAIS API not configured, using AD group mapping. User ${principal.navIdent} authorized for teams: $teams")
        return teams
    }

    val email = principal.email

    val teamsByEmailResult: NaisApiResult<Set<String>> = if (!email.isNullOrBlank()) {
        naisLookup.getTeamSlugsForUserResult(email)
    } else {
        log.warn("User ${principal.navIdent} has no email claim, cannot lookup teams via NAIS API")
        NaisApiResult.Success(emptySet())
    }

    val resolvedTeamsResult: NaisApiResult<Set<String>> = when (teamsByEmailResult) {
        is NaisApiResult.Success -> {
            if (teamsByEmailResult.value.isNotEmpty()) {
                log.debug("Resolved teams from NAIS API (by email) for ${principal.navIdent}: ${teamsByEmailResult.value}")
                teamsByEmailResult
            } else {
                // Matches NAIS Console behavior: `me { ... on User { teams { ... }}}`.
                // Depending on NAIS API auth configuration, `me` might not be a User.
                val viewerTeamsResult = naisLookup.getTeamSlugsForViewerResult()
                if (viewerTeamsResult is NaisApiResult.Success && viewerTeamsResult.value.isNotEmpty()) {
                    log.debug("Resolved teams from NAIS API (viewer query) for ${principal.navIdent}: ${viewerTeamsResult.value}")
                }
                viewerTeamsResult
            }
        }
        is NaisApiResult.Error -> teamsByEmailResult
    }

    val fallbackTeams = principal.getAuthorizedTeams()

    return when (resolvedTeamsResult) {
        is NaisApiResult.Success -> {
            if (resolvedTeamsResult.value.isEmpty()) {
                // NAIS lookup succeeded but yielded no teams.
                log.info(
                    "NAIS API returned no teams, falling back to AD group mapping for ${principal.navIdent}: $fallbackTeams"
                )
                fallbackTeams
            } else {
                resolvedTeamsResult.value
            }
        }
        is NaisApiResult.Error -> {
            // NAIS lookup failed (e.g. 401/timeout). This is NOT an "empty teams" situation.
            val msg =
                "NAIS API team lookup failed (${resolvedTeamsResult.message}), falling back to AD group mapping for ${principal.navIdent}: $fallbackTeams"
            if (resolvedTeamsResult.message.contains("cached", ignoreCase = true)) {
                log.debug(msg)
            } else {
                log.warn(msg)
            }
            fallbackTeams
        }
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
