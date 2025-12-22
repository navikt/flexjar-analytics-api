package no.nav.flexjar.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.flexjar.config.auth.SubmissionAuthPlugin
import no.nav.flexjar.config.auth.getCallerIdentity
import no.nav.flexjar.config.exception.ApiErrorException
import no.nav.flexjar.repository.FeedbackRepository
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SubmissionRoutes")
private val feedbackRepository = FeedbackRepository()
private val json = Json { ignoreUnknownKeys = true }

/**
 * Submission routes for feedback collection.
 * 
 * All routes require authentication via Azure AD token.
 * The caller's identity (team/app) is extracted from the token's azp_name claim.
 * Any authenticated NAIS app can submit feedback - no whitelist required.
 */
fun Route.submissionRoutes() {
    route("/api") {
        // Install authentication plugin for all submission routes
        install(SubmissionAuthPlugin)
        
        // V1 legacy endpoint
        post("/v1/feedback") {
            handleSubmission(call, returnId = false)
        }
        
        // V2 endpoint - returns ID
        post("/v2/feedback") {
            handleSubmission(call, returnId = true)
        }
        
        // V2 update endpoint
        put("/v2/feedback/{id}") {
            handleUpdate(call)
        }
        
        // Azure variants (same behavior, different paths for backwards compatibility)
        post("/v1/feedback/azure") {
            handleSubmission(call, returnId = false)
        }
        
        post("/azure/v2/feedback") {
            handleSubmission(call, returnId = true)
        }
        
        put("/azure/v2/feedback/{id}") {
            handleUpdate(call)
        }
    }
}

private suspend fun handleSubmission(call: io.ktor.server.application.ApplicationCall, returnId: Boolean) {
    // Get authenticated caller identity (set by SubmissionAuthPlugin)
    val identity = call.getCallerIdentity()
    
    val body = call.receiveText()
    
    // Validate JSON structure
    val jsonElement = try {
        json.parseToJsonElement(body)
    } catch (e: Exception) {
        log.warn("Invalid JSON in feedback submission from team=${identity.team}", e)
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON"))
        return
    }
    
    // Validate that feedbackId exists
    val feedbackId = jsonElement.jsonObject["feedbackId"]?.jsonPrimitive?.content
    if (feedbackId.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "feedbackId is required"))
        return
    }
    
    val id = feedbackRepository.save(
        feedbackJson = body,
        team = identity.team,
        app = identity.app
    )
    
    log.info("Saved feedback id=$id team=${identity.team} app=${identity.app} feedbackId=$feedbackId")
    
    if (returnId) {
        call.respond(HttpStatusCode.Created, mapOf("id" to id))
    } else {
        call.respond(HttpStatusCode.Accepted)
    }
}

private suspend fun handleUpdate(call: io.ktor.server.application.ApplicationCall) {
    val id = call.parameters["id"] ?: run {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))
        return
    }
    
    // Get authenticated caller identity
    val identity = call.getCallerIdentity()
    
    // Verify the record exists and get ownership info
    val existing = feedbackRepository.findRawById(id)
    if (existing == null) {
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Feedback not found"))
        return
    }
    
    // Verify ownership - only the original team/app can update
    val existingTeam = existing.team
    val existingApp = existing.app
    
    if (existingTeam != identity.team || existingApp != identity.app) {
        log.warn("Update rejected: team=${identity.team} app=${identity.app} tried to update record owned by team=$existingTeam app=$existingApp")
        throw ApiErrorException.ForbiddenException("Cannot update feedback that belongs to another app")
    }
    
    val body = call.receiveText()
    feedbackRepository.update(id, body)
    
    log.info("Updated feedback id=$id team=${identity.team} app=${identity.app}")
    call.respond(HttpStatusCode.OK)
}
