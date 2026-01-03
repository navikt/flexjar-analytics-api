package no.nav.flexjar.domain

import kotlinx.serialization.Serializable

/**
 * Text theme definition - used to group free-text responses by keyword matching.
 * Themes are team-scoped and can be reused across ALL survey types.
 */
@Serializable
data class TextThemeDto(
    val id: String,
    val team: String,
    val name: String,
    val keywords: List<String>,
    val color: String? = null,
    val priority: Int = 0
)

/**
 * Request body for creating a new theme
 */
@Serializable
data class CreateThemeRequest(
    val name: String,
    val keywords: List<String>,
    val color: String? = null,
    val priority: Int? = null
)

/**
 * Request body for updating an existing theme
 */
@Serializable
data class UpdateThemeRequest(
    val name: String? = null,
    val keywords: List<String>? = null,
    val color: String? = null,
    val priority: Int? = null
)

// ============================================
// Discovery Statistics Response Types
// (Still named Discovery since the endpoint is for Discovery dashboard)
// ============================================

/**
 * A matched theme with aggregated statistics
 */
@Serializable
data class ThemeResult(
    val theme: String,
    val count: Int,
    val successRate: Double,
    val examples: List<String>
)

/**
 * Word frequency entry for word cloud
 */
@Serializable
data class WordFrequencyEntry(
    val word: String,
    val count: Int
)

/**
 * Recent discovery response
 */
@Serializable
data class DiscoveryRecentResponse(
    val task: String,
    val success: String,  // "yes" | "partial" | "no"
    val blocker: String? = null,
    val submittedAt: String
)

/**
 * Full discovery statistics response
 */
@Serializable
data class DiscoveryStatsResponse(
    val totalSubmissions: Int,
    val wordFrequency: List<WordFrequencyEntry>,
    val themes: List<ThemeResult>,
    val recentResponses: List<DiscoveryRecentResponse>
)
