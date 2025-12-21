package no.nav.flexjar.repository

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.javatime.JavaLocalDateColumnType
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.time.LocalDate

object FeedbackTable : Table("feedback") {
    val id = varchar("id", 255)
    val opprettet = timestamp("opprettet")
    val feedbackJson = text("feedback_json") // Storing as text for simplicity, could be jsonb
    val team = varchar("team", 255)
    val app = varchar("app", 255).nullable()
    val tags = varchar("tags", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

class JsonExtract(val col: Column<*>, val path: List<String>) : Function<String>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(col)
        queryBuilder.append("::json")
        path.forEachIndexed { index, p ->
            if (index == path.size - 1) {
                queryBuilder.append("->>'\$p'")
            } else {
                queryBuilder.append("->'\$p'")
            }
        }
    }
}

class DateDate(val expr: Expression<Instant>) : Function<LocalDate>(JavaLocalDateColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("DATE(")
        expr.toQueryBuilder(queryBuilder)
        queryBuilder.append(")")
    }
}
