package no.nav.flexjar.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.flexjar.domain.*
import no.nav.flexjar.sensitive.SensitiveDataFilter
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import kotlin.math.ceil

/**
 * Escape special characters for SQL LIKE patterns to prevent SQL injection.
 * Escapes %, _, and \ which have special meaning in LIKE clauses.
 */
fun String.escapeLikePattern(): String {
    return this
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}

class FeedbackRepository {
    private val log = LoggerFactory.getLogger(FeedbackRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    internal fun findById(id: String): FeedbackDto? {
        return transaction {
            FeedbackTable.selectAll().where { FeedbackTable.id eq id }
                .singleOrNull()
                ?.toDto()
        }
    }

    fun findById(id: String, team: String): FeedbackDto? {
        return transaction {
            FeedbackTable.selectAll().where { (FeedbackTable.id eq id) and (FeedbackTable.team eq team) }
                .singleOrNull()
                ?.toDto()
        }
    }
    
    /**
     * Get raw database record for ownership verification.
     * Returns team and app for the given feedback ID.
     */
    internal fun findRawById(id: String): FeedbackDbRecord? {
        return transaction {
            FeedbackTable.selectAll().where { FeedbackTable.id eq id }
                .singleOrNull()
                ?.let {
                    FeedbackDbRecord(
                        id = it[FeedbackTable.id],
                        opprettet = OffsetDateTime.ofInstant(it[FeedbackTable.opprettet], java.time.ZoneOffset.UTC),
                        feedbackJson = it[FeedbackTable.feedbackJson],
                        team = it[FeedbackTable.team],
                        app = it[FeedbackTable.app],
                        tags = it[FeedbackTable.tags]
                    )
                }
        }
    }

    fun findRawById(id: String, team: String): FeedbackDbRecord? {
        return transaction {
            FeedbackTable.selectAll().where { (FeedbackTable.id eq id) and (FeedbackTable.team eq team) }
                .singleOrNull()
                ?.let {
                    FeedbackDbRecord(
                        id = it[FeedbackTable.id],
                        opprettet = OffsetDateTime.ofInstant(it[FeedbackTable.opprettet], java.time.ZoneOffset.UTC),
                        feedbackJson = it[FeedbackTable.feedbackJson],
                        team = it[FeedbackTable.team],
                        app = it[FeedbackTable.app],
                        tags = it[FeedbackTable.tags]
                    )
                }
        }
    }

    fun save(feedbackJson: String, team: String, app: String): String {
        val id = UUID.randomUUID().toString()
        
        transaction {
            FeedbackTable.insert {
                it[FeedbackTable.id] = id
                it[FeedbackTable.opprettet] = Instant.now()
                it[FeedbackTable.feedbackJson] = feedbackJson
                it[FeedbackTable.team] = team
                it[FeedbackTable.app] = app
            }
        }
        return id
    }

    internal fun update(id: String, feedbackJson: String): Boolean {
        return transaction {
            FeedbackTable.update({ FeedbackTable.id eq id }) {
                it[FeedbackTable.feedbackJson] = feedbackJson
            } > 0
        }
    }

    internal fun updateJson(id: String, feedbackJson: String): Boolean {
        return transaction {
            FeedbackTable.update({ FeedbackTable.id eq id }) {
                it[FeedbackTable.feedbackJson] = feedbackJson
            } > 0
        }
    }

    fun updateJson(id: String, team: String, feedbackJson: String): Boolean {
        return transaction {
            FeedbackTable.update({ (FeedbackTable.id eq id) and (FeedbackTable.team eq team) }) {
                it[FeedbackTable.feedbackJson] = feedbackJson
            } > 0
        }
    }

    /**
     * Permanently delete a feedback item from the database.
     * Returns true if the item was deleted, false if not found.
     */
    fun delete(id: String, team: String): Boolean {
        return transaction {
            FeedbackTable.deleteWhere { 
                (FeedbackTable.id eq id) and (FeedbackTable.team eq team) 
            } > 0
        }
    }

    /**
     * Permanently delete all feedback items for a given surveyId and team.
     *
     * Note: surveyId is stored inside feedback_json, so we filter using JSON extraction.
     * Returns number of deleted rows.
     */
    fun deleteSurvey(surveyId: String, team: String): Int {
        return transaction {
            FeedbackTable.deleteWhere {
                (JsonExtract(FeedbackTable.feedbackJson, listOf("surveyId")) eq surveyId) and
                    (FeedbackTable.team eq team)
            }
        }
    }

    internal fun addTag(id: String, tag: String): Boolean {
        return transaction {
            val record = FeedbackTable.select(FeedbackTable.tags)
                .where { FeedbackTable.id eq id }
                .singleOrNull() 
                ?: return@transaction false
                
            val currentTags = record[FeedbackTable.tags]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { it.trim().lowercase() }
                ?.toSet() 
                ?: emptySet()
            
            val newTags = currentTags + tag.lowercase()
            
            FeedbackTable.update({ FeedbackTable.id eq id }) {
                it[FeedbackTable.tags] = newTags.joinToString(",")
            } > 0
        }
    }

    fun addTag(id: String, team: String, tag: String): Boolean {
        return transaction {
            val record = FeedbackTable.select(FeedbackTable.tags)
                .where { (FeedbackTable.id eq id) and (FeedbackTable.team eq team) }
                .singleOrNull()
                ?: return@transaction false

            val currentTags = record[FeedbackTable.tags]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { it.trim().lowercase() }
                ?.toSet()
                ?: emptySet()

            val newTags = currentTags + tag.lowercase()

            FeedbackTable.update({ (FeedbackTable.id eq id) and (FeedbackTable.team eq team) }) {
                it[FeedbackTable.tags] = newTags.joinToString(",")
            } > 0
        }
    }
    
    internal fun removeTag(id: String, tag: String): Boolean {
        return transaction {
            val record = FeedbackTable.select(FeedbackTable.tags)
                .where { FeedbackTable.id eq id }
                .singleOrNull() 
                ?: return@transaction false
                
            val currentTags = record[FeedbackTable.tags]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { it.trim().lowercase() }
                ?.toSet() 
                ?: emptySet()
            
            val newTags = currentTags - tag.lowercase()
            val tagsStr = if (newTags.isEmpty()) null else newTags.joinToString(",")
            
            FeedbackTable.update({ FeedbackTable.id eq id }) {
                it[FeedbackTable.tags] = tagsStr
            } > 0
        }
    }

    fun removeTag(id: String, team: String, tag: String): Boolean {
        return transaction {
            val record = FeedbackTable.select(FeedbackTable.tags)
                .where { (FeedbackTable.id eq id) and (FeedbackTable.team eq team) }
                .singleOrNull()
                ?: return@transaction false

            val currentTags = record[FeedbackTable.tags]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { it.trim().lowercase() }
                ?.toSet()
                ?: emptySet()

            val newTags = currentTags - tag.lowercase()
            val tagsStr = if (newTags.isEmpty()) null else newTags.joinToString(",")

            FeedbackTable.update({ (FeedbackTable.id eq id) and (FeedbackTable.team eq team) }) {
                it[FeedbackTable.tags] = tagsStr
            } > 0
        }
    }

    fun findPaginated(query: FeedbackQuery): Triple<List<FeedbackDto>, Long, Int> {
        return transaction {
            val dbQuery = FeedbackTable.selectAll()
            
            dbQuery.andWhere { FeedbackTable.team eq query.team }
            
            query.app?.let { app ->
                if (app != FILTER_ALL) {
                    dbQuery.andWhere { FeedbackTable.app eq app } 
                }
            }
            
            applyCommonFilters(dbQuery, query)

            val total = dbQuery.count()
            val totalPages = if (query.size > 0) ceil(total.toDouble() / query.size).toInt() else 0
            val page = query.page ?: (totalPages - 1).coerceAtLeast(0)
            val offset = (page * query.size).toLong()

            val records = dbQuery
                .orderBy(FeedbackTable.opprettet to SortOrder.DESC)
                .limit(query.size).offset(offset)
                .map { it.toDto() }

            Triple(records, total, page)
        }
    }


    /**
     * Find all tags for a specific team.
     * Used by filter bootstrap endpoint.
     */
    fun findAllTags(team: String): Set<String> {
        return transaction {
             FeedbackTable.select(FeedbackTable.tags)
                .where { 
                    (FeedbackTable.team eq team) and 
                    FeedbackTable.tags.isNotNull() and 
                    (FeedbackTable.tags neq "") 
                }
                .withDistinct()
                .mapNotNull { it[FeedbackTable.tags] }
                .flatMap { it.split(",") }
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
        }
    }

    fun findDistinctApps(team: String): List<String> {
        return transaction {
            FeedbackTable.select(FeedbackTable.app)
                .where { (FeedbackTable.team eq team) and FeedbackTable.app.isNotNull() }
                .withDistinct()
                .mapNotNull { it[FeedbackTable.app] }
        }
    }

    /**
     * Find all surveys (surveyIds) grouped by app for a specific team.
     * Used by filter bootstrap endpoint and survey list.
     */
    fun findSurveysByApp(team: String): Map<String, List<String>> {
        return transaction {
            val sql = """
                SELECT DISTINCT 
                    app,
                    feedback_json::json->>'surveyId' as survey_id
                FROM feedback
                WHERE team = ?
                  AND app IS NOT NULL
                  AND feedback_json::json->>'surveyId' IS NOT NULL
                ORDER BY app, survey_id
            """.trimIndent()
            
            val result = mutableMapOf<String, MutableList<String>>()
            exec(sql, listOf(org.jetbrains.exposed.sql.VarCharColumnType() to team)) { rs ->
                while (rs.next()) {
                    val app = rs.getString("app") ?: continue
                    val surveyId = rs.getString("survey_id") ?: continue
                    result.getOrPut(app) { mutableListOf() }.add(surveyId)
                }
            }
            result
        }
    }
    
    fun findMetadataKeysForSurvey(surveyId: String, team: String): Map<String, Set<String>> {
        return transaction {
            val sql = """
                SELECT DISTINCT 
                    key as metadata_key,
                    feedback_json::json->'context'->'tags'->>key as metadata_value
                FROM feedback,
                     jsonb_object_keys(feedback_json::jsonb->'context'->'tags') as key
                WHERE team = ?
                  AND feedback_json::json->>'surveyId' = ?
                  AND feedback_json::json->'context'->'tags' IS NOT NULL
            """.trimIndent()
            
            val result = mutableMapOf<String, MutableSet<String>>()
            exec(sql, listOf(VarCharColumnType() to team, VarCharColumnType() to surveyId)) { rs ->
                while (rs.next()) {
                    val key = rs.getString("metadata_key") ?: continue
                    val value = rs.getString("metadata_value") ?: continue
                    result.getOrPut(key) { mutableSetOf() }.add(value)
                }
            }
            result
        }
    }

    fun findContextTagsForSurvey(
        surveyId: String,
        team: String,
        task: String? = null,
        segments: List<Pair<String, String>> = emptyList(),
    ): Map<String, List<MetadataValueWithCount>> {
        return transaction {
            if (task.isNullOrBlank()) {
                val segmentClauses = segments
                    .map { (key, _) ->
                        // Uses ->> to compare string values
                        "AND feedback_json::json->'context'->'tags'->> ? = ?"
                    }

                val sql = buildString {
                    append(
                        """
                    SELECT
                        key as tag_key,
                        feedback_json::json->'context'->'tags'->>key as tag_value,
                        COUNT(*) as tag_count
                    FROM feedback,
                         jsonb_object_keys(feedback_json::jsonb->'context'->'tags') as key
                    WHERE team = ?
                      AND feedback_json::json->>'surveyId' = ?
                      AND feedback_json::jsonb->'context'->'tags' IS NOT NULL
                    """.trimIndent()
                    )

                    if (segmentClauses.isNotEmpty()) {
                        append("\n")
                        append(segmentClauses.joinToString("\n"))
                    }

                    append("\n")
                    append(
                        """
                    GROUP BY key, tag_value
                """.trimIndent()
                    )
                }

                val result = mutableMapOf<String, MutableList<MetadataValueWithCount>>()

                val args = buildList {
                    add(VarCharColumnType() to team)
                    add(VarCharColumnType() to surveyId)

                    for ((key, value) in segments) {
                        val safeKey = key.trim()
                        val safeValue = value.trim()
                        if (safeKey.isBlank() || safeValue.isBlank()) continue
                        add(VarCharColumnType() to safeKey)
                        add(VarCharColumnType() to safeValue)
                    }
                }

                exec(sql, args) { rs ->
                    while (rs.next()) {
                        val key = rs.getString("tag_key") ?: continue
                        val value = rs.getString("tag_value") ?: continue
                        val count = rs.getInt("tag_count")
                        result.getOrPut(key) { mutableListOf() }
                            .add(MetadataValueWithCount(value = value, count = count))
                    }
                }

                // Keep stable order (desc by count, then value) for deterministic responses
                return@transaction result.mapValues { (_, values) ->
                    values.sortedWith(compareByDescending<MetadataValueWithCount> { it.count }.thenBy { it.value })
                }
            }

            val dbQuery = FeedbackTable.selectAll()
            dbQuery.andWhere { FeedbackTable.team eq team }
            dbQuery.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("surveyId")) eq surveyId }

            val records = dbQuery.map { it.toDto() }

            val taskFiltered = records.filter { feedback ->
                // Segment filter (context.tags)
                if (segments.isNotEmpty()) {
                    val tags = feedback.context?.tags
                    if (tags == null) return@filter false

                    val matchesSegments = segments.all { (key, value) ->
                        val safeKey = key.trim()
                        val safeValue = value.trim()
                        safeKey.isNotBlank() && safeValue.isNotBlank() && tags[safeKey] == safeValue
                    }
                    if (!matchesSegments) return@filter false
                }

                val taskAnswer = feedback.answers.find { a ->
                    a.fieldId in TopTasksFieldIds.task
                }
                if (taskAnswer != null && taskAnswer.fieldType == FieldType.SINGLE_CHOICE) {
                    val selectedId = (taskAnswer.value as? AnswerValue.SingleChoice)?.selectedOptionId
                    val option = taskAnswer.question.options?.find { it.id == selectedId }
                    option?.label == task
                } else {
                    false
                }
            }

            val counts = mutableMapOf<String, MutableMap<String, Int>>()
            for (feedback in taskFiltered) {
                val tags = feedback.context?.tags ?: continue
                for ((key, value) in tags) {
                    if (key.isBlank() || value.isBlank()) continue
                    val perKey = counts.getOrPut(key) { mutableMapOf() }
                    perKey[value] = (perKey[value] ?: 0) + 1
                }
            }

            counts.mapValues { (_, valueCounts) ->
                valueCounts.entries
                    .map { (value, count) -> MetadataValueWithCount(value = value, count = count) }
                    .sortedWith(compareByDescending<MetadataValueWithCount> { it.count }.thenBy { it.value })
            }
        }
    }

    // Stats methods removed - moved to FeedbackStatsRepository

    
    private fun applyCommonFilters(query: Query, criteria: FeedbackQuery) {
        // Filter for feedback with text responses
        if (criteria.hasText) {
            query.andWhere {
                JsonbPathExists(
                    FeedbackTable.feedbackJson,
                    "$.answers[*] ? (@.value.type == \"text\" && @.value.text != \"\")"
                )
            }
        }
        
        // Filter by survey ID
        criteria.surveyId?.let { surveyId ->
            query.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("surveyId")) eq surveyId }
        }
        
        // Date range filter - convert YYYY-MM-DD to UTC Instants (Europe/Oslo)
        criteria.fromDate?.let { fromDate ->
            try {
                val localDate = java.time.LocalDate.parse(fromDate)
                val startOfDay = localDate.atStartOfDay(java.time.ZoneId.of("Europe/Oslo")).toInstant()
                query.andWhere { FeedbackTable.opprettet greaterEq startOfDay }
            } catch (e: Exception) {
                log.warn("Invalid fromDate format: $fromDate, expected YYYY-MM-DD")
            }
        }
        
        criteria.toDate?.let { toDate ->
            try {
                val localDate = java.time.LocalDate.parse(toDate)
                // Inclusive date filter: < start of next day in Europe/Oslo
                val nextDayStart = localDate.plusDays(1)
                    .atStartOfDay(java.time.ZoneId.of("Europe/Oslo"))
                    .toInstant()
                query.andWhere { FeedbackTable.opprettet less nextDayStart }
            } catch (e: Exception) {
                log.warn("Invalid toDate format: $toDate, expected YYYY-MM-DD")
            }
        }
        
        // Filter for low ratings (1-2)
        if (criteria.lowRating) {
            val ratingText = JsonbPathQueryFirstText(
                FeedbackTable.feedbackJson,
                "$.answers[*] ? (@.value.type == \"rating\").value.rating"
            )
            val ratingExpr = Cast(ratingText, IntegerColumnType())
            query.andWhere { ratingExpr lessEq 2 }
        }
        
        // Device type filter
        criteria.deviceType?.let { device ->
            query.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("context", "deviceType")) eq device }
        }
        
        // Tags filter
        criteria.tags.forEach { tag ->
            val escaped = tag.escapeLikePattern()
            query.andWhere { FeedbackTable.tags like "%$escaped%" }
        }
        
        // Full-text search query
        criteria.query?.let { searchQuery ->
            val escaped = searchQuery.escapeLikePattern()
            query.andWhere { (FeedbackTable.feedbackJson like "%$escaped%") or (FeedbackTable.tags like "%$escaped%") }
        }
        
        // Segment filter (context.tags)
        criteria.segments.forEach { (key, value) ->
            val safeKey = key.trim()
            val safeValue = value.trim()
            if (safeKey.isBlank() || safeValue.isBlank()) return@forEach
            // Query JSONB context->tags->key = value
            query.andWhere { 
                JsonExtract(FeedbackTable.feedbackJson, listOf("context", "tags", safeKey)) eq safeValue 
            }
        }
    }

}
