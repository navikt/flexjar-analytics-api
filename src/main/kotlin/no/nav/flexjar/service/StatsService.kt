package no.nav.flexjar.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.flexjar.domain.*
import no.nav.flexjar.integrations.valkey.StatsCache
import no.nav.flexjar.integrations.valkey.ValkeyStatsCache
import no.nav.flexjar.repository.FeedbackRepository
import no.nav.flexjar.repository.FeedbackStatsRepository
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val json = Json { 
    ignoreUnknownKeys = true 
    encodeDefaults = true
}

/**
 * Service layer for feedback statistics.
 * Contains business logic for calculating and aggregating feedback statistics.
 * 
 * Uses StatsCache (Valkey with in-memory fallback) to cache expensive aggregations.
 * Default TTL: 5 minutes.
 */
class StatsService(
    private val feedbackRepository: FeedbackRepository = FeedbackRepository(),
    private val statsRepository: FeedbackStatsRepository = FeedbackStatsRepository(),
    private val statsCache: StatsCache = ValkeyStatsCache.fromEnvOrFallback(),
    private val cacheTtl: Duration = Duration.ofMinutes(5)
) {
    /**
     * Get comprehensive feedback statistics for the given query.
     * Results are cached for 5 minutes.
     */
    fun getStats(query: StatsQuery): FeedbackStats {
        val cacheKey = "stats:${query.hashCode()}"
        
        // Try cache first
        statsCache.get(cacheKey)?.let { cached ->
            return try {
                json.decodeFromString<FeedbackStats>(cached)
            } catch (e: Exception) {
                computeStats(query).also { statsCache.set(cacheKey, json.encodeToString(it), cacheTtl) }
            }
        }
        
        // Compute and cache
        return computeStats(query).also { 
            statsCache.set(cacheKey, json.encodeToString(it), cacheTtl) 
        }
    }
    
    private fun computeStats(query: StatsQuery): FeedbackStats {
        val stats = statsRepository.getStats(query)
        
        val averageRating = calculateAverageRating(stats.byRating)
        val days = calculateDays(query.fromDate, query.toDate)
        
        // Build privacy info if data should be masked
        val privacy = if (stats.masked) {
            PrivacyInfo(
                masked = true,
                reason = "Antall svar (${stats.totalCount}) er under ${stats.threshold}. Statistikk vises ikke av hensyn til personvern.",
                threshold = stats.threshold
            )
        } else null
        
        return FeedbackStats(
            totalCount = stats.totalCount.toInt(),
            countWithText = stats.countWithText.toInt(),
            countWithoutText = (stats.totalCount - stats.countWithText).toInt(),
            byRating = if (stats.masked) emptyMap() else stats.byRating,
            byApp = if (stats.masked) emptyMap() else stats.byApp,
            byDate = if (stats.masked) emptyMap() else stats.byDate,
            bySurveyId = if (stats.masked) emptyMap() else stats.bySurveyId,
            averageRating = if (stats.masked) null else averageRating,
            period = StatsPeriod(
                from = query.fromDate,
                to = query.toDate,
                days = days
            ),
            surveyType = stats.surveyType?.let { 
                try { SurveyType.valueOf(it.uppercase()) } catch(e: Exception) { SurveyType.CUSTOM }
            },
            privacy = privacy
        )
    }

    /**
     * Get stats overview (new consolidated endpoint per GPT contract).
     */
    fun getStatsOverview(query: StatsQuery): StatsOverviewResponse {
        val stats = statsRepository.getStats(query)
        
        // Calculate low rating count (ratings 1-2)
        val lowRatingCount = stats.byRating
            .filter { (rating, _) -> rating.toIntOrNull()?.let { it <= 2 } ?: false }
            .values.sum()
        
        return StatsOverviewResponse(
            generatedAt = java.time.Instant.now().toString(),
            range = if (query.fromDate != null || query.toDate != null) {
                DateRange(fromDate = query.fromDate, toDate = query.toDate)
            } else null,
            totals = StatsTotals(
                feedbackCount = stats.totalCount.toInt(),
                textCount = stats.countWithText.toInt(),
                lowRatingCount = lowRatingCount
            ),
            ratingDistribution = stats.byRating
        )
    }

    /**
     * Get rating distribution for the given query.
     */
    fun getRatingDistribution(query: StatsQuery): RatingDistribution {
        val stats = statsRepository.getStats(query)
        
        return RatingDistribution(
            distribution = stats.byRating,
            average = calculateAverageRating(stats.byRating),
            total = stats.byRating.values.sum()
        )
    }

    /**
     * Get timeline data for the given query.
     */
    fun getTimeline(query: StatsQuery): TimelineResponse {
        val stats = statsRepository.getStats(query)
        
        return TimelineResponse(
            data = stats.byDate.map { (date, count) ->
                TimelineEntry(date = date, count = count)
            }.sortedBy { it.date }
        )
    }

    /**
     * Get Top Tasks statistics for the given query.
     */
    fun getTopTasksStats(query: StatsQuery): TopTasksResponse {
        return statsRepository.getTopTasksStats(query)
    }

    /**
     * Get Survey Type distribution for the given query.
     */
    fun getSurveyTypeDistribution(query: StatsQuery): SurveyTypeDistribution {
        return statsRepository.getSurveyTypeDistribution(query)
    }

    /**
     * Calculate average rating from rating distribution.
     * Returns null if no numeric ratings found.
     */
    fun calculateAverageRating(byRating: Map<String, Int>): Double? {
        val numericRatings = byRating.mapNotNull { (rating, count) ->
            rating.toIntOrNull()?.let { it to count }
        }
        
        if (numericRatings.isEmpty()) return null
        
        val totalSum = numericRatings.map { it.first * it.second }.sum()
        val totalCount = numericRatings.sumOf { it.second }
        
        return if (totalCount > 0) totalSum.toDouble() / totalCount else null
    }

    /**
     * Calculate number of days in the query period.
     */
    fun calculateDays(from: String?, to: String?): Int {
        return try {
            val fromDate = from?.let { LocalDate.parse(it.take(10)) } ?: LocalDate.now().minusDays(30)
            val toDate = to?.let { LocalDate.parse(it.take(10)) } ?: LocalDate.now()
            ChronoUnit.DAYS.between(fromDate, toDate).toInt().coerceAtLeast(1)
        } catch (e: Exception) {
            30
        }
    }
}
