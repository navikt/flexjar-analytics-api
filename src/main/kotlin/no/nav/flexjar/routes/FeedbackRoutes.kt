package no.nav.flexjar.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.resources.delete
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.flexjar.config.auth.authorizedTeam
import no.nav.flexjar.domain.FeedbackPage
import no.nav.flexjar.domain.FeedbackQuery
import no.nav.flexjar.domain.ContextTagsResponse
import no.nav.flexjar.domain.FILTER_ALL
import no.nav.flexjar.domain.TagInput
import no.nav.flexjar.domain.TeamsAndApps
import no.nav.flexjar.repository.FeedbackRepository
import no.nav.flexjar.service.FeedbackService
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("FeedbackRoutes")
private val defaultFeedbackService = FeedbackService()

fun Route.feedbackRoutes(service: FeedbackService = defaultFeedbackService) {
    // List feedback with pagination and filters
    get<ApiV1Intern.Feedback> { params ->
        // Team is already validated by TeamAuthorizationPlugin
        val team = call.authorizedTeam

        // Parse segment params (format: "key:value")
        val segments = params.segment?.mapNotNull { segmentStr ->
            val parts = segmentStr.split(":", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        } ?: emptyList()

        val tags = params.tag
            ?.flatMap { it.split(",") }
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val query = FeedbackQuery(
            team = team,
            app = params.app?.takeIf { it != FILTER_ALL },
            page = params.page,
            size = params.size ?: 10,
            hasText = params.hasText ?: false,
            lowRating = params.lowRating ?: false,
            tags = tags,
            query = params.query?.takeIf { it.isNotBlank() },
            fromDate = params.fromDate,
            toDate = params.toDate,
            surveyId = params.surveyId,
            deviceType = params.deviceType?.takeIf { it.isNotBlank() },
            segments = segments
        )
        
        val (content, total, page) = service.findPaginated(query)
        val totalPages = if (query.size > 0) ((total + query.size - 1) / query.size).toInt() else 0
        
        call.respond(FeedbackPage(
            content = content,
            totalPages = totalPages,
            totalElements = total.toInt(),
            size = query.size,
            number = page,
            hasNext = page < totalPages - 1,
            hasPrevious = page > 0
        ))
    }
    
    // Get single feedback
    get<ApiV1Intern.Feedback.Id> { params ->
        val team = call.authorizedTeam
        val feedback = service.findById(params.id, team)
        if (feedback == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        
        call.respond(feedback)
    }
    
    // Soft delete (clears feedback text)
    delete<ApiV1Intern.Feedback.Id> { params ->
        val team = call.authorizedTeam
        val deleted = service.softDelete(params.id, team)
        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
    
    // Add tag
    post<ApiV1Intern.Feedback.Id.Tags> { params ->
        val team = call.authorizedTeam
        val input = call.receive<TagInput>()
        val added = service.addTag(params.parent.id, team, input.tag)
        
        if (added) {
            call.respond(HttpStatusCode.Created)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
    
    // Remove tag
    delete<ApiV1Intern.Feedback.Id.Tags> { params ->
        val team = call.authorizedTeam
        val tag = params.tag
        if (tag.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing tag parameter"))
            return@delete
        }
        
        val removed = service.removeTag(params.parent.id, team, tag)
        if (removed) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
    
    // Get all tags for the current team
    get<ApiV1Intern.Feedback.AllTags> {
        val team = call.authorizedTeam
        val tags = service.findAllTags(team)
        call.respond(tags)
    }
    
    // Get all apps for the current team
    get<ApiV1Intern.Feedback.Teams> {
        val team = call.authorizedTeam
        val apps = service.findDistinctApps(team)
        call.respond(TeamsAndApps(mapOf(team to apps.toSet())))
    }
    
    // Get all metadata keys and values (filtered by cardinality for graph-friendly data)
    get<ApiV1Intern.Feedback.MetadataKeys> { params ->
        val team = call.authorizedTeam
        val metadataKeys = FeedbackRepository().findMetadataKeysForSurvey(params.surveyId, team)
        
        // Filter by cardinality if specified (default 10)
        val maxCard = params.maxCardinality
        val filteredKeys = if (maxCard != null) {
            metadataKeys.filter { entry -> entry.value.size <= maxCard }
        } else {
            metadataKeys
        }
        
        call.respond(mapOf(
            "surveyId" to params.surveyId,
            "metadataKeys" to filteredKeys,
            "maxCardinality" to maxCard
        ))
    }

    // Get all surveys (grouped by app) for the current team
    get<ApiV1Intern.Surveys> {
        val team = call.authorizedTeam
        val surveysByApp = FeedbackRepository().findSurveysByApp(team)
        call.respond(surveysByApp)
    }
}

fun Route.surveyFacetRoutes(service: FeedbackService = defaultFeedbackService) {
    // Get all context tags and values with counts for a survey (filtered by cardinality)
    get<ApiV1Intern.Surveys.Id.ContextTags> { params ->
        val team = call.authorizedTeam

        // Parse segment params (format: "key:value")
        val segments = params.segment?.mapNotNull { segmentStr ->
            val parts = segmentStr.split(":", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        } ?: emptyList()

        // repository lookup for advanced facet query
        val contextTags = FeedbackRepository().findContextTagsForSurvey(
            surveyId = params.parent.surveyId,
            team = team,
            task = params.task,
            segments = segments,
        )

        val maxCard = params.maxCardinality
        val filteredTags = if (maxCard != null) {
            contextTags.filter { entry -> entry.value.size <= maxCard }
        } else {
            contextTags
        }

        call.respond(
            ContextTagsResponse(
                surveyId = params.parent.surveyId,
                contextTags = filteredTags,
                maxCardinality = maxCard,
            )
        )
    }
}
