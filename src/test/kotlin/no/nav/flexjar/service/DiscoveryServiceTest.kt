package no.nav.flexjar.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import no.nav.flexjar.domain.*

class DiscoveryServiceTest : FunSpec({
    val service = DiscoveryService()


    context("processStats") {
        test("returns empty response for empty feedback list") {
            val result = service.processStats(emptyList(), emptyList())
            
            result.totalSubmissions shouldBe 0
            result.wordFrequency.shouldBeEmpty()
            result.themes.shouldBeEmpty()
            result.recentResponses.shouldBeEmpty()
        }

        test("calculates word frequency correctly") {
            val feedbacks = listOf(
                createDiscoveryFeedback("Sjekke sykepenger status"),
                createDiscoveryFeedback("Sjekke utbetaling status"),
                createDiscoveryFeedback("Sjekke søknad status")
            )
            
            val result = service.processStats(feedbacks, emptyList())
            
            val sjekkeEntry = result.wordFrequency.find { it.word == "sjekke" }
            sjekkeEntry?.count shouldBe 3
            
            val statusEntry = result.wordFrequency.find { it.word == "status" }
            statusEntry?.count shouldBe 3
        }

        test("groups by theme correctly") {
            val themes = listOf(
                TextThemeDto("1", "team", "Sykepenger", listOf("sykepenger"), priority = 1, analysisContext = AnalysisContext.GENERAL_FEEDBACK),
                TextThemeDto("2", "team", "Utbetaling", listOf("utbetaling"), priority = 1, analysisContext = AnalysisContext.GENERAL_FEEDBACK)
            )
            val feedbacks = listOf(
                createDiscoveryFeedback("Sjekke sykepenger", "yes"),
                createDiscoveryFeedback("Sjekke sykepenger", "yes"),
                createDiscoveryFeedback("Sjekke utbetaling", "no")
            )
            
            val result = service.processStats(feedbacks, themes)
            
            val sykTheme = result.themes.find { it.theme == "Sykepenger" }
            sykTheme?.count shouldBe 2
            sykTheme?.successRate shouldBe 1.0
            
            val utbTheme = result.themes.find { it.theme == "Utbetaling" }
            utbTheme?.count shouldBe 1
            utbTheme?.successRate shouldBe 0.0
        }

        test("calculates success rate with partial weighting") {
            val feedbacks = listOf(
                createDiscoveryFeedback("Nav status sjekk", "yes"),     // 1.0
                createDiscoveryFeedback("Nav status sjekk", "partial"), // 0.5
                createDiscoveryFeedback("Nav status sjekk", "no")       // 0.0
            )
            
            val result = service.processStats(feedbacks, emptyList())
            
            // All go to "Annet" since no themes defined
            val annetTheme = result.themes.find { it.theme == "Annet" }
            annetTheme?.count shouldBe 3
            // (1.0 + 0.5 + 0.0) / 3 = 0.5
            annetTheme?.successRate shouldBe 0.5
        }

        test("limits examples to MAX_EXAMPLES") {
            val feedbacks = (1..10).map { 
                createDiscoveryFeedback("Task text number $it", "yes") 
            }
            
            val result = service.processStats(feedbacks, emptyList())
            
            val annetTheme = result.themes.find { it.theme == "Annet" }
            annetTheme?.examples?.size shouldBe DiscoveryService.MAX_EXAMPLES
        }

        test("limits recent responses to MAX_RECENT_RESPONSES") {
            val feedbacks = (1..30).map { 
                createDiscoveryFeedback("Task $it", "yes") 
            }
            
            val result = service.processStats(feedbacks, emptyList())
            
            result.recentResponses shouldHaveSize DiscoveryService.MAX_RECENT_RESPONSES
        }
    }
})

/**
 * Helper to create a discovery feedback DTO for testing
 */
private fun createDiscoveryFeedback(
    taskText: String,
    success: String = "yes",
    blocker: String? = null
): FeedbackDto {
    val answers = mutableListOf(
        Answer(
            fieldId = "task",
            fieldType = FieldType.TEXT,
            question = Question("Hva kom du for å gjøre?"),
            value = AnswerValue.Text(taskText)
        ),
        Answer(
            fieldId = "success",
            fieldType = FieldType.SINGLE_CHOICE,
            question = Question("Fikk du gjort det?", options = listOf(
                ChoiceOption("yes", "Ja"),
                ChoiceOption("partial", "Delvis"),
                ChoiceOption("no", "Nei")
            )),
            value = AnswerValue.SingleChoice(success)
        )
    )
    
    if (blocker != null) {
        answers.add(Answer(
            fieldId = "blocker",
            fieldType = FieldType.TEXT,
            question = Question("Hva hindret deg?"),
            value = AnswerValue.Text(blocker)
        ))
    }
    
    return FeedbackDto(
        id = java.util.UUID.randomUUID().toString(),
        submittedAt = java.time.OffsetDateTime.now().toString(),
        app = "test-app",
        surveyId = "survey-discovery",
        surveyType = SurveyType.DISCOVERY,
        answers = answers
    )
}
