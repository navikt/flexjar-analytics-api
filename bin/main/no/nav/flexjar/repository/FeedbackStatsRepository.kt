package no.nav.flexjar.repository

import kotlinx.serialization.json.*
import no.nav.flexjar.domain.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

class FeedbackStatsRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun getStats(query: StatsQuery): FeedbackStatsResult {
        return transaction {
            val totalQuery = FeedbackTable.selectAll()
            applyStatsFilters(totalQuery, query)
            val totalCount = totalQuery.count()
            
            val textQuery = FeedbackTable.selectAll()
            applyStatsFilters(textQuery, query)
            textQuery.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("feedback")).isNotNull() }
            textQuery.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("feedback")) neq "" }
            val countWithText = textQuery.count()
            
            val typeQuery = FeedbackTable.selectAll()
            applyStatsFilters(typeQuery, query)
            typeQuery.orderBy(FeedbackTable.opprettet to SortOrder.DESC).limit(1)
            val surveyType = typeQuery.firstOrNull()?.let { 
                 val jsonStr = it[FeedbackTable.feedbackJson]
                 val jsonObj = json.parseToJsonElement(jsonStr).jsonObject
                 jsonObj["surveyType"]?.jsonPrimitive?.content ?: "custom"
            }

            val ratingExpr = JsonExtract(FeedbackTable.feedbackJson, listOf("svar"))
            val ratingQuery = FeedbackTable.select(ratingExpr, FeedbackTable.id.count())
            applyStatsFilters(ratingQuery, query)
            ratingQuery.andWhere { ratingExpr.isNotNull() }
            val byRating = ratingQuery
                .groupBy(ratingExpr)
                .associate { row ->
                    (row[ratingExpr] ?: "unknown") to row[FeedbackTable.id.count()].toInt()
                }

            val appQuery = FeedbackTable.select(FeedbackTable.app, FeedbackTable.id.count())
            applyStatsFilters(appQuery, query)
            appQuery.andWhere { FeedbackTable.app.isNotNull() }
            val byApp = appQuery
                .groupBy(FeedbackTable.app)
                .orderBy(FeedbackTable.id.count() to SortOrder.DESC)
                .limit(20)
                .associate { row -> 
                    (row[FeedbackTable.app] ?: "unknown") to row[FeedbackTable.id.count()].toInt()
                }
            
            val dateExpr = DateDate(FeedbackTable.opprettet)
            val dateQuery = FeedbackTable.select(dateExpr, FeedbackTable.id.count())
            applyStatsFilters(dateQuery, query)
            dateQuery.andWhere { FeedbackTable.opprettet greaterEq Instant.now().minus(Duration.ofDays(30)) }
            val byDate = dateQuery
                 .groupBy(dateExpr)
                 .orderBy(dateExpr to SortOrder.ASC)
                 .associate { row ->
                     row[dateExpr].toString() to row[FeedbackTable.id.count()].toInt()
                 }
                 
            val feedbackIdExpr = JsonExtract(FeedbackTable.feedbackJson, listOf("feedbackId"))
            val fidQuery = FeedbackTable.select(feedbackIdExpr, FeedbackTable.id.count())
            applyStatsFilters(fidQuery, query)
            fidQuery.andWhere { feedbackIdExpr.isNotNull() }
            val byFeedbackId = fidQuery
                 .groupBy(feedbackIdExpr)
                 .orderBy(FeedbackTable.id.count() to SortOrder.DESC)
                 .limit(20)
                 .associate { row ->
                     (row[feedbackIdExpr] ?: "unknown") to row[FeedbackTable.id.count()].toInt()
                 }

            FeedbackStatsResult(
                totalCount = totalCount,
                countWithText = countWithText,
                surveyType = surveyType,
                byRating = byRating,
                byApp = byApp,
                byDate = byDate,
                byFeedbackId = byFeedbackId
            )
        }
    }

    fun getTopTasksStats(query: StatsQuery): TopTasksResponse {
        return transaction {
            val dbQuery = FeedbackTable.selectAll()
            applyStatsFilters(dbQuery, query)
            val records = dbQuery.map { it.toDto() }
            processTopTasks(records)
        }
    }
    
    private fun applyStatsFilters(query: Query, criteria: StatsQuery) {
         query.andWhere { FeedbackTable.team eq criteria.team }
         criteria.app?.let { query.andWhere { FeedbackTable.app eq it } }
         
         criteria.feedbackId?.let { fid ->
             query.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("feedbackId")) eq fid }
         }
         
         criteria.from?.let { from ->
             query.andWhere { FeedbackTable.opprettet greaterEq Instant.parse(from) }
         }
         
         criteria.to?.let { to ->
             query.andWhere { FeedbackTable.opprettet lessEq Instant.parse(to) } 
         }
         
         criteria.deviceType?.let { device ->
             query.andWhere { JsonExtract(FeedbackTable.feedbackJson, listOf("context", "deviceType")) eq device }
         }
    }
    
    // DTO methods removed - using Extensions.kt

    private fun processTopTasks(feedbacks: List<FeedbackDto>): TopTasksResponse {
        val taskStatsMap = mutableMapOf<String, MutableMap<String, Int>>()
        var totalSubmissions = 0
        val dailyStats = mutableMapOf<String, MutableMap<String, Int>>()
        var questionText: String? = null

        feedbacks.forEach { dto ->
             val taskAnswer = dto.answers.find { it.fieldId == "task" }
             if (taskAnswer == null) return@forEach
             
             totalSubmissions++
             
             val taskLabel = when (val v = taskAnswer.value) {
                 is AnswerValue.SingleChoice -> {
                     val optId = v.selectedOptionId
                     taskAnswer.question.options?.find { it.id == optId }?.label ?: optId
                 }
                 is AnswerValue.Text -> v.text
                 else -> "Ukjent oppgave"
             }
             
             if (questionText == null) {
                 questionText = taskAnswer.question.label
             }
             
             val successAnswer = dto.answers.find { it.fieldId == "taskSuccess" }
             val successValue = when (val v = successAnswer?.value) {
                 is AnswerValue.SingleChoice -> v.selectedOptionId // "yes", "partial", "no"
                 else -> null
             }
             
             val blockerAnswer = dto.answers.find { it.fieldId == "blocker" }
             val blockerValue = when (val v = blockerAnswer?.value) {
                 is AnswerValue.SingleChoice -> v.selectedOptionId
                 is AnswerValue.Text -> v.text
                 else -> null
             }
             
             // Daily stats
             val dateStr = LocalDate.parse(dto.submittedAt.substring(0, 10)).toString()
             val dayStat = dailyStats.getOrPut(dateStr) { mutableMapOf("total" to 0, "success" to 0) }
             dayStat["total"] = (dayStat["total"] ?: 0) + 1
             if (successValue == "yes") {
                 dayStat["success"] = (dayStat["success"] ?: 0) + 1
             }
             
             // Task stats
             val stats = taskStatsMap.getOrPut(taskLabel) { 
                mutableMapOf("total" to 0, "success" to 0, "partial" to 0, "failure" to 0) 
             }
             stats["total"] = (stats["total"] ?: 0) + 1
             when (successValue) {
                "yes" -> stats["success"] = (stats["success"] ?: 0) + 1
                "partial" -> stats["partial"] = (stats["partial"] ?: 0) + 1
                "no" -> stats["failure"] = (stats["failure"] ?: 0) + 1
             }
             
             if ((successValue == "no" || successValue == "partial") && !blockerValue.isNullOrBlank()) {
                val blockerKey = "blocker_$blockerValue"
                stats[blockerKey] = (stats[blockerKey] ?: 0) + 1
             }
        }
        
        val taskStatsList = taskStatsMap.map { (task, stats) ->
            val total = stats["total"] ?: 0
            val success = stats["success"] ?: 0
            val partial = stats["partial"] ?: 0
            val failure = stats["failure"] ?: 0
            val successRate = if (total > 0) (success.toDouble() / total.toDouble()) else 0.0
            val formattedRate = "${(successRate * 100).toInt()}%"
            val blockers = stats.filterKeys { it.startsWith("blocker_") }.mapKeys { it.key.removePrefix("blocker_") }
            
            TopTaskStats(task, total, success, partial, failure, successRate, formattedRate, blockers)
        }.sortedByDescending { it.totalCount }
        
        val dailyStatsResult = dailyStats.mapValues { (_, v) -> 
            no.nav.flexjar.domain.DailyStat(v["total"] ?: 0, v["success"] ?: 0)
        }
        
        return TopTasksResponse(totalSubmissions, taskStatsList, dailyStatsResult, questionText)
    }
}
