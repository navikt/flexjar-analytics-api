package no.nav.flexjar.repository

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Exposed table definition for text_theme table.
 * Used for grouping free-text responses by keyword matching.
 * Can be reused across all survey types (Rating, Discovery, TopTasks, etc.)
 */
object TextThemeTable : Table("text_theme") {
    val id = uuid("id").autoGenerate()
    val team = text("team")
    val name = text("name")
    val keywords = array<String>("keywords")
    val color = text("color").nullable()
    val priority = integer("priority").default(0)
    val analysisContext = text("analysis_context")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
