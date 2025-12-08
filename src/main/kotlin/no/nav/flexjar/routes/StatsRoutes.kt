package no.nav.flexjar.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.flexjar.domain.DeviceStats
import no.nav.flexjar.domain.FeedbackStats
import no.nav.flexjar.domain.PathnameStats
import no.nav.flexjar.domain.RatingByDateEntry
import no.nav.flexjar.domain.StatsPeriod
import no.nav.flexjar.domain.StatsQuery
import no.nav.flexjar.repository.FeedbackRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val feedbackRepository = FeedbackRepository()

fun Route.statsRoutes() {
    route("/api/v1/intern/stats") {
        // Get statistics for feedback
        get {
            val query = StatsQuery(
                team = call.request.queryParameters["team"] ?: "flex",
                app = call.request.queryParameters["app"]?.takeIf { it != "alle" },
                from = call.request.queryParameters["from"],
                to = call.request.queryParameters["to"],
                feedbackId = call.request.queryParameters["feedbackId"],
                deviceType = call.request.queryParameters["deviceType"]?.takeIf { it != "alle" }
            )
            
            val stats = feedbackRepository.getStats(query)
            
            @Suppress("UNCHECKED_CAST")
            val byRating = stats["byRating"] as? Map<String, Int> ?: emptyMap()
            
            // Calculate average rating if ratings are numeric
            val averageRating = calculateAverageRating(byRating)
            
            // Calculate period days
            val days = calculateDays(query.from, query.to)
            
            // Parse new aggregations
            @Suppress("UNCHECKED_CAST")
            val ratingByDateRaw = stats["ratingByDate"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val ratingByDate = ratingByDateRaw.mapValues { (_, v) ->
                RatingByDateEntry(
                    average = (v["average"] as? Number)?.toDouble() ?: 0.0,
                    count = (v["count"] as? Number)?.toInt() ?: 0
                )
            }
            
            @Suppress("UNCHECKED_CAST")
            val byDeviceRaw = stats["byDevice"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val byDevice = byDeviceRaw.mapValues { (_, v) ->
                DeviceStats(
                    count = (v["count"] as? Number)?.toInt() ?: 0,
                    averageRating = (v["averageRating"] as? Number)?.toDouble() ?: 0.0
                )
            }
            
            @Suppress("UNCHECKED_CAST")
            val byPathnameRaw = stats["byPathname"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val byPathname = byPathnameRaw.mapValues { (_, v) ->
                PathnameStats(
                    count = (v["count"] as? Number)?.toInt() ?: 0,
                    averageRating = (v["averageRating"] as? Number)?.toDouble() ?: 0.0
                )
            }
            
            call.respond(FeedbackStats(
                totalCount = (stats["totalCount"] as? Long)?.toInt() ?: 0,
                countWithText = (stats["countWithText"] as? Long)?.toInt() ?: 0,
                countWithoutText = ((stats["totalCount"] as? Long)?.toInt() ?: 0) - ((stats["countWithText"] as? Long)?.toInt() ?: 0),
                byRating = byRating,
                byApp = stats["byApp"] as? Map<String, Int> ?: emptyMap(),
                byDate = stats["byDate"] as? Map<String, Int> ?: emptyMap(),
                byFeedbackId = stats["byFeedbackId"] as? Map<String, Int> ?: emptyMap(),
                averageRating = averageRating,
                period = StatsPeriod(
                    from = query.from,
                    to = query.to,
                    days = days
                ),
                ratingByDate = ratingByDate,
                byDevice = byDevice,
                byPathname = byPathname
            ))
        }
        
        // Get rating distribution
        get("/ratings") {
            val query = StatsQuery(
                team = call.request.queryParameters["team"] ?: "flex",
                app = call.request.queryParameters["app"]?.takeIf { it != "alle" },
                from = call.request.queryParameters["from"],
                to = call.request.queryParameters["to"],
                feedbackId = call.request.queryParameters["feedbackId"]
            )
            
            val stats = feedbackRepository.getStats(query)
            
            @Suppress("UNCHECKED_CAST")
            val byRating = stats["byRating"] as? Map<String, Int> ?: emptyMap()
            
            call.respond(mapOf(
                "distribution" to byRating,
                "average" to calculateAverageRating(byRating),
                "total" to byRating.values.sum()
            ))
        }
        
        // Get timeline data
        get("/timeline") {
            val query = StatsQuery(
                team = call.request.queryParameters["team"] ?: "flex",
                app = call.request.queryParameters["app"]?.takeIf { it != "alle" },
                from = call.request.queryParameters["from"],
                to = call.request.queryParameters["to"],
                feedbackId = call.request.queryParameters["feedbackId"]
            )
            
            val stats = feedbackRepository.getStats(query)
            
            @Suppress("UNCHECKED_CAST")
            val byDate = stats["byDate"] as? Map<String, Int> ?: emptyMap()
            
            call.respond(mapOf(
                "data" to byDate.map { (date, count) ->
                    mapOf("date" to date, "count" to count)
                }.sortedBy { it["date"] as String }
            ))
        }
    }
}

private fun calculateAverageRating(byRating: Map<String, Int>): Double? {
    val numericRatings = byRating.mapNotNull { (rating, count) ->
        rating.toIntOrNull()?.let { it to count }
    }
    
    if (numericRatings.isEmpty()) return null
    
    val totalSum = numericRatings.sumOf { (rating, count) -> rating * count }
    val totalCount = numericRatings.sumOf { (_, count) -> count }
    
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
