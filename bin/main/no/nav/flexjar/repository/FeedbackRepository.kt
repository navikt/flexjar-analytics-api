package no.nav.flexjar.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.flexjar.config.dataSource
import no.nav.flexjar.domain.Answer
import no.nav.flexjar.domain.AnswerValue
import no.nav.flexjar.domain.ChoiceOption
import no.nav.flexjar.domain.DeviceType
import no.nav.flexjar.domain.FeedbackDbRecord
import no.nav.flexjar.domain.FeedbackDto
import no.nav.flexjar.domain.FeedbackQuery
import no.nav.flexjar.domain.FieldType
import no.nav.flexjar.domain.Question
import no.nav.flexjar.domain.StatsQuery
import no.nav.flexjar.domain.SubmissionContext
import no.nav.flexjar.sensitive.SensitiveDataFilter
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.math.ceil

class FeedbackRepository(
    private val sensitiveDataFilter: SensitiveDataFilter = SensitiveDataFilter.DEFAULT
) {
    private val log = LoggerFactory.getLogger(FeedbackRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    
    fun findPaginated(query: FeedbackQuery): Triple<List<FeedbackDto>, Long, Int> {
        val whereClause = buildWhereClause(query)
        val params = buildParams(query)
        
        // Count total
        val countSql = "SELECT COUNT(*) FROM feedback $whereClause"
        val total = executeCount(countSql, params)
        
        val totalPages = if (query.size > 0) ceil(total.toDouble() / query.size).toInt() else 0
        val page = query.page ?: (totalPages - 1).coerceAtLeast(0)
        val offset = page * query.size
        
        // Fetch page
        val selectSql = """
            SELECT id, opprettet, feedback_json, team, app, tags 
            FROM feedback 
            $whereClause 
            ORDER BY opprettet DESC 
            LIMIT ? OFFSET ?
        """.trimIndent()
        
        val records = executeQuery(selectSql, params + listOf(query.size, offset))
        val dtos = records.map { it.toDto() }
        
        return Triple(dtos, total, page)
    }
    
    fun findById(id: String): FeedbackDto? {
        val sql = "SELECT id, opprettet, feedback_json, team, app, tags FROM feedback WHERE id = ?"
        return executeQuery(sql, listOf(id)).firstOrNull()?.toDto()
    }
    
    fun findAllTags(): Set<String> {
        val sql = "SELECT DISTINCT tags FROM feedback WHERE tags IS NOT NULL AND tags != ''"
        
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val tags = mutableSetOf<String>()
                    while (rs.next()) {
                        rs.getString("tags")?.split(",")?.forEach { tag ->
                            if (tag.isNotBlank()) tags.add(tag.trim().lowercase())
                        }
                    }
                    tags
                }
            }
        }
    }
    
    fun findAllTeamsAndApps(): Map<String, Set<String>> {
        val sql = "SELECT DISTINCT team, app FROM feedback"
        
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = mutableMapOf<String, MutableSet<String>>()
                    while (rs.next()) {
                        val team = rs.getString("team")
                        val app = rs.getString("app")
                        result.getOrPut(team) { mutableSetOf() }
                        if (app != null) {
                            result[team]!!.add(app)
                        }
                    }
                    result
                }
            }
        }
    }
    
    fun getStats(query: StatsQuery): Map<String, Any> {
        val whereClause = buildStatsWhereClause(query)
        val params = buildStatsParams(query)
        
        val stats = mutableMapOf<String, Any>()
        
        // Total count
        val countSql = "SELECT COUNT(*) FROM feedback $whereClause"
        stats["totalCount"] = executeCount(countSql, params)
        
        // Count with text
        val withTextSql = "SELECT COUNT(*) FROM feedback $whereClause AND feedback_json::json->>'feedback' IS NOT NULL AND feedback_json::json->>'feedback' != ''"
        stats["countWithText"] = executeCount(withTextSql, params)
        
        // By rating (svar field)
        val byRatingSql = """
            SELECT feedback_json::json->>'svar' as rating, COUNT(*) as count 
            FROM feedback 
            $whereClause AND feedback_json::json->>'svar' IS NOT NULL
            GROUP BY feedback_json::json->>'svar'
        """.trimIndent()
        stats["byRating"] = executeGroupCount(byRatingSql, params, "rating")
        
        // By app
        val byAppSql = """
            SELECT app, COUNT(*) as count 
            FROM feedback 
            $whereClause AND app IS NOT NULL
            GROUP BY app
            ORDER BY count DESC
            LIMIT 20
        """.trimIndent()
        stats["byApp"] = executeGroupCount(byAppSql, params, "app")
        
        // By date (last 30 days)
        val byDateSql = """
            SELECT DATE(opprettet) as date, COUNT(*) as count 
            FROM feedback 
            $whereClause AND opprettet >= NOW() - INTERVAL '30 days'
            GROUP BY DATE(opprettet)
            ORDER BY date
        """.trimIndent()
        stats["byDate"] = executeGroupCount(byDateSql, params, "date")
        
        // By feedbackId
        val byFeedbackIdSql = """
            SELECT feedback_json::json->>'feedbackId' as feedbackId, COUNT(*) as count 
            FROM feedback 
            $whereClause AND feedback_json::json->>'feedbackId' IS NOT NULL
            GROUP BY feedback_json::json->>'feedbackId'
            ORDER BY count DESC
            LIMIT 20
        """.trimIndent()
        stats["byFeedbackId"] = executeGroupCount(byFeedbackIdSql, params, "feedbackId")
        
        // Rating by date (for RatingTrendChart)
        val ratingByDateSql = """
            SELECT DATE(opprettet) as date, 
                   AVG((feedback_json::json->>'svar')::numeric) as avg_rating,
                   COUNT(*) as count 
            FROM feedback 
            $whereClause 
            AND feedback_json::json->>'svar' IS NOT NULL
            AND opprettet >= NOW() - INTERVAL '30 days'
            GROUP BY DATE(opprettet)
            ORDER BY date
        """.trimIndent()
        stats["ratingByDate"] = executeRatingByDate(ratingByDateSql, params)
        
        // By device type (for DeviceBreakdownChart)
        val byDeviceSql = """
            SELECT feedback_json::json->'context'->>'deviceType' as device,
                   COUNT(*) as count,
                   AVG((feedback_json::json->>'svar')::numeric) as avg_rating
            FROM feedback 
            $whereClause 
            AND feedback_json::json->'context'->>'deviceType' IS NOT NULL
            GROUP BY feedback_json::json->'context'->>'deviceType'
        """.trimIndent()
        stats["byDevice"] = executeDeviceStats(byDeviceSql, params)
        
        // By pathname (for TopPathsChart)
        val byPathnameSql = """
            SELECT feedback_json::json->'context'->>'pathname' as pathname,
                   COUNT(*) as count,
                   AVG((feedback_json::json->>'svar')::numeric) as avg_rating
            FROM feedback 
            $whereClause 
            AND feedback_json::json->'context'->>'pathname' IS NOT NULL
            GROUP BY feedback_json::json->'context'->>'pathname'
            ORDER BY count DESC
            LIMIT 20
        """.trimIndent()
        stats["byPathname"] = executePathnameStats(byPathnameSql, params)
        
        // Lowest rating paths (new)
        val lowestRatingPathsSql = """
            SELECT feedback_json::json->'context'->>'pathname' as pathname,
                   COUNT(*) as count,
                   AVG((feedback_json::json->>'svar')::numeric) as avg_rating
            FROM feedback 
            $whereClause 
            AND feedback_json::json->'context'->>'pathname' IS NOT NULL
            AND feedback_json::json->>'svar' IS NOT NULL
            GROUP BY feedback_json::json->'context'->>'pathname'
            HAVING COUNT(*) >= 3
            ORDER BY avg_rating ASC
            LIMIT 5
        """.trimIndent()
        stats["lowestRatingPaths"] = executePathnameStats(lowestRatingPathsSql, params)
        
        return stats
    }
    
    fun addTag(id: String, tag: String): Boolean {
        val existingTags = getTagsForId(id) ?: return false
        val newTags = (existingTags + tag.lowercase()).toSet()
        
        val sql = "UPDATE feedback SET tags = ? WHERE id = ?"
        return executeUpdate(sql, listOf(newTags.joinToString(","), id)) > 0
    }
    
    fun removeTag(id: String, tag: String): Boolean {
        val existingTags = getTagsForId(id) ?: return false
        val newTags = existingTags - tag.lowercase()
        
        val tagsStr = if (newTags.isEmpty()) null else newTags.joinToString(",")
        val sql = "UPDATE feedback SET tags = ? WHERE id = ?"
        return executeUpdate(sql, listOf(tagsStr, id)) > 0
    }
    
    private fun getTagsForId(id: String): Set<String>? {
        val sql = "SELECT tags FROM feedback WHERE id = ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getString("tags")
                            ?.split(",")
                            ?.map { it.trim().lowercase() }
                            ?.filter { it.isNotBlank() }
                            ?.toSet()
                            ?: emptySet()
                    } else null
                }
            }
        }
    }
    
    fun softDelete(id: String): Boolean {
        // Remove all text answers from the stored JSON
        val existingJson = getJsonForId(id) ?: return false
        
        val jsonObj = try {
            json.parseToJsonElement(existingJson).jsonObject
        } catch (e: Exception) {
            return false
        }
        
        // Filter out text values from answers
        val answers = jsonObj["answers"] as? kotlinx.serialization.json.JsonArray ?: return false
        val filteredAnswers = answers.mapNotNull { answerEl ->
            val answerObj = answerEl.jsonObject
            val fieldType = answerObj["fieldType"]?.jsonPrimitive?.content
            if (fieldType == "TEXT") null else answerEl
        }
        
        val newJsonObj = jsonObj.toMutableMap()
        newJsonObj["answers"] = kotlinx.serialization.json.JsonArray(filteredAnswers)
        
        val newJson = Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(newJsonObj)
        )
        
        val sql = "UPDATE feedback SET feedback_json = ? WHERE id = ?"
        return executeUpdate(sql, listOf(newJson, id)) > 0
    }
    
    private fun getJsonForId(id: String): String? {
        val sql = "SELECT feedback_json FROM feedback WHERE id = ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("feedback_json") else null
                }
            }
        }
    }
    
    fun save(
        feedbackJson: String,
        team: String,
        app: String?
    ): String {
        val id = UUID.randomUUID().toString()
        val sql = """
            INSERT INTO feedback (id, opprettet, feedback_json, team, app) 
            VALUES (?, NOW(), ?, ?, ?)
        """.trimIndent()
        
        executeUpdate(sql, listOf(id, feedbackJson, team, app))
        return id
    }
    
    fun update(id: String, feedbackJson: String): Boolean {
        val sql = "UPDATE feedback SET feedback_json = ? WHERE id = ?"
        return executeUpdate(sql, listOf(feedbackJson, id)) > 0
    }
    
    // Helper methods
    
    private fun buildWhereClause(query: FeedbackQuery): String {
        val conditions = mutableListOf("team = ?")
        
        if (query.app != null) conditions.add("app = ?")
        if (query.medTekst) conditions.add("feedback_json::json->>'feedback' IS NOT NULL AND feedback_json::json->>'feedback' != ''")
        if (query.stjerne) conditions.add("tags LIKE '%stjerne%'")
        if (query.feedbackId != null) conditions.add("feedback_json::json->>'feedbackId' = ?")
        if (query.from != null) conditions.add("opprettet >= ?::timestamptz")
        if (query.to != null) conditions.add("opprettet <= ?::timestamptz")
        if (query.lavRating) conditions.add("(feedback_json::json->>'svar')::int <= 2")

        if (query.deviceType != null) conditions.add("feedback_json::json->'context'->>'deviceType' = ?")
        
        query.tags.forEachIndexed { index, _ ->
            conditions.add("tags LIKE ?")
        }
        
        query.fritekst.forEachIndexed { index, _ ->
            conditions.add("(feedback_json ILIKE ? OR tags ILIKE ?)")
        }
        
        return "WHERE " + conditions.joinToString(" AND ")
    }
    
    private fun buildParams(query: FeedbackQuery): List<Any?> {
        val params = mutableListOf<Any?>()
        params.add(query.team)
        
        if (query.app != null) params.add(query.app)
        if (query.feedbackId != null) params.add(query.feedbackId)
        if (query.from != null) params.add(query.from)
        if (query.to != null) params.add(query.to)
        if (query.deviceType != null) params.add(query.deviceType)
        
        query.tags.forEach { tag ->
            params.add("%$tag%")
        }
        
        query.fritekst.forEach { text ->
            params.add("%$text%")
            params.add("%$text%")
        }
        
        return params
    }
    
    private fun buildStatsWhereClause(query: StatsQuery): String {
        val conditions = mutableListOf("team = ?")
        
        if (query.app != null) conditions.add("app = ?")
        if (query.feedbackId != null) conditions.add("feedback_json::json->>'feedbackId' = ?")
        if (query.from != null) conditions.add("opprettet >= ?::timestamptz")
        if (query.to != null) conditions.add("opprettet <= ?::timestamptz")

        if (query.deviceType != null) conditions.add("feedback_json::json->'context'->>'deviceType' = ?")
        
        return "WHERE " + conditions.joinToString(" AND ")
    }
    
    private fun buildStatsParams(query: StatsQuery): List<Any?> {
        val params = mutableListOf<Any?>()
        params.add(query.team)
        
        if (query.app != null) params.add(query.app)
        if (query.feedbackId != null) params.add(query.feedbackId)
        if (query.from != null) params.add(query.from)
        if (query.to != null) params.add(query.to)
        if (query.deviceType != null) params.add(query.deviceType)
        
        return params
    }
    
    private fun executeQuery(sql: String, params: List<Any?>): List<FeedbackDbRecord> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<FeedbackDbRecord>()
                    while (rs.next()) {
                        results.add(rs.toFeedbackDbRecord())
                    }
                    results
                }
            }
        }
    }
    
    private fun executeCount(sql: String, params: List<Any?>): Long {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                }
            }
        }
    }
    
    private fun executeGroupCount(sql: String, params: List<Any?>, keyColumn: String): Map<String, Int> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeQuery().use { rs ->
                    val results = mutableMapOf<String, Int>()
                    while (rs.next()) {
                        val key = rs.getString(keyColumn) ?: "unknown"
                        val count = rs.getInt("count")
                        results[key] = count
                    }
                    results
                }
            }
        }
    }
    
    private fun executeRatingByDate(sql: String, params: List<Any?>): Map<String, Map<String, Any>> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeQuery().use { rs ->
                    val results = mutableMapOf<String, Map<String, Any>>()
                    while (rs.next()) {
                        val date = rs.getString("date") ?: continue
                        val avgRating = rs.getDouble("avg_rating")
                        val count = rs.getInt("count")
                        results[date] = mapOf("average" to avgRating, "count" to count)
                    }
                    results
                }
            }
        }
    }
    
    private fun executeDeviceStats(sql: String, params: List<Any?>): Map<String, Map<String, Any>> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeQuery().use { rs ->
                    val results = mutableMapOf<String, Map<String, Any>>()
                    while (rs.next()) {
                        val device = rs.getString("device") ?: continue
                        val count = rs.getInt("count")
                        val avgRating = rs.getDouble("avg_rating")
                        results[device] = mapOf("count" to count, "averageRating" to avgRating)
                    }
                    results
                }
            }
        }
    }
    
    private fun executePathnameStats(sql: String, params: List<Any?>): Map<String, Map<String, Any>> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeQuery().use { rs ->
                    val results = mutableMapOf<String, Map<String, Any>>()
                    while (rs.next()) {
                        val pathname = rs.getString("pathname") ?: continue
                        val count = rs.getInt("count")
                        val avgRating = rs.getDouble("avg_rating")
                        results[pathname] = mapOf("count" to count, "averageRating" to avgRating)
                    }
                    results
                }
            }
        }
    }
    
    private fun executeUpdate(sql: String, params: List<Any?>): Int {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                val result = stmt.executeUpdate()
                conn.commit()
                result
            }
        }
    }
    
    private fun ResultSet.toFeedbackDbRecord(): FeedbackDbRecord {
        return FeedbackDbRecord(
            id = getString("id"),
            opprettet = getObject("opprettet", OffsetDateTime::class.java),
            feedbackJson = getString("feedback_json"),
            team = getString("team"),
            app = getString("app"),
            tags = getString("tags")
        )
    }
    
    private fun FeedbackDbRecord.toDto(): FeedbackDto {
        val jsonElement = try {
            json.parseToJsonElement(feedbackJson)
        } catch (e: Exception) {
            log.warn("Failed to parse feedback JSON for id=$id", e)
            return createEmptyDto()
        }
        
        val jsonObj = jsonElement.jsonObject
        val surveyId = jsonObj["surveyId"]?.jsonPrimitive?.content 
            ?: jsonObj["feedbackId"]?.jsonPrimitive?.content 
            ?: "unknown"
        val surveyVersion = jsonObj["surveyVersion"]?.jsonPrimitive?.contentOrNull
        
        // Parse context if present
        val context = jsonObj["context"]?.jsonObject?.let { ctxObj ->
            SubmissionContext(
                url = ctxObj["url"]?.jsonPrimitive?.contentOrNull,
                pathname = ctxObj["pathname"]?.jsonPrimitive?.contentOrNull,
                deviceType = ctxObj["deviceType"]?.jsonPrimitive?.contentOrNull?.let { dt ->
                    try { DeviceType.valueOf(dt.uppercase()) } catch (e: Exception) { null }
                },
                viewportWidth = ctxObj["viewportWidth"]?.jsonPrimitive?.int
            )
        }
        
        val answersJson = jsonObj["answers"] as? kotlinx.serialization.json.JsonArray 
            ?: return createEmptyDto()
        
        val answers = mutableListOf<Answer>()
        var hasRedactions = false
        
        for (answerElement in answersJson) {
            val answerObj = answerElement.jsonObject
            val fieldId = answerObj["fieldId"]?.jsonPrimitive?.content ?: continue
            val fieldTypeStr = answerObj["fieldType"]?.jsonPrimitive?.content ?: continue
            val fieldType = try {
                FieldType.valueOf(fieldTypeStr)
            } catch (e: Exception) {
                continue
            }
            
            val questionObj = answerObj["question"]?.jsonObject ?: continue
            val questionLabel = questionObj["label"]?.jsonPrimitive?.content ?: ""
            val questionDescription = questionObj["description"]?.jsonPrimitive?.contentOrNull
            val questionOptions = questionObj["options"]?.let { optionsArray ->
                (optionsArray as? kotlinx.serialization.json.JsonArray)?.mapNotNull { optionEl ->
                    val optObj = optionEl.jsonObject
                    val optId = optObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val optLabel = optObj["label"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    ChoiceOption(optId, optLabel)
                }
            }
            
            val valueObj = answerObj["value"]?.jsonObject ?: continue
            val valueType = valueObj["type"]?.jsonPrimitive?.content ?: continue
            
            val answerValue: AnswerValue = when (valueType) {
                "rating" -> {
                    val rating = valueObj["rating"]?.jsonPrimitive?.int ?: continue
                    AnswerValue.Rating(rating)
                }
                "text" -> {
                    val text = valueObj["text"]?.jsonPrimitive?.content ?: ""
                    val result = sensitiveDataFilter.redact(text)
                    if (result.wasRedacted) hasRedactions = true
                    AnswerValue.Text(result.redactedText)
                }
                "singleChoice" -> {
                    val selectedId = valueObj["selectedOptionId"]?.jsonPrimitive?.content ?: continue
                    AnswerValue.SingleChoice(selectedId)
                }
                "multiChoice" -> {
                    val selectedIds = (valueObj["selectedOptionIds"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: continue
                    AnswerValue.MultiChoice(selectedIds)
                }
                "date" -> {
                    val date = valueObj["date"]?.jsonPrimitive?.content ?: continue
                    AnswerValue.DateValue(date)
                }
                else -> continue
            }
            
            answers.add(Answer(
                fieldId = fieldId,
                fieldType = fieldType,
                question = Question(
                    label = questionLabel,
                    description = questionDescription,
                    options = questionOptions
                ),
                value = answerValue
            ))
        }
        
        return FeedbackDto(
            id = id,
            submittedAt = opprettet.toString(),
            app = app,
            surveyId = surveyId,
            surveyVersion = surveyVersion,
            context = context,
            answers = answers,
            sensitiveDataRedacted = hasRedactions
        )
    }
    
    private fun FeedbackDbRecord.createEmptyDto(): FeedbackDto {
        return FeedbackDto(
            id = id,
            submittedAt = opprettet.toString(),
            app = app,
            surveyId = "unknown",
            answers = emptyList(),
            sensitiveDataRedacted = false
        )
    }
}
