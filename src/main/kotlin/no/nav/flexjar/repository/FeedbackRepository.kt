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

class FeedbackRepository(
    private val sensitiveDataFilter: SensitiveDataFilter = SensitiveDataFilter.DEFAULT
) {
    private val log = LoggerFactory.getLogger(FeedbackRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun findById(id: String): FeedbackDto? {
        return transaction {
            FeedbackTable.selectAll().where { FeedbackTable.id eq id }
                .singleOrNull()
                ?.toDto()
        }
    }
    
    /**
     * Get raw database record for ownership verification.
     * Returns team and app for the given feedback ID.
     */
    fun findRawById(id: String): FeedbackDbRecord? {
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

    fun update(id: String, feedbackJson: String): Boolean {
        return transaction {
            FeedbackTable.update({ FeedbackTable.id eq id }) {
                it[FeedbackTable.feedbackJson] = feedbackJson
            } > 0
        }
    }

    fun softDelete(id: String): Boolean {
        return transaction {
            val record = FeedbackTable.selectAll().where { FeedbackTable.id eq id }.singleOrNull() ?: return@transaction false
            
            val existingJson = record[FeedbackTable.feedbackJson]
            
            val jsonObj = try {
                json.parseToJsonElement(existingJson).jsonObject
            } catch (e: Exception) {
                return@transaction false
            }
            
            val answers = jsonObj["answers"] as? kotlinx.serialization.json.JsonArray ?: return@transaction false
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
            
            FeedbackTable.update({ FeedbackTable.id eq id }) {
                it[FeedbackTable.feedbackJson] = newJson
            } > 0
        }
    }

    fun addTag(id: String, tag: String): Boolean {
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
    
    fun removeTag(id: String, tag: String): Boolean {
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

    fun findAllTags(): Set<String> {
        return transaction {
             FeedbackTable.select(FeedbackTable.tags)
                .where { FeedbackTable.tags.isNotNull() and (FeedbackTable.tags neq "") }
                .withDistinct()
                .mapNotNull { it[FeedbackTable.tags] }
                .flatMap { it.split(",") }
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
        }
    }

    fun findAllTeamsAndApps(): Map<String, Set<String>> {
        return transaction {
             val result = mutableMapOf<String, MutableSet<String>>()
             FeedbackTable.select(FeedbackTable.team, FeedbackTable.app)
                .withDistinct()
                .forEach { 
                    val team = it[FeedbackTable.team]
                    val app = it[FeedbackTable.app]
                    result.getOrPut(team) { mutableSetOf() }
                    if (app != null) {
                        result[team]!!.add(app)
                    }
                }
             result
        }
    }
    
    fun findMetadataKeysForSurvey(feedbackId: String, team: String = "flex"): Map<String, Set<String>> {
        return transaction {
            val sql = """
                SELECT DISTINCT 
                    key as metadata_key,
                    feedback_json::json->'metadata'->>key as metadata_value
                FROM feedback,
                     jsonb_object_keys(feedback_json::jsonb->'metadata') as key
                WHERE team = ?
                  AND feedback_json::json->>'feedbackId' = ?
                  AND feedback_json::json->'metadata' IS NOT NULL
            """.trimIndent()
            
            val result = mutableMapOf<String, MutableSet<String>>()
            exec(sql, listOf(VarCharColumnType() to team, VarCharColumnType() to feedbackId)) { rs ->
                while (rs.next()) {
                    val key = rs.getString("metadata_key") ?: continue
                    val value = rs.getString("metadata_value") ?: continue
                    result.getOrPut(key) { mutableSetOf() }.add(value)
                }
            }
            result
        }
    }

    // Stats methods removed - moved to FeedbackStatsRepository

    
    private fun applyCommonFilters(query: Query, criteria: FeedbackQuery) {
        if (criteria.medTekst) {
             query.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("feedback")).isNotNull() }
             query.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("feedback")) neq "" }
        }
        
        if (criteria.stjerne) {
             query.andWhere { FeedbackTable.tags like "%stjerne%" }
        }
        
        criteria.feedbackId?.let { fid ->
             query.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("feedbackId")) eq fid }
        }
        
        criteria.from?.let { from ->
             query.andWhere { FeedbackTable.opprettet greaterEq Instant.parse(from) }
        }
        
        criteria.to?.let { to ->
             query.andWhere { FeedbackTable.opprettet lessEq Instant.parse(to) } 
        }
        
        if (criteria.lavRating) {
             val ratingExpr = Cast(JsonExtract(FeedbackTable.feedbackJson, listOf("svar")), IntegerColumnType())
             query.andWhere { ratingExpr lessEq 2 }
        }
        
        criteria.deviceType?.let { device ->
             query.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("context", "deviceType")) eq device }
        }
        
        criteria.tags.forEach { tag ->
             query.andWhere { FeedbackTable.tags like "%$tag%" }
        }
        
        criteria.fritekst.forEach { text ->
             val jsonText = JsonExtract(FeedbackTable.feedbackJson, listOf("feedback"))
             query.andWhere { (jsonText like "%$text%") or (FeedbackTable.tags like "%$text%") }
        }
    }

}
