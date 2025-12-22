package no.nav.flexjar.config.auth

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TeamAuthorization")

/**
 * Team authorization using AD group → NAIS namespace mapping.
 * 
 * IMPORTANT: Team names MUST match NAIS namespace names exactly!
 * This ensures consistency between:
 * - Analytics routes (users viewing data via AD group membership)
 * - Submission routes (apps submitting data via azp_name namespace)
 * 
 * To add a new team:
 * 1. Find the team's Azure AD group UUID (Azure portal or ask team lead)
 * 2. Add mapping: AD group UUID → NAIS namespace name
 * 3. Update flexjar-analytics frontend NAIS spec to include the group claim
 * 
 * FUTURE: This manual mapping can be replaced with NAIS API integration
 * to dynamically fetch user team memberships. See implementation_plan.md.
 */
private val GROUP_TO_TEAM = mapOf(
    // isyfo - Intern Sykefraværsoppfølging (namespace: teamsykefravr)
    "5066bb56-7f19-4b49-ae48-f1ba66abf546" to "teamsykefravr",
    
    // esyfo - Ekstern Sykefraværsoppfølging (namespace: team-esyfo)
    "ef4e9824-6f3a-4933-8f40-6edf5233d4d2" to "team-esyfo",
    
    // Add more teams here as needed:
    // "<ad-group-uuid>" to "<nais-namespace>",
)

/**
 * Get all team names the user has access to based on their AD groups.
 */
fun BrukerPrincipal.getAuthorizedTeams(): Set<String> {
    val teams = groups
        .mapNotNull { groupId -> GROUP_TO_TEAM[groupId] }
        .toSet()
    
    if (teams.isEmpty()) {
        log.warn("User $navIdent has no authorized teams. Groups: $groups")
    } else {
        log.debug("User $navIdent authorized for teams: $teams")
    }
    
    return teams
}

/**
 * Check if user is authorized for a specific team.
 */
fun BrukerPrincipal.isAuthorizedForTeam(team: String): Boolean {
    return getAuthorizedTeams().contains(team)
}

/**
 * Get the user's primary team (first authorized team).
 * Returns null if user has no authorized teams.
 */
fun BrukerPrincipal.getPrimaryTeam(): String? {
    return getAuthorizedTeams().firstOrNull()
}
