package no.nav.flexjar.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.resources.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.flexjar.config.auth.authorizedTeam
import no.nav.flexjar.config.auth.authorizedTeams
import no.nav.flexjar.config.auth.authorizedPrincipal
import no.nav.flexjar.integrations.valkey.StringCache
import no.nav.flexjar.integrations.valkey.ValkeyStringCache
import no.nav.flexjar.repository.FeedbackRepository
import java.time.Instant
import java.time.Duration

/**
 * Response for GET /api/v1/intern/filters/bootstrap
 * 
 * Provides all data needed for FilterBar dropdowns in a single request.
 * This endpoint is designed for long caching (5-10 minutes).
 */
@Serializable
data class FilterBootstrapResponse(
    val generatedAt: String,
    val selectedTeam: String,
    val availableTeams: List<String>,
    val deviceTypes: List<String> = listOf("mobile", "tablet", "desktop"),
    val apps: List<String>,
    val surveysByApp: Map<String, List<String>>,
    val tags: List<String>
)

private val defaultRepository = FeedbackRepository()
private val bootstrapCache: StringCache = ValkeyStringCache.fromEnvOrFallback(keyPrefix = "filters:bootstrap:")

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Routes for filter bootstrap and facets.
 * 
 * These endpoints provide metadata for FilterBar dropdowns,
 * enabling the frontend to render filter controls before fetching actual data.
 */
fun Route.filterRoutes(
    feedbackRepository: FeedbackRepository = defaultRepository
) {
    get<ApiV1Intern.Filters.Bootstrap> {
        val team = call.authorizedTeam
        val teams = call.authorizedTeams
        val principal = call.authorizedPrincipal

        // Cache is shared across users (Valkey). Include user identity to avoid leaking `availableTeams`.
        val cacheKey = "user=${principal.navIdent}&team=${team}".lowercase()

        bootstrapCache.get(cacheKey)?.let { cachedJson ->
            call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
            call.respondText(cachedJson, ContentType.Application.Json)
            return@get
        }

        // Each repository call manages its own transaction.
        // This endpoint is designed for long caching, so multiple DB round-trips are acceptable.
        val apps = feedbackRepository.findDistinctApps(team)
        val surveysByApp = feedbackRepository.findSurveysByApp(team)
        val tags = feedbackRepository.findAllTags(team)
        
        val response = FilterBootstrapResponse(
            generatedAt = Instant.now().toString(),
            selectedTeam = team,
            availableTeams = teams.sorted(),
            apps = apps.sorted(),
            surveysByApp = surveysByApp.mapValues { it.value.sorted() }.toSortedMap(),
            tags = tags.sorted()
        )

        bootstrapCache.set(cacheKey, json.encodeToString(response), ttl = Duration.ofMinutes(5))
        call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
        
        call.respond(response)
    }
}
