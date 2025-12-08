package no.nav.flexjar.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.flexjar.config.extractCallerIdentity
import no.nav.flexjar.repository.FeedbackRepository
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SubmissionRoutes")
private val feedbackRepository = FeedbackRepository()
private val json = Json { ignoreUnknownKeys = true }

fun Route.submissionRoutes() {
    route("/api") {
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
        
        // Azure variants
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
    val token = call.request.header("Authorization")?.removePrefix("Bearer ")
    val identity = token?.let { extractCallerIdentity(it) }
    
    val body = call.receiveText()
    
    // Validate that feedbackId exists
    val jsonElement = try {
        json.parseToJsonElement(body)
    } catch (e: Exception) {
        log.warn("Invalid JSON in feedback submission", e)
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON"))
        return
    }
    
    val feedbackId = jsonElement.jsonObject["feedbackId"]?.jsonPrimitive?.content
    if (feedbackId.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "feedbackId is required"))
        return
    }
    
    val team = identity?.team ?: "unknown"
    val app = identity?.app
    
    val id = feedbackRepository.save(
        feedbackJson = body,
        team = team,
        app = app
    )
    
    log.info("Saved feedback id=$id team=$team app=$app feedbackId=$feedbackId")
    
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
    
    val token = call.request.header("Authorization")?.removePrefix("Bearer ")
    val identity = token?.let { extractCallerIdentity(it) }
    
    // Verify the record exists
    val existing = feedbackRepository.findById(id)
    if (existing == null) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    
    // Note: ownership verification would require storing team/app in DB or JWT claims
    // For now, allow updates if the record exists
    
    val body = call.receiveText()
    feedbackRepository.update(id, body)
    
    call.respond(HttpStatusCode.OK)
}
