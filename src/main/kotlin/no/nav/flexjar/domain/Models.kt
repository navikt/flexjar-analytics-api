package no.nav.flexjar.domain

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.OffsetDateTime

/**
 * Value used by frontend to indicate "all" (no filter).
 * When received as a query parameter, should be treated as null/unfiltered.
 */
const val FILTER_ALL = "alle"

/**
 * Database record for feedback
 */
data class FeedbackDbRecord(
    val id: String,
    val opprettet: OffsetDateTime,
    val feedbackJson: String,
    val team: String,
    val app: String,
    val tags: String?
)

@Serializable
enum class SurveyType {
    @SerialName("rating") RATING,
    @SerialName("topTasks") TOP_TASKS,
    @SerialName("discovery") DISCOVERY,
    @SerialName("taskPriority") TASK_PRIORITY,
    @SerialName("custom") CUSTOM
}

// ============================================
// Structured Answer Types (new format)
// ============================================

@Serializable
enum class FieldType {
    RATING,
    TEXT,
    SINGLE_CHOICE,
    MULTI_CHOICE,
    DATE
}

@Serializable
data class ChoiceOption(
    val id: String,
    val label: String
)

@Serializable
data class Question(
    val label: String,
    val description: String? = null,
    val options: List<ChoiceOption>? = null
)
/**
 * Rating variant (opinionated, fixed scales).
 * Each variant has exactly one valid scale.
 */
@Serializable
enum class RatingVariant {
    @SerialName("emoji") EMOJI,      // Scale: 5 (fixed)
    @SerialName("thumbs") THUMBS,    // Scale: 2 (fixed)
    @SerialName("stars") STARS,      // Scale: 5 (fixed)
    @SerialName("nps") NPS;          // Scale: 11 (0-10)

    companion object {
        /** Fixed scale for each variant */
        val fixedScales: Map<RatingVariant, Int> = mapOf(
            EMOJI to 5,
            THUMBS to 2,
            STARS to 5,
            NPS to 11
        )

        /** Get the fixed scale for a variant */
        fun getScale(variant: RatingVariant): Int = fixedScales[variant] ?: 5
    }
}

@Serializable
sealed class AnswerValue {
    @Serializable
    @SerialName("rating")
    data class Rating(
        val rating: Int,
        /** Rating variant: emoji, thumbs, stars, nps */
        val ratingVariant: RatingVariant? = null,
        /** Scale (fixed per variant): emoji=5, thumbs=2, stars=5, nps=11 */
        val ratingScale: Int? = null
    ) : AnswerValue() {
        init {
            // Validate rating is within bounds when variant is present
            if (ratingVariant != null) {
                val scale = ratingScale ?: RatingVariant.getScale(ratingVariant)
                val maxRating = if (ratingVariant == RatingVariant.NPS) scale - 1 else scale
                val minRating = if (ratingVariant == RatingVariant.NPS) 0 else 1
                require(rating in minRating..maxRating) {
                    "Rating $rating is out of bounds for $ratingVariant (expected $minRating-$maxRating)"
                }
            }
        }
    }
    
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AnswerValue()
    
    @Serializable
    @SerialName("singleChoice")
    data class SingleChoice(val selectedOptionId: String) : AnswerValue()
    
    @Serializable
    @SerialName("multiChoice")
    data class MultiChoice(val selectedOptionIds: List<String>) : AnswerValue()
    
    @Serializable
    @SerialName("date")
    data class DateValue(val date: String) : AnswerValue()
}

@Serializable
data class Answer(
    val fieldId: String,
    val fieldType: FieldType,
    val question: Question,
    val value: AnswerValue
)

// ============================================
// Context Types (browser metadata)
// ============================================

@Serializable
enum class DeviceType {
    @SerialName("mobile") MOBILE,
    @SerialName("tablet") TABLET,
    @SerialName("desktop") DESKTOP
}

@Serializable
data class SubmissionContext(
    val url: String? = null,
    val pathname: String? = null,
    val deviceType: DeviceType? = null,
    val viewportWidth: Int? = null,
    val viewportHeight: Int? = null,
    /** Context tags from widget (low-cardinality segmentation) */
    val tags: Map<String, String>? = null
)

@Serializable
data class MetadataValueWithCount(
    val value: String,
    val count: Int
)

@Serializable
data class ContextTagsResponse(
    val surveyId: String,
    val contextTags: Map<String, List<MetadataValueWithCount>>,
    val maxCardinality: Int? = null
)

@Serializable
data class DeleteSurveyResult(
    val surveyId: String,
    val deletedCount: Int
)

