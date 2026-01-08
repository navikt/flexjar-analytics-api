package no.nav.flexjar.domain

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
    val feedbackId: String,
    val contextTags: Map<String, List<MetadataValueWithCount>>,
    val maxCardinality: Int? = null
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
    val sensitiveDataRedacted: Boolean = false
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
 * Input for submitting new feedback (from widget)
 */
@Serializable
data class FeedbackInput(
    val feedbackId: String,
    val feedback: String? = null,
    val svar: String? = null,
    // Allow any additional fields
    val extra: Map<String, String> = emptyMap()
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
    val byFeedbackId: Map<String, Int>,
    val averageRating: Double?,
    val period: StatsPeriod,
    val surveyType: SurveyType? = null,
    // New fields for analytics dashboard
    val ratingByDate: Map<String, RatingByDateEntry> = emptyMap(),
    val byDevice: Map<String, DeviceStats> = emptyMap(),
    val byPathname: Map<String, PathnameStats> = emptyMap(),
    val lowestRatingPaths: Map<String, PathnameStats> = emptyMap(),
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
    val fieldType: String,
    val label: String,
    val stats: FieldStatDetails
)

@Serializable
data class FieldStatDetails(
    val type: String,
    val average: Double? = null,
    val distribution: Map<Int, Int>? = null,
    val responseCount: Int? = null,
    val responseRate: Double? = null
)

@Serializable
data class StatsPeriod(
    val from: String?,
    val to: String?,
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
 * Query parameters for feedback list
 */
data class FeedbackQuery(
    val team: String = "flex",
    val app: String? = null,
    val page: Int? = null,
    val size: Int = 10,
    val medTekst: Boolean = false,
    val stjerne: Boolean = false,
    val tags: List<String> = emptyList(),
    val fritekst: List<String> = emptyList(),
    val from: String? = null,
    val to: String? = null,
    val feedbackId: String? = null,
    val lavRating: Boolean = false,
    val deviceType: String? = null
)

/**
 * Query parameters for stats
 */
data class StatsQuery(
    val team: String = "flex",
    val app: String? = null,
    val from: String? = null,
    val to: String? = null,
    val feedbackId: String? = null,
    val deviceType: String? = null
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
    val byFeedbackId: Map<String, Int>,
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
