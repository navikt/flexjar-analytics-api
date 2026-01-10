package no.nav.flexjar.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class Viewport(
    val width: Int,
    val height: Int
)

/**
 * Canonical submission context from the widget (schemaVersion=1).
 *
 * Note: `tags` accepts primitives and will be stringified for analytics segmentation.
 */
@Serializable
data class SubmissionContextV1(
    val url: String? = null,
    val pathname: String? = null,
    val deviceType: DeviceType? = null,
    val viewport: Viewport? = null,
    val userAgent: String? = null,
    val tags: Map<String, JsonPrimitive>? = null,
    val debug: JsonObject? = null
)

/**
 * Canonical widget submission payload (schemaVersion=1).
 */
@Serializable
data class FeedbackSubmissionV1(
    val schemaVersion: Int,
    val surveyId: String,
    val surveyType: SurveyType,
    val submittedAt: String,
    val startedAt: String? = null,
    val timeToCompleteMs: Long? = null,
    val context: SubmissionContextV1? = null,
    val answers: List<Answer>
)
