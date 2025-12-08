package no.nav.flexjar.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.flexjar.domain.FeedbackPage
import no.nav.flexjar.domain.FeedbackQuery
import no.nav.flexjar.domain.TagInput
import no.nav.flexjar.domain.TeamsAndApps
import no.nav.flexjar.repository.FeedbackRepository
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("FeedbackRoutes")
private val feedbackRepository = FeedbackRepository()

fun Route.feedbackRoutes() {
    route("/api/v1/intern") {
        // List feedback with pagination and filters
        get("/feedback") {
            val query = FeedbackQuery(
                team = call.request.queryParameters["team"] ?: "flex",
                app = call.request.queryParameters["app"]?.takeIf { it != "alle" },
                page = call.request.queryParameters["page"]?.toIntOrNull(),
                size = call.request.queryParameters["size"]?.toIntOrNull() ?: 10,
                medTekst = call.request.queryParameters["medTekst"]?.toBoolean() ?: false,
                stjerne = call.request.queryParameters["stjerne"]?.toBoolean() ?: false,
                tags = call.request.queryParameters["tags"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                fritekst = call.request.queryParameters["fritekst"]?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
                from = call.request.queryParameters["from"],
                to = call.request.queryParameters["to"],
                feedbackId = call.request.queryParameters["feedbackId"],
                deviceType = call.request.queryParameters["deviceType"]?.takeIf { it.isNotBlank() }
            )
            
            val (content, total, page) = feedbackRepository.findPaginated(query)
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
        get("/feedback/{id}") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))
                return@get
            }
            
            val feedback = feedbackRepository.findById(id)
            if (feedback == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            
            call.respond(feedback)
        }
        
        // Soft delete (clears feedback text)
        delete("/feedback/{id}") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))
                return@delete
            }
            
            val deleted = feedbackRepository.softDelete(id)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        
        // Add tag
        post("/feedback/{id}/tags") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))
                return@post
            }
            
            val input = call.receive<TagInput>()
            val added = feedbackRepository.addTag(id, input.tag)
            
            if (added) {
                call.respond(HttpStatusCode.Created)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        
        // Remove tag
        delete("/feedback/{id}/tags") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))
                return@delete
            }
            
            val tag = call.request.queryParameters["tag"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing tag parameter"))
                return@delete
            }
            
            val removed = feedbackRepository.removeTag(id, tag)
            if (removed) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        
        // Get all tags
        get("/feedback/tags") {
            val tags = feedbackRepository.findAllTags()
            call.respond(tags)
        }
        
        // Get all teams and their apps
        get("/feedback/teams") {
            val teamsAndApps = feedbackRepository.findAllTeamsAndApps()
            call.respond(TeamsAndApps(teams = teamsAndApps))
        }
    }
}
