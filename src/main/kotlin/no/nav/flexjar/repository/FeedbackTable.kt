package no.nav.flexjar.repository

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.BooleanColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.javatime.JavaLocalDateColumnType
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.time.LocalDate

object FeedbackTable : Table("feedback") {
    val id = varchar("id", 255)
    val opprettet = timestamp("opprettet")
    val feedbackJson = text("feedback_json")
    val team = varchar("team", 255)
    val app = varchar("app", 255)
    val tags = text("tags").nullable()

    override val primaryKey = PrimaryKey(id)
}

class JsonExtract(val col: Column<*>, val path: List<String>) : Function<String>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(col)
        queryBuilder.append("::json")
        path.forEachIndexed { index, p ->
            val escaped = p.replace("'", "''")
            if (index == path.size - 1) {
                queryBuilder.append("->>'")
                queryBuilder.append(escaped)
                queryBuilder.append("'")
            } else {
                queryBuilder.append("->'")
                queryBuilder.append(escaped)
                queryBuilder.append("'")
            }
        }
    }
}

/**
 * PostgreSQL COALESCE(expr1, expr2, ...) helper for String expressions.
 */
class CoalesceString(vararg val expr: Expression<String>) : Function<String>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("COALESCE(")
        expr.forEachIndexed { index, e ->
            if (index > 0) queryBuilder.append(", ")
            e.toQueryBuilder(queryBuilder)
        }
        queryBuilder.append(")")
    }
}

class DateDate(val expr: Expression<Instant>) : Function<LocalDate>(JavaLocalDateColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("DATE(")
        expr.toQueryBuilder(queryBuilder)
        queryBuilder.append(")")
    }
}

/**
 * PostgreSQL jsonb_path_exists(col::jsonb, 'path') helper.
 *
 * Note: the JSONPath is intended to be a constant string (not user input).
 */
class JsonbPathExists(val col: Column<*>, val jsonPath: String) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("jsonb_path_exists(")
        queryBuilder.append(col)
        queryBuilder.append("::jsonb, '")
        queryBuilder.append(jsonPath.replace("'", "''"))
        queryBuilder.append("')")
    }
}

/**
 * PostgreSQL (jsonb_path_query_first(col::jsonb, 'path') #>> '{}') helper.
 * Returns a scalar JSON value as text, or null if not found.
 */
class JsonbPathQueryFirstText(val col: Column<*>, val jsonPath: String) : Function<String>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("(")
        queryBuilder.append("jsonb_path_query_first(")
        queryBuilder.append(col)
        queryBuilder.append("::jsonb, '")
        queryBuilder.append(jsonPath.replace("'", "''"))
        queryBuilder.append("') #>> '{}')")
    }
}
