package no.nav.flexjar.repository

import kotlinx.serialization.json.*
import no.nav.flexjar.domain.*
import no.nav.flexjar.service.DiscoveryService
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
            var records = dbQuery.map { it.toDto() }
            
            // Task filter: filter by specific task name if provided
            query.task?.let { taskFilter ->
                records = records.filter { feedback ->
                    val taskAnswer = feedback.answers.find { a -> 
                        a.fieldId in TopTasksFieldIds.task
                    }
                    if (taskAnswer != null && taskAnswer.fieldType == FieldType.SINGLE_CHOICE) {
                        val selectedId = (taskAnswer.value as? AnswerValue.SingleChoice)?.selectedOptionId
                        val option = taskAnswer.question.options?.find { it.id == selectedId }
                        option?.label == taskFilter
                    } else false
                }
            }
            
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

    fun getTaskPriorityStats(query: StatsQuery): TaskPriorityResponse {
        return transaction {
            val dbQuery = FeedbackTable.selectAll()
            applyStatsFilters(dbQuery, query)
            val records = dbQuery.map { it.toDto() }
                .filter { it.surveyType == SurveyType.TASK_PRIORITY }

            val voteCounts = mutableMapOf<String, Int>()
            val taskLabels = mutableMapOf<String, String>()

            for (feedback in records) {
                val priorityAnswer = feedback.answers.find { a ->
                    a.fieldId == "priority" && a.fieldType == FieldType.MULTI_CHOICE
                } ?: continue

                val selectedIds = (priorityAnswer.value as? AnswerValue.MultiChoice)?.selectedOptionIds.orEmpty()
                if (selectedIds.isEmpty()) continue

                // Cache labels
                priorityAnswer.question.options?.forEach { opt ->
                    taskLabels.putIfAbsent(opt.id, opt.label)
                }

                for (taskId in selectedIds) {
                    voteCounts[taskId] = (voteCounts[taskId] ?: 0) + 1
                }
            }

            val tasks = voteCounts.entries
                .sortedByDescending { it.value }
                .map { (taskId, votes) ->
                    TaskVote(
                        task = taskLabels[taskId] ?: taskId,
                        votes = votes,
                        percentage = 0
                    )
                }

            val totalVotes = tasks.sumOf { it.votes }
            val tasksWithPercentages = if (totalVotes > 0) {
                tasks.map { it.copy(percentage = kotlin.math.round((it.votes.toDouble() / totalVotes) * 100.0).toInt()) }
            } else tasks

            // Find long neck cutoff (where cumulative percentage hits 80%)
            var cumulative = 0
            var longNeckCutoff = 0
            for (i in tasksWithPercentages.indices) {
                cumulative += tasksWithPercentages[i].percentage
                if (cumulative >= 80) {
                    longNeckCutoff = i + 1
                    break
                }
            }
            if (longNeckCutoff == 0) {
                longNeckCutoff = tasksWithPercentages.size
            }

            val cumulativePercentageAt5 = tasksWithPercentages
                .take(5)
                .sumOf { it.percentage }

            TaskPriorityResponse(
                totalSubmissions = records.size,
                tasks = tasksWithPercentages,
                longNeckCutoff = longNeckCutoff,
                cumulativePercentageAt5 = cumulativePercentageAt5,
            )
        }
    }

    fun getBlockerStats(query: StatsQuery, themes: List<TextThemeDto>): BlockerStatsResponse {
        return transaction {
            val dbQuery = FeedbackTable.selectAll()
            applyStatsFilters(dbQuery, query)
            var records = dbQuery.map { it.toDto() }

            records = records.filter { it.surveyType == SurveyType.TOP_TASKS }

            // Extract blocker responses with optional task filter (matches option label)
            val blockerResponses = mutableListOf<RecentBlockerResponse>()

            for (feedback in records) {
                val blockerAnswer = feedback.answers.find { a ->
                    a.fieldId in TopTasksFieldIds.blocker
                }
                val blockerText = (blockerAnswer?.value as? AnswerValue.Text)?.text?.trim().orEmpty()
                if (blockerText.isBlank()) continue

                val taskAnswer = feedback.answers.find { a ->
                    a.fieldId in TopTasksFieldIds.task
                }

                val taskLabel = when {
                    taskAnswer != null && taskAnswer.fieldType == FieldType.SINGLE_CHOICE -> {
                        val selectedId = (taskAnswer.value as? AnswerValue.SingleChoice)?.selectedOptionId
                        val option = taskAnswer.question.options?.find { it.id == selectedId }
                        option?.label ?: "Ukjent oppgave"
                    }
                    else -> "Ukjent oppgave"
                }

                if (query.task != null && taskLabel != query.task) continue

                blockerResponses.add(
                    RecentBlockerResponse(
                        blocker = blockerText,
                        task = taskLabel,
                        submittedAt = feedback.submittedAt
                    )
                )
            }

            val wordData = mutableMapOf<String, WordAccumulator>()
            for (response in blockerResponses) {
                val words = extractWords(response.blocker)
                val seenWordsInResponse = mutableSetOf<String>()
                for (word in words) {
                    val acc = wordData.getOrPut(word) { WordAccumulator() }
                    acc.count++
                    if (!seenWordsInResponse.contains(word) && acc.sourceResponses.size < 5) {
                        acc.sourceResponses.add(BlockerSourceResponse(text = response.blocker, submittedAt = response.submittedAt))
                        seenWordsInResponse.add(word)
                    }
                }
            }

            val wordFrequency = wordData.entries
                .sortedByDescending { it.value.count }
                .take(30)
                .map { (word, acc) ->
                    BlockerWordFrequencyEntry(
                        word = word,
                        count = acc.count,
                        sourceResponses = acc.sourceResponses.toList()
                    )
                }

            val themeAccumulators = themes.map { theme ->
                ThemeAccumulator(
                    theme = theme.name,
                    themeId = theme.id,
                    color = theme.color,
                )
            }.toMutableList()

            val annetThemeId = "blocker-annet"
            themeAccumulators.add(
                ThemeAccumulator(
                    theme = "Annet",
                    themeId = annetThemeId,
                    color = "var(--ax-text-neutral-subtle)",
                )
            )

            for (response in blockerResponses) {
                val blockerWordStems = extractWords(response.blocker).map { stemNorwegian(it) }
                var matchedAny = false

                for (acc in themeAccumulators) {
                    if (acc.themeId == annetThemeId) continue

                    val theme = themes.find { it.id == acc.themeId } ?: continue
                    val keywordStems = theme.keywords.map { stemNorwegian(it.lowercase()) }
                    if (keywordStems.any { it in blockerWordStems }) {
                        acc.count++
                        if (acc.examples.size < 3 && acc.usedExamples.add(response.blocker)) {
                            acc.examples.add(response.blocker)
                        }
                        matchedAny = true
                    }
                }

                if (!matchedAny) {
                    val annet = themeAccumulators.find { it.themeId == annetThemeId }
                    if (annet != null) {
                        annet.count++
                        if (annet.examples.size < 3 && annet.usedExamples.add(response.blocker)) {
                            annet.examples.add(response.blocker)
                        }
                    }
                }
            }

            val themeResults = themeAccumulators
                .filter { it.count > 0 }
                .sortedByDescending { it.count }
                .map { it.toResult() }

            val recentBlockers = blockerResponses
                .sortedByDescending { parseSubmittedAt(it.submittedAt) }
                .take(10)

            BlockerStatsResponse(
                totalBlockers = blockerResponses.size,
                wordFrequency = wordFrequency,
                themes = themeResults,
                recentBlockers = recentBlockers
            )
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

        // Segment filter (context.tags)
        criteria.segments.forEach { (key, value) ->
            val safeKey = key.trim()
            val safeValue = value.trim()
            if (safeKey.isBlank() || safeValue.isBlank()) return@forEach
            query.andWhere {
                JsonExtract(FeedbackTable.feedbackJson, listOf("context", "tags", safeKey)) eq safeValue
            }
        }
    }

    private fun extractWords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-zæøåA-ZÆØÅ0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in DiscoveryService.STOP_WORDS }
    }

    private fun stemNorwegian(word: String): String {
        var stem = word.lowercase().trim()

        val suffixes = listOf(
            "ene", "ane",
            "en", "et", "a",
            "er", "ar",
            "te", "de",
            "ere", "est"
        )

        for (suffix in suffixes) {
            if (stem.length > suffix.length + 2 && stem.endsWith(suffix)) {
                return stem.dropLast(suffix.length)
            }
        }

        return stem
    }

    private fun parseSubmittedAt(value: String): Instant {
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(value).toInstant()
            } catch (_: Exception) {
                Instant.EPOCH
            }
        }
    }
    

private class WordAccumulator(
    var count: Int = 0,
    val sourceResponses: MutableList<BlockerSourceResponse> = mutableListOf()
)

private class ThemeAccumulator(
    val theme: String,
    val themeId: String,
    val color: String?,
    val examples: MutableList<String> = mutableListOf(),
    val usedExamples: MutableSet<String> = mutableSetOf(),
    var count: Int = 0,
) {
    fun toResult(): BlockerThemeResult {
        return BlockerThemeResult(
            theme = theme,
            themeId = themeId,
            count = count,
            examples = examples.toList(),
            color = color,
        )
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
