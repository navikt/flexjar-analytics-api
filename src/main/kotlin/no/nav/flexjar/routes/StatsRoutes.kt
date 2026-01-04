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
 * Routes for feedback statistics endpoints.
 * Delegates business logic to StatsService.
 */
fun Route.statsRoutes(
    statsService: StatsService = defaultStatsService
) {
    // Get statistics for feedback
    get<ApiV1Intern.Stats> { params ->
        val team = call.authorizedTeam

        val query = StatsQuery(
            team = team,
            app = params.app?.takeIf { it != FILTER_ALL },
            from = params.from,
            to = params.to,
            feedbackId = params.feedbackId,
            deviceType = params.deviceType?.takeIf { it != FILTER_ALL }
        )
        
        val stats = statsService.getStats(query)
        call.respond(stats)
    }
    
    // Get rating distribution
    get<ApiV1Intern.Stats.Ratings> { params ->
        val team = call.authorizedTeam
        
        val query = StatsQuery(
            team = team,
            app = params.parent.app?.takeIf { it != FILTER_ALL },
            from = params.parent.from,
            to = params.parent.to,
            feedbackId = params.parent.feedbackId
        )
        
        val distribution = statsService.getRatingDistribution(query)
        call.respond(distribution)
    }
    
    // Get timeline data
    get<ApiV1Intern.Stats.Timeline> { params ->
        val team = call.authorizedTeam

        val query = StatsQuery(
            team = team,
            app = params.parent.app?.takeIf { it != FILTER_ALL },
            from = params.parent.from,
            to = params.parent.to,
            feedbackId = params.parent.feedbackId
        )
        
        val timeline = statsService.getTimeline(query)
        call.respond(timeline)
    }

    // Get Top Tasks statistics
    get<ApiV1Intern.Stats.TopTasks> { params ->
        val team = call.authorizedTeam

        val query = StatsQuery(
            team = team,
            app = params.parent.app?.takeIf { it != FILTER_ALL },
            from = params.parent.from,
            to = params.parent.to,
            feedbackId = params.parent.feedbackId,
            deviceType = params.parent.deviceType?.takeIf { it != FILTER_ALL }
        )
        
        val stats = statsService.getTopTasksStats(query)
        call.respond(stats)
    }

    // Get Survey Type distribution
    get<ApiV1Intern.Stats.SurveyTypes> { params ->
        val team = call.authorizedTeam

        val query = StatsQuery(
            team = team,
            app = params.parent.app?.takeIf { it != FILTER_ALL },
            from = params.parent.from,
            to = params.parent.to,
            feedbackId = null, // Don't filter by single survey - we want all
            deviceType = params.parent.deviceType?.takeIf { it != FILTER_ALL }
        )
        
        val distribution = statsService.getSurveyTypeDistribution(query)
        call.respond(distribution)
    }
}