// ============================================
// API Response Types
// ============================================

/**
 * API response for a single feedback item
 */
@Serializable
data class FeedbackDto(
    val id: String,
    val submittedAt: String,
    val app: String?,
    val surveyId: String,
    val surveyVersion: String? = null,
    val surveyType: SurveyType? = null,
    val context: SubmissionContext? = null,
    /** Custom metadata for segmentation/filtering in analytics */
    val metadata: Map<String, String>? = null,
    val answers: List<Answer>,
    /** User-defined tags set in analytics (stored separately from submission payload). */
    val tags: List<String> = emptyList(),
    val sensitiveDataRedacted: Boolean = false,
    /** Duration in milliseconds from visit start to submission (from widget submission). */
    val durationMs: Long? = null,
    /** ISO timestamp when the user started the session/task (from widget submission). */
    val visitStartedAt: String? = null
)

/**
 * Paginated response for feedback list
 */
@Serializable
data class FeedbackPage(
    val content: List<FeedbackDto>,
    val totalPages: Int,
    val totalElements: Int,
    val size: Int,
    val number: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

/**
 * Input for adding a tag
 */
@Serializable
data class TagInput(
    val tag: String
)

/**
 * Privacy information for aggregation threshold
 * Used to prevent identification of individuals in small datasets
 */
@Serializable
data class PrivacyInfo(
    val masked: Boolean = false,
    val reason: String? = null,
    val threshold: Int = 5
)

/**
 * Statistics response
 */
@Serializable
data class FeedbackStats(
    val totalCount: Int,
    val countWithText: Int,
    val countWithoutText: Int,
    val byRating: Map<String, Int>,
    val byApp: Map<String, Int>,
    val byDate: Map<String, Int>,
    val bySurveyId: Map<String, Int>,
    val averageRating: Double?,
    val period: StatsPeriod,
    val surveyType: SurveyType? = null,
    // New fields for analytics dashboard
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val ratingByDate: Map<String, RatingByDateEntry> = emptyMap(),
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val byDevice: Map<String, DeviceStats> = emptyMap(),
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val byPathname: Map<String, PathnameStats> = emptyMap(),
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val lowestRatingPaths: Map<String, PathnameStats> = emptyMap(),
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val fieldStats: List<FieldStat> = emptyList(),
    // Privacy threshold info
    val privacy: PrivacyInfo? = null
)

@Serializable
data class RatingByDateEntry(
    val average: Double,
    val count: Int
)

@Serializable
data class DeviceStats(
    val count: Int,
    val averageRating: Double
)

@Serializable
data class PathnameStats(
    val count: Int,
    val averageRating: Double
)

@Serializable
data class FieldStat(
    val fieldId: String,
    val fieldType: FieldType,
    val label: String,
    val stats: FieldStats
)

// Mirrors frontend discriminated union on `type`.
@Serializable
sealed class FieldStats {
    abstract val type: String

    @Serializable
    @SerialName("rating")
    data class Rating(
        override val type: String = "rating",
        val average: Double,
        /** Keys are serialized as strings in JSON ("1".."5"). */
        val distribution: Map<String, Int>
    ) : FieldStats()

    @Serializable
    @SerialName("text")
    data class Text(
        override val type: String = "text",
        val responseCount: Int,
        val responseRate: Double,
        val topKeywords: List<KeywordCount> = emptyList(),
        val recentResponses: List<RecentTextResponse> = emptyList()
    ) : FieldStats()

    @Serializable
    @SerialName("choice")
    data class Choice(
        override val type: String = "choice",
        val distribution: Map<String, ChoiceDistributionEntry>
    ) : FieldStats()
}

@Serializable
data class KeywordCount(
    val word: String,
    val count: Int
)

@Serializable
data class RecentTextResponse(
    val text: String,
    val submittedAt: String
)

@Serializable
data class ChoiceDistributionEntry(
    val label: String,
    val count: Int,
    val percentage: Int
)

@Serializable
@Deprecated("Replaced by FieldStats sealed class")
data class FieldStatDetails(
    val type: String,
    val average: Double? = null,
    val distribution: Map<Int, Int>? = null,
    val responseCount: Int? = null,
    val responseRate: Double? = null
)

@Serializable
data class StatsPeriod(
    val fromDate: String?,
    val toDate: String?,
    val days: Int
)

/**
 * Teams and apps response
 */
@Serializable
data class TeamsAndApps(
    val teams: Map<String, Set<String>>
)

/**
 * Query parameters for feedback list.
 * 
 * Note: fromDate/toDate are in YYYY-MM-DD format and interpreted as Europe/Oslo timezone.
 * Backend converts to UTC Instant for database queries.
 */
data class FeedbackQuery(
    val team: String,
    val app: String? = null,
    val page: Int? = null,
    val size: Int = 10,
    /** Filter for feedback with text responses */
    val hasText: Boolean = false,
    /** Filter for low ratings (1-2) */
    val lowRating: Boolean = false,
    /** Tags to filter by */
    val tags: List<String> = emptyList(),
    /** Full-text search query (searches in feedback text and tags) */
    val query: String? = null,
    /** Start date (YYYY-MM-DD, Europe/Oslo inclusive) */
    val fromDate: String? = null,
    /** End date (YYYY-MM-DD, Europe/Oslo inclusive) */
    val toDate: String? = null,
    /** Survey ID filter */
    val surveyId: String? = null,
    /** Device type filter */
    val deviceType: String? = null,
    /** Segment filters (context.tags) as key:value pairs */
    val segments: List<Pair<String, String>> = emptyList()
)

/**
 * Query parameters for stats.
 * 
 * Note: fromDate/toDate are in YYYY-MM-DD format and interpreted as Europe/Oslo timezone.
 */
data class StatsQuery(
    val team: String,
    val app: String? = null,
    /** Start date (YYYY-MM-DD, Europe/Oslo inclusive) */
    val fromDate: String? = null,
    /** End date (YYYY-MM-DD, Europe/Oslo inclusive) */
    val toDate: String? = null,
    /** Survey ID filter */
    val surveyId: String? = null,
    /** Device type filter */
    val deviceType: String? = null,
    /** Segment filters (context.tags) as key:value pairs */
    val segments: List<Pair<String, String>> = emptyList(),
    /** Task filter for Top Tasks drill-down */
    val task: String? = null
)

// ============================================
// Task Priority Survey Types
// ============================================

@Serializable
data class TaskVote(
    val task: String,
    val votes: Int,
    val percentage: Int
)

@Serializable
data class TaskPriorityResponse(
    val totalSubmissions: Int,
    val tasks: List<TaskVote>,
    /** Index in tasks array where cumulative percentage hits 80% (the "long neck") */
    val longNeckCutoff: Int,
    val cumulativePercentageAt5: Int,
)

/**
 * Response for GET /api/v1/intern/stats/overview
 */
@Serializable
data class StatsOverviewResponse(
    val generatedAt: String,
    val range: DateRange?,
    val totals: StatsTotals,
    val ratingDistribution: Map<String, Int>
)

@Serializable
data class DateRange(
    val fromDate: String?,
    val toDate: String?
)

@Serializable
data class StatsTotals(
    val feedbackCount: Int,
    val textCount: Int,
    val lowRatingCount: Int
)

/**
 * Internal result from FeedbackStatsRepository - typed for safety
 */
data class FeedbackStatsResult(
    val totalCount: Long,
    val countWithText: Long,
    val surveyType: String?,
    val byRating: Map<String, Int>,
    val byApp: Map<String, Int>,
    val byDate: Map<String, Int>,
    val bySurveyId: Map<String, Int>,
    // Privacy threshold
    val masked: Boolean = false,
    val threshold: Int = 5
)

/**
 * Export format options
 */
enum class ExportFormat {
    CSV,
    JSON,
    EXCEL
}

@Serializable
data class RatingDistribution(
    val distribution: Map<String, Int>,
    val average: Double?,
    val total: Int
)

@Serializable
data class TimelineEntry(
    val date: String,
    val count: Int
)

@Serializable
data class TimelineResponse(
    val data: List<TimelineEntry>
)

// ============================================
// Top Tasks Statistics
// ============================================

@Serializable
data class TopTaskStats(
    val task: String,
    val totalCount: Int,
    val successCount: Int,
    val partialCount: Int,
    val failureCount: Int,
    val successRate: Double,
    val formattedSuccessRate: String,
    val blockerCounts: Map<String, Int> = emptyMap()
)

@Serializable
data class DailyStat(
    val total: Int,
    val success: Int
)

@Serializable
data class TopTasksResponse(
    val totalSubmissions: Int,
    val tasks: List<TopTaskStats>,
    val dailyStats: Map<String, DailyStat> = emptyMap(),
    val questionText: String? = null
)

// ============================================
// Survey Type Distribution Statistics
// ============================================

@Serializable
data class SurveyTypeCount(
    val type: SurveyType,
    val count: Int,
    val percentage: Int
)

@Serializable
data class SurveyTypeDistribution(
    val totalSurveys: Int,
    val distribution: List<SurveyTypeCount>
)
