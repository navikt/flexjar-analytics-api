package no.nav.flexjar.routes

import io.ktor.server.resources.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.flexjar.config.auth.authorizedTeam
import no.nav.flexjar.repository.FeedbackRepository
import java.time.Instant

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

        // Each repository call manages its own transaction.
        // This endpoint is designed for long caching, so multiple DB round-trips are acceptable.
        val apps = feedbackRepository.findDistinctApps(team)
        val surveysByApp = feedbackRepository.findSurveysByApp(team)
        val tags = feedbackRepository.findAllTags(team)
        
        val response = FilterBootstrapResponse(
            generatedAt = Instant.now().toString(),
            selectedTeam = team,
            availableTeams = listOf(team), // For now, user has access to one team
            apps = apps.sorted(),
            surveysByApp = surveysByApp.mapValues { it.value.sorted() }.toSortedMap(),
            tags = tags.sorted()
        )
        
        call.respond(response)
    }
}
