package no.nav.flexjar.routes

import io.ktor.http.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import no.nav.flexjar.config.SubmissionRateLimit
import no.nav.flexjar.config.auth.SubmissionAuthPlugin
import no.nav.flexjar.config.auth.getCallerIdentity
import no.nav.flexjar.config.exception.ApiErrorException
import no.nav.flexjar.domain.AnswerValue
import no.nav.flexjar.domain.FeedbackSubmissionV1
import no.nav.flexjar.domain.RatingVariant
import no.nav.flexjar.service.FeedbackService
import org.slf4j.LoggerFactory
import java.time.Instant

private val log = LoggerFactory.getLogger("SubmissionRoutes")
private val defaultFeedbackService = FeedbackService()

private val strictJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
    encodeDefaults = true
}

/**
 * Submission routes for feedback collection.
 * 
 * All routes require authentication via Azure AD token.
 * The caller's identity (team/app) is extracted from the token's azp_name claim.
 * Any authenticated NAIS app can submit feedback - no whitelist required.
 * 
 * Rate limited to 100 requests per minute per calling application.
 */
fun Route.submissionRoutes(feedbackService: FeedbackService = defaultFeedbackService) {
    rateLimit(SubmissionRateLimit) {
        route("/api") {
            // Install authentication plugin for all submission routes
            install(SubmissionAuthPlugin)

            // Canonical submission endpoint (schemaVersion=1)
            post("/v1/feedback") {
                handleSubmissionV1(call, feedbackService)
            }
        }
    }
}

private fun validateSubmissionV1(submission: FeedbackSubmissionV1) {
    if (submission.schemaVersion != 1) {
        throw ApiErrorException.BadRequestException(
            "UNSUPPORTED_SCHEMA: schemaVersion=${submission.schemaVersion} is not supported"
        )
    }

    if (submission.surveyId.isBlank()) {
        throw ApiErrorException.BadRequestException("Invalid payload: surveyId must be non-blank")
    }

    runCatching { Instant.parse(submission.submittedAt) }
        .getOrElse { throw ApiErrorException.BadRequestException("Invalid payload: submittedAt must be an ISO instant") }

    if (submission.startedAt != null) {
        runCatching { Instant.parse(submission.startedAt) }
            .getOrElse { throw ApiErrorException.BadRequestException("Invalid payload: startedAt must be an ISO instant") }
    }

    if (submission.answers.isEmpty()) {
        throw ApiErrorException.BadRequestException("Invalid payload: answers must be non-empty")
    }

    val duplicateFieldIds = submission.answers
        .groupBy { it.fieldId }
        .filterValues { it.size > 1 }
        .keys
        .toList()

    if (duplicateFieldIds.isNotEmpty()) {
        throw ApiErrorException.BadRequestException(
            "Invalid payload: answers.fieldId must be unique (duplicates: ${duplicateFieldIds.joinToString(",")})"
        )
    }

    submission.answers.forEach { answer ->
        when (val value = answer.value) {
            is AnswerValue.Rating -> {
                val variant = value.ratingVariant
                    ?: throw ApiErrorException.BadRequestException("Invalid payload: ratingVariant is required for rating answers")
                val scale = value.ratingScale
                    ?: throw ApiErrorException.BadRequestException("Invalid payload: ratingScale is required for rating answers")

                val expectedScale = RatingVariant.getScale(variant)
                if (scale != expectedScale) {
                    throw ApiErrorException.BadRequestException(
                        "Invalid payload: ratingScale=$scale does not match ratingVariant=$variant (expected $expectedScale)"
                    )
                }

                val (minRating, maxRating) = if (variant == RatingVariant.NPS) {
                    0 to 10
                } else {
                    1 to scale
                }

                if (value.rating !in minRating..maxRating) {
                    throw ApiErrorException.BadRequestException(
                        "Invalid payload: rating=${value.rating} out of range for ratingVariant=$variant ($minRating-$maxRating)"
                    )
                }
            }

            is AnswerValue.Text -> {
                // No extra validation (PII redaction happens before storage)
            }

            is AnswerValue.SingleChoice -> {
                if (value.selectedOptionId.isBlank()) {
                    throw ApiErrorException.BadRequestException("Invalid payload: selectedOptionId must be non-blank")
                }
            }

            is AnswerValue.MultiChoice -> {
                if (value.selectedOptionIds.isEmpty()) {
                    throw ApiErrorException.BadRequestException("Invalid payload: selectedOptionIds must be non-empty")
                }
            }

            is AnswerValue.DateValue -> {
                if (value.date.isBlank()) {
                    throw ApiErrorException.BadRequestException("Invalid payload: date must be non-blank")
                }
            }
        }
    }
}

private suspend fun handleSubmissionV1(
    call: io.ktor.server.application.ApplicationCall,
    feedbackService: FeedbackService
) {
    val identity = call.getCallerIdentity()
    val body = call.receiveText()

    val jsonElement = try {
        strictJson.parseToJsonElement(body)
    } catch (e: Exception) {
        log.warn("Invalid JSON in feedback submission from team=${identity.team} app=${identity.app}", e)
        throw ApiErrorException.BadRequestException("Invalid JSON")
    }

    val submission = try {
        strictJson.decodeFromJsonElement(FeedbackSubmissionV1.serializer(), jsonElement)
    } catch (e: SerializationException) {
        throw ApiErrorException.BadRequestException("Invalid payload")
    }

    validateSubmissionV1(submission)

    val id = feedbackService.save(
        feedbackJson = body,
        team = identity.team,
        app = identity.app
    )

    log.info("Saved feedback id=$id team=${identity.team} app=${identity.app} surveyId=${submission.surveyId}")
    call.respond(HttpStatusCode.Created, mapOf("id" to id))
}
