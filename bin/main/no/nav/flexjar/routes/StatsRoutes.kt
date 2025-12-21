package no.nav.flexjar.routes

import io.ktor.http.*
import io.ktor.server.resources.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.flexjar.domain.DeviceStats
import no.nav.flexjar.domain.FeedbackStats
import no.nav.flexjar.domain.PathnameStats
import no.nav.flexjar.domain.RatingByDateEntry
import no.nav.flexjar.domain.StatsPeriod
import no.nav.flexjar.domain.StatsQuery
import no.nav.flexjar.domain.RatingDistribution
import no.nav.flexjar.domain.SurveyType
import no.nav.flexjar.domain.TimelineEntry
import no.nav.flexjar.domain.TimelineResponse
import no.nav.flexjar.domain.FILTER_ALL
import no.nav.flexjar.repository.FeedbackRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import no.nav.flexjar.repository.FeedbackStatsRepository

private val defaultFeedbackRepository = FeedbackRepository()
private val defaultStatsRepository = FeedbackStatsRepository()

fun Route.statsRoutes(
    repository: FeedbackRepository = defaultFeedbackRepository,
    statsRepository: FeedbackStatsRepository = defaultStatsRepository
) {
    // Get statistics for feedback
    get<ApiV1Intern.Stats> { params ->
        val team = params.team ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing team parameter")

        val query = StatsQuery(
            team = team,
            app = params.app?.takeIf { it != FILTER_ALL },
            from = params.from,
            to = params.to,
            feedbackId = params.feedbackId,
            deviceType = params.deviceType?.takeIf { it != FILTER_ALL }
        )
        
        val stats = statsRepository.getStats(query)
        
        val averageRating = calculateAverageRating(stats.byRating)
        val days = calculateDays(query.from, query.to)
        
        call.respond(FeedbackStats(
            totalCount = stats.totalCount.toInt(),
            countWithText = stats.countWithText.toInt(),
            countWithoutText = (stats.totalCount - stats.countWithText).toInt(),
            byRating = stats.byRating,
            byApp = stats.byApp,
            byDate = stats.byDate,
            byFeedbackId = stats.byFeedbackId,
            averageRating = averageRating,
            period = StatsPeriod(
                from = query.from,
                to = query.to,
                days = days
            ),
            surveyType = stats.surveyType?.let { 
                try { SurveyType.valueOf(it.uppercase()) } catch(e: Exception) { SurveyType.CUSTOM }
            }
        ))
    }
    
    // Get rating distribution
    get<ApiV1Intern.Stats.Ratings> { params ->
        val team = params.parent.team ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing team parameter")
        
        val query = StatsQuery(
            team = team,
            app = params.parent.app?.takeIf { it != FILTER_ALL },
            from = params.parent.from,
            to = params.parent.to,
            feedbackId = params.parent.feedbackId
        )
        
        val stats = statsRepository.getStats(query)
        
        call.respond(RatingDistribution(
            distribution = stats.byRating,
            average = calculateAverageRating(stats.byRating),
            total = stats.byRating.values.sum()
        ))
    }
    
    // Get timeline data
    get<ApiV1Intern.Stats.Timeline> { params ->
        val team = params.parent.team ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing team parameter")

        val query = StatsQuery(
            team = team,
            app = params.parent.app?.takeIf { it != FILTER_ALL },
            from = params.parent.from,
            to = params.parent.to,
            feedbackId = params.parent.feedbackId
        )
        
        val stats = statsRepository.getStats(query)
        
        call.respond(TimelineResponse(
            data = stats.byDate.map { (date, count) ->
                TimelineEntry(date = date, count = count)
            }.sortedBy { it.date }
        ))
    }

    // Get Top Tasks statistics
    get<ApiV1Intern.Stats.TopTasks> { params ->
        val team = params.parent.team ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing team parameter")

        val query = StatsQuery(
            team = team,
            app = params.parent.app?.takeIf { it != FILTER_ALL },
            from = params.parent.from,
            to = params.parent.to,
            feedbackId = params.parent.feedbackId,
            deviceType = params.parent.deviceType?.takeIf { it != FILTER_ALL }
        )
        
        val stats = statsRepository.getTopTasksStats(query)
        call.respond(stats)
    }
}

private fun calculateAverageRating(byRating: Map<String, Int>): Double? {
    val numericRatings = byRating.mapNotNull { (rating, count) ->
        rating.toIntOrNull()?.let { it to count }
    }
    
    if (numericRatings.isEmpty()) return null
    
    val totalSum = numericRatings.map { it.first * it.second }.sum()
    val totalCount = numericRatings.sumOf { it.second }
    
    return if (totalCount > 0) totalSum.toDouble() / totalCount else null
}

private fun calculateDays(from: String?, to: String?): Int {
    return try {
        val fromDate = from?.let { LocalDate.parse(it.take(10)) } ?: LocalDate.now().minusDays(30)
        val toDate = to?.let { LocalDate.parse(it.take(10)) } ?: LocalDate.now()
        ChronoUnit.DAYS.between(fromDate, toDate).toInt().coerceAtLeast(1)
    } catch (e: Exception) {
        30
    }
}
