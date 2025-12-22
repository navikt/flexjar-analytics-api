package no.nav.flexjar.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.resources.delete
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.flexjar.domain.FeedbackPage
import no.nav.flexjar.domain.FeedbackQuery
import no.nav.flexjar.domain.FILTER_ALL
import no.nav.flexjar.domain.TagInput
import no.nav.flexjar.domain.TeamsAndApps
import no.nav.flexjar.repository.FeedbackRepository
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("FeedbackRoutes")
private val defaultFeedbackRepository = FeedbackRepository()

fun Route.feedbackRoutes(repository: FeedbackRepository = defaultFeedbackRepository) {
    // List feedback with pagination and filters
    get<ApiV1Intern.Feedback> { params ->
        val team = params.team ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing team parameter")

        val query = FeedbackQuery(
            team = team,
            app = params.app?.takeIf { it != FILTER_ALL },
            page = params.page,
            size = params.size ?: 10,
            medTekst = params.medTekst ?: false,
            stjerne = params.stjerne ?: false,
            tags = params.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            fritekst = params.fritekst?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
            from = params.from,
            to = params.to,
            feedbackId = params.feedbackId,
            deviceType = params.deviceType?.takeIf { it.isNotBlank() }
        )
        
        val (content, total, page) = repository.findPaginated(query)
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
        val feedback = repository.findById(params.id)
        if (feedback == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        
        call.respond(feedback)
    }
    
    // Soft delete (clears feedback text)
    delete<ApiV1Intern.Feedback.Id> { params ->
        val deleted = repository.softDelete(params.id)
        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
    
    // Add tag
    post<ApiV1Intern.Feedback.Id.Tags> { params ->
        val input = call.receive<TagInput>()
        val added = repository.addTag(params.parent.id, input.tag)
        
        if (added) {
            call.respond(HttpStatusCode.Created)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
    
    // Remove tag
    delete<ApiV1Intern.Feedback.Id.Tags> { params ->
        val tag = params.tag
        if (tag.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing tag parameter"))
            return@delete
        }
        
        val removed = repository.removeTag(params.parent.id, tag)
        if (removed) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
    
    // Get all tags
    get<ApiV1Intern.Feedback.AllTags> {
        val tags = repository.findAllTags()
        call.respond(tags)
    }
    
    // Get all teams and their apps
    get<ApiV1Intern.Feedback.Teams> {
        val teamsAndApps = repository.findAllTeamsAndApps()
        call.respond(TeamsAndApps(teams = teamsAndApps))
    }
    
    // Get all metadata keys and values
    get<ApiV1Intern.Feedback.MetadataKeys> { params ->
        val metadataKeys = repository.findMetadataKeysForSurvey(params.feedbackId, params.team)
        call.respond(mapOf(
            "feedbackId" to params.feedbackId,
            "metadataKeys" to metadataKeys
        ))
    }

    // Get all surveys (grouped by app)
    get<ApiV1Intern.Surveys> {
        val teamsAndApps = repository.findAllTeamsAndApps()
        call.respond(teamsAndApps)
    }
}
