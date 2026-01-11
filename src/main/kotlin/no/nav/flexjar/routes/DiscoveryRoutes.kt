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
import no.nav.flexjar.domain.*
import no.nav.flexjar.repository.TextThemeRepository
import no.nav.flexjar.service.DiscoveryService
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger("DiscoveryRoutes")
private val defaultThemeRepository = TextThemeRepository()
private val defaultDiscoveryService = DiscoveryService()

fun Route.discoveryRoutes(
    themeRepository: TextThemeRepository = defaultThemeRepository,
    discoveryService: DiscoveryService = defaultDiscoveryService
) {
    // ============================================
    // Theme CRUD endpoints (generic, usable for all surveys)
    // ============================================

    // List all themes for team
    get<ApiV1Intern.Themes> { params ->
        val team = call.authorizedTeam

        val themes = when (val ctx = params.context?.trim()?.takeIf { it.isNotEmpty() }) {
            null -> themeRepository.findByTeam(team)
            else -> {
                val parsed = try {
                    AnalysisContext.valueOf(ctx)
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid context '$ctx'. Expected GENERAL_FEEDBACK or BLOCKER"))
                    return@get
                }
                themeRepository.findByTeam(team, parsed)
            }
        }

        call.respond(themes)
    }

    // Create new theme
    post<ApiV1Intern.Themes> {
        val team = call.authorizedTeam
        val request = call.receive<CreateThemeRequest>()

        if (request.name.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Theme name is required"))
            return@post
        }
        if (request.keywords.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "At least one keyword is required"))
            return@post
        }

        try {
            val theme = themeRepository.create(team, request)
            call.respond(HttpStatusCode.Created, theme)
        } catch (e: Exception) {
            log.warn("Failed to create theme", e)
            if (e.message?.contains("unique") == true || e.message?.contains("duplicate") == true) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Theme with this name already exists"))
            } else {
                throw e
            }
        }
    }

    // Update existing theme
    put<ApiV1Intern.Themes.Id> { params ->
        val team = call.authorizedTeam
        val id = try {
            UUID.fromString(params.id)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid theme ID"))
            return@put
        }

        // Verify ownership
        if (!themeRepository.belongsToTeam(id, team)) {
            call.respond(HttpStatusCode.NotFound)
            return@put
        }

        val request = call.receive<UpdateThemeRequest>()

        val updated = themeRepository.update(id, request)
        if (updated) {
            val theme = themeRepository.findById(id)
            call.respond(theme ?: HttpStatusCode.NotFound)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    // Delete theme
    delete<ApiV1Intern.Themes.Id> { params ->
        val team = call.authorizedTeam
        val id = try {
            UUID.fromString(params.id)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid theme ID"))
            return@delete
        }

        // Verify ownership
        if (!themeRepository.belongsToTeam(id, team)) {
            call.respond(HttpStatusCode.NotFound)
            return@delete
        }

        val deleted = themeRepository.delete(id)
        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    // ============================================
    // Discovery stats endpoint
    // ============================================

    get<ApiV1Intern.Stats.Discovery> { params ->
        val team = call.authorizedTeam

        val query = StatsQuery(
            team = team,
            app = params.parent.app?.takeIf { it != FILTER_ALL },
            fromDate = params.parent.fromDate,
            toDate = params.parent.toDate,
            surveyId = params.parent.surveyId,
            deviceType = params.parent.deviceType?.takeIf { it != FILTER_ALL }
        )

        val stats = discoveryService.getStats(query)
        call.respond(stats)
    }
}
