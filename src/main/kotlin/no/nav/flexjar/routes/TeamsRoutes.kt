package no.nav.flexjar.routes

import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import no.nav.flexjar.config.auth.authorizedTeams
import no.nav.flexjar.domain.TeamsAndApps
import no.nav.flexjar.repository.FeedbackRepository

private val defaultRepository = FeedbackRepository()

fun Route.teamsRoutes(
    feedbackRepository: FeedbackRepository = defaultRepository,
) {
    get<ApiV1Intern.Teams> {
        val teams = call.authorizedTeams

        val teamsToApps = teams
            .sorted()
            .associateWith { team ->
                feedbackRepository.findDistinctApps(team).toSet()
            }

        call.respond(TeamsAndApps(teams = teamsToApps))
    }
}
