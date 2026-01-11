package no.nav.flexjar.routes

import io.ktor.server.resources.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.flexjar.config.auth.authorizedTeam
import no.nav.flexjar.domain.FILTER_ALL
import no.nav.flexjar.domain.StatsQuery
import no.nav.flexjar.service.StatsService

private val defaultStatsService = StatsService()

/**
 * Helper to build StatsQuery from resource params.
 * Maps new param names (fromDate, toDate, surveyId) from resource.
 */
private fun buildStatsQuery(
    team: String,
    app: String?,
    fromDate: String?,
    toDate: String?,
    surveyId: String?,
    deviceType: String?
) = StatsQuery(
    team = team,
    app = app?.takeIf { it != FILTER_ALL },
    fromDate = fromDate,
    toDate = toDate,
    surveyId = surveyId,
    deviceType = deviceType?.takeIf { it != FILTER_ALL }
)

/**
 * Routes for feedback statistics endpoints.
 * Delegates business logic to StatsService.
 */
fun Route.statsRoutes(
    statsService: StatsService = defaultStatsService
) {
    // Get stats overview (new consolidated endpoint)
    get<ApiV1Intern.Stats.Overview> { params ->
        val team = call.authorizedTeam
        val p = params.parent
        
        val query = buildStatsQuery(team, p.app, p.fromDate, p.toDate, p.surveyId, p.deviceType)
        val overview = statsService.getStatsOverview(query)
        call.respond(overview)
    }

    // Get statistics for feedback (legacy endpoint, still functional)
    get<ApiV1Intern.Stats> { params ->
        val team = call.authorizedTeam

        val query = buildStatsQuery(team, params.app, params.fromDate, params.toDate, params.surveyId, params.deviceType)
        val stats = statsService.getStats(query)
        call.respond(stats)
    }
    
    // Get rating distribution
    get<ApiV1Intern.Stats.Ratings> { params ->
        val team = call.authorizedTeam
        val p = params.parent
        
        val query = buildStatsQuery(team, p.app, p.fromDate, p.toDate, p.surveyId, p.deviceType)
        val distribution = statsService.getRatingDistribution(query)
        call.respond(distribution)
    }
    
    // Get timeline data
    get<ApiV1Intern.Stats.Timeline> { params ->
        val team = call.authorizedTeam
        val p = params.parent

        val query = buildStatsQuery(team, p.app, p.fromDate, p.toDate, p.surveyId, p.deviceType)
        val timeline = statsService.getTimeline(query)
        call.respond(timeline)
    }

    // Get Top Tasks statistics
    get<ApiV1Intern.Stats.TopTasks> { params ->
        val team = call.authorizedTeam
        val p = params.parent

        val query = buildStatsQuery(team, p.app, p.fromDate, p.toDate, p.surveyId, p.deviceType)
        val stats = statsService.getTopTasksStats(query)
        call.respond(stats)
    }

    // Get Survey Type distribution
    get<ApiV1Intern.Stats.SurveyTypes> { params ->
        val team = call.authorizedTeam
        val p = params.parent

        val query = buildStatsQuery(team, p.app, p.fromDate, p.toDate, null, p.deviceType)
        val distribution = statsService.getSurveyTypeDistribution(query)
        call.respond(distribution)
    }
}
