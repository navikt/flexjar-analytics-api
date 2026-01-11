package no.nav.flexjar.repository

import no.nav.flexjar.domain.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.*

/**
 * Repository for Text Theme CRUD operations.
 * Themes can be used across all survey types for text analysis.
 */
class TextThemeRepository {

    /**
     * Find all themes for a team, ordered by priority (highest first)
     */
    fun findByTeam(team: String): List<TextThemeDto> {
        return transaction {
            TextThemeTable.selectAll()
                .where { TextThemeTable.team eq team }
                .orderBy(TextThemeTable.priority to SortOrder.DESC)
                .map { it.toDto() }
        }
    }

    fun findByTeam(team: String, context: AnalysisContext): List<TextThemeDto> {
        return transaction {
            TextThemeTable.selectAll()
                .where {
                    (TextThemeTable.team eq team) and
                        (TextThemeTable.analysisContext eq context.name)
                }
                .orderBy(TextThemeTable.priority to SortOrder.DESC)
                .map { it.toDto() }
        }
    }

    /**
     * Find a single theme by ID
     */
    fun findById(id: UUID): TextThemeDto? {
        return transaction {
            TextThemeTable.selectAll()
                .where { TextThemeTable.id eq id }
                .singleOrNull()
                ?.toDto()
        }
    }

    /**
     * Create a new theme
     */
    fun create(team: String, request: CreateThemeRequest): TextThemeDto {
        return transaction {
            val id = UUID.randomUUID()
            val now = OffsetDateTime.now()
            val context = request.analysisContext
            
            TextThemeTable.insert {
                it[TextThemeTable.id] = id
                it[TextThemeTable.team] = team
                it[TextThemeTable.name] = request.name
                it[TextThemeTable.keywords] = request.keywords
                it[TextThemeTable.color] = request.color
                it[TextThemeTable.priority] = request.priority ?: 0
                it[TextThemeTable.analysisContext] = context.name
                it[TextThemeTable.createdAt] = now
            }
            
            TextThemeDto(
                id = id.toString(),
                team = team,
                name = request.name,
                keywords = request.keywords,
                color = request.color,
                priority = request.priority ?: 0,
                analysisContext = context
            )
        }
    }

    /**
     * Update an existing theme. Only updates non-null fields.
     */
    fun update(id: UUID, request: UpdateThemeRequest): Boolean {
        return transaction {
            TextThemeTable.update({ TextThemeTable.id eq id }) { stmt ->
                request.name?.let { stmt[name] = it }
                request.keywords?.let { stmt[keywords] = it }
                request.color?.let { stmt[color] = it }
                request.priority?.let { stmt[priority] = it }
                stmt[analysisContext] = request.analysisContext.name
            } > 0
        }
    }

    /**
     * Delete a theme by ID
     */
    fun delete(id: UUID): Boolean {
        return transaction {
            TextThemeTable.deleteWhere { TextThemeTable.id eq id } > 0
        }
    }

    /**
     * Check if theme belongs to a specific team (for authorization)
     */
    fun belongsToTeam(id: UUID, team: String): Boolean {
        return transaction {
            TextThemeTable.selectAll()
                .where { (TextThemeTable.id eq id) and (TextThemeTable.team eq team) }
                .count() > 0
        }
    }

    private fun ResultRow.toDto(): TextThemeDto {
        return TextThemeDto(
            id = this[TextThemeTable.id].toString(),
            team = this[TextThemeTable.team],
            name = this[TextThemeTable.name],
            keywords = this[TextThemeTable.keywords].toList(),
            color = this[TextThemeTable.color],
            priority = this[TextThemeTable.priority],
            analysisContext = try {
                AnalysisContext.valueOf(this[TextThemeTable.analysisContext])
            } catch (_: Exception) {
                AnalysisContext.GENERAL_FEEDBACK
            }
        )
    }
}
