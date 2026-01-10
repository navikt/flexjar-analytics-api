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

    companion object {
        /** Minimum number of responses required to show aggregated statistics */
        const val MIN_AGGREGATION_THRESHOLD = 5
    }

    fun getStats(query: StatsQuery): FeedbackStatsResult {
        return transaction {
            val totalQuery = FeedbackTable.selectAll()
            applyStatsFilters(totalQuery, query)
            val totalCount = totalQuery.count()
            
            // Check if we should mask data for privacy
            val shouldMask = totalCount in 1 until MIN_AGGREGATION_THRESHOLD
            
            val textQuery = FeedbackTable.selectAll()
            applyStatsFilters(textQuery, query)
            textQuery.andWhere {
                JsonbPathExists(
                    FeedbackTable.feedbackJson,
                    "$.answers[*] ? (@.value.type == \"text\" && @.value.text != \"\")"
                )
            }
            val countWithText = textQuery.count()
            
            val typeQuery = FeedbackTable.selectAll()
            applyStatsFilters(typeQuery, query)
            typeQuery.orderBy(FeedbackTable.opprettet to SortOrder.DESC).limit(1)
            val surveyType = typeQuery.firstOrNull()?.let { 
                 val jsonStr = it[FeedbackTable.feedbackJson]
                 val jsonObj = json.parseToJsonElement(jsonStr).jsonObject
                 jsonObj["surveyType"]?.jsonPrimitive?.content ?: "custom"
            }

            val ratingExpr = JsonbPathQueryFirstText(
                FeedbackTable.feedbackJson,
                "$.answers[*] ? (@.value.type == \"rating\").value.rating"
            )
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
                 
            val surveyIdExpr = JsonExtract(FeedbackTable.feedbackJson, listOf("surveyId"))
            val fidQuery = FeedbackTable.select(surveyIdExpr, FeedbackTable.id.count())
            applyStatsFilters(fidQuery, query)
            fidQuery.andWhere { surveyIdExpr.isNotNull() }
            val bySurveyId = fidQuery
                 .groupBy(surveyIdExpr)
                 .orderBy(FeedbackTable.id.count() to SortOrder.DESC)
                 .limit(20)
                 .associate { row ->
                     (row[surveyIdExpr] ?: "unknown") to row[FeedbackTable.id.count()].toInt()
                 }

            FeedbackStatsResult(
                totalCount = totalCount,
                countWithText = countWithText,
                surveyType = surveyType,
                byRating = byRating,
                byApp = byApp,
                byDate = byDate,
                bySurveyId = bySurveyId,
                masked = shouldMask,
                threshold = MIN_AGGREGATION_THRESHOLD
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

    fun getSurveyTypeDistribution(query: StatsQuery): SurveyTypeDistribution {
        return transaction {
            val dbQuery = FeedbackTable.selectAll()
            applyStatsFilters(dbQuery, query)
            val records = dbQuery.map { it.toDto() }
            
            // Count unique surveys by type
            val surveyTypeCounts = mutableMapOf<SurveyType, Int>()
            val seenSurveys = mutableMapOf<String, SurveyType>()
            
            for (record in records) {
                val surveyType = record.surveyType ?: SurveyType.CUSTOM
                val surveyId = record.surveyId
                
                // Only count each survey once
                if (!seenSurveys.containsKey(surveyId)) {
                    seenSurveys[surveyId] = surveyType
                    surveyTypeCounts[surveyType] = (surveyTypeCounts[surveyType] ?: 0) + 1
                }
            }
            
            val totalSurveys = seenSurveys.size
            val distribution = surveyTypeCounts.map { (type, count) ->
                val percentage = if (totalSurveys > 0) (count * 100 / totalSurveys) else 0
                SurveyTypeCount(type, count, percentage)
            }.sortedByDescending { it.count }
            
            SurveyTypeDistribution(totalSurveys, distribution)
        }
    }

    
    private fun applyStatsFilters(query: Query, criteria: StatsQuery) {
        query.andWhere { FeedbackTable.team eq criteria.team }
        criteria.app?.let { query.andWhere { FeedbackTable.app eq it } }
        
        // Survey ID filter
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
                // If parsing fails, try as instant (backward compatibility)
                try {
                    query.andWhere { FeedbackTable.opprettet greaterEq Instant.parse(fromDate) }
                } catch (_: Exception) { }
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
                // If parsing fails, try as instant (backward compatibility)
                try {
                    query.andWhere { FeedbackTable.opprettet lessEq Instant.parse(toDate) }
                } catch (_: Exception) { }
            }
        }
        
        // Device type filter
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
