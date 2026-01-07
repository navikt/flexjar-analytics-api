package no.nav.flexjar.repository

import kotlinx.serialization.json.*
import no.nav.flexjar.domain.*
import no.nav.flexjar.sensitive.SensitiveDataFilter
import org.jetbrains.exposed.sql.ResultRow
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneId

private val log = LoggerFactory.getLogger("Extensions")
private val json = Json { ignoreUnknownKeys = true }
private val sensitiveDataFilter = SensitiveDataFilter.DEFAULT

fun ResultRow.toDto(): FeedbackDto {
     return FeedbackDbRecord(
        id = this[FeedbackTable.id],
        opprettet = OffsetDateTime.ofInstant(this[FeedbackTable.opprettet], ZoneId.of("Europe/Oslo")),
        feedbackJson = this[FeedbackTable.feedbackJson],
        team = this[FeedbackTable.team],
        app = this[FeedbackTable.app],
        tags = this[FeedbackTable.tags]
     ).toDto()
}

fun FeedbackDbRecord.createEmptyDto(): FeedbackDto {
    return FeedbackDto(
        id = id,
        submittedAt = opprettet.toString(),
        app = app,
        surveyId = "unknown",
        answers = emptyList(),
        sensitiveDataRedacted = false
    )
}

fun FeedbackDbRecord.toDto(): FeedbackDto {
    val jsonElement = try {
        json.parseToJsonElement(feedbackJson)
    } catch (e: Exception) {
        log.warn("Failed to parse feedback JSON for id=$id", e)
        return createEmptyDto()
    }
    
    val jsonObj = jsonElement.jsonObject
    val surveyId = jsonObj["surveyId"]?.jsonPrimitive?.content ?: jsonObj["feedbackId"]?.jsonPrimitive?.content ?: "unknown"
    val surveyVersion = jsonObj["surveyVersion"]?.jsonPrimitive?.contentOrNull
    
    val surveyTypeStr = jsonObj["surveyType"]?.jsonPrimitive?.contentOrNull
    val surveyType = when (surveyTypeStr) {
        "rating", "RATING" -> SurveyType.RATING
        "topTasks", "TOP_TASKS" -> SurveyType.TOP_TASKS
        "discovery", "DISCOVERY" -> SurveyType.DISCOVERY
        "taskPriority", "TASK_PRIORITY" -> SurveyType.TASK_PRIORITY
        "custom", "CUSTOM" -> SurveyType.CUSTOM
        null -> null
        else -> SurveyType.CUSTOM
    }
    
    val context = jsonObj["context"]?.jsonObject?.let { ctxObj ->
        val viewportObj = ctxObj["viewport"]?.jsonObject

        val viewportWidth =
            ctxObj["viewportWidth"]?.jsonPrimitive?.intOrNull
                ?: viewportObj?.get("width")?.jsonPrimitive?.intOrNull

        val viewportHeight =
            ctxObj["viewportHeight"]?.jsonPrimitive?.intOrNull
                ?: viewportObj?.get("height")?.jsonPrimitive?.intOrNull

        val tags = ctxObj["tags"]?.jsonObject?.entries?.associate { (key, value) ->
            key to (value.jsonPrimitive.contentOrNull ?: value.toString())
        }

        SubmissionContext(
            url = ctxObj["url"]?.jsonPrimitive?.contentOrNull,
            pathname = ctxObj["pathname"]?.jsonPrimitive?.contentOrNull,
            deviceType = ctxObj["deviceType"]?.jsonPrimitive?.contentOrNull?.let { dt ->
                try { DeviceType.valueOf(dt.uppercase()) } catch (e: Exception) { null }
            },
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            tags = tags
        )
    }
    
    val metadata = jsonObj["metadata"]?.jsonObject?.entries?.associate { (key, value) ->
        key to (value.jsonPrimitive.contentOrNull ?: value.toString())
    }
    
    val answersJson = jsonObj["answers"] as? JsonArray ?: return createEmptyDto()
    val answers = mutableListOf<Answer>()
    var hasRedactions = false
    
    for (answerElement in answersJson) {
        val answerObj = answerElement.jsonObject
        val fieldId = answerObj["fieldId"]?.jsonPrimitive?.content ?: continue
        val fieldTypeStr = answerObj["fieldType"]?.jsonPrimitive?.content ?: continue
        val fieldType = try { FieldType.valueOf(fieldTypeStr) } catch (e: Exception) { continue }
        
        val questionObj = answerObj["question"]?.jsonObject ?: continue
        val questionLabel = questionObj["label"]?.jsonPrimitive?.content ?: ""
        val questionDescription = questionObj["description"]?.jsonPrimitive?.contentOrNull
        val questionOptions = questionObj["options"]?.let { optionsArray ->
            (optionsArray as? JsonArray)?.mapNotNull { optionEl ->
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
                val selectedIds = (valueObj["selectedOptionIds"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: continue
                AnswerValue.MultiChoice(selectedIds)
            }
            "date" -> {
                val date = valueObj["date"]?.jsonPrimitive?.content ?: continue
                AnswerValue.DateValue(date)
            }
            else -> continue
        }
        
        answers.add(Answer(fieldId, fieldType, Question(questionLabel, questionDescription, questionOptions), answerValue))
    }
    
    return FeedbackDto(id, opprettet.toString(), app, surveyId, surveyVersion, surveyType, context, metadata, answers, hasRedactions)
}
