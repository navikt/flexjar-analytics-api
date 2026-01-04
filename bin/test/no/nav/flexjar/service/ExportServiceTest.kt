package no.nav.flexjar.service

import no.nav.flexjar.domain.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ExportServiceTest {
    
    private val service = ExportService()
    
    @Test
    fun `exportToCsv generates correct header`() {
        val feedbacks = emptyList<FeedbackDto>()
        
        val csv = service.exportToCsv(feedbacks)
        
        assertTrue(csv.startsWith("id,submittedAt,app,surveyId,rating,feedback,sensitiveDataRedacted"))
    }
    
    @Test
    fun `exportToCsv includes feedback data`() {
        val feedbacks = listOf(
            FeedbackDto(
                id = "test-id-123",
                submittedAt = "2024-01-15T10:00:00Z",
                app = "test-app",
                surveyId = "survey-1",
                answers = listOf(
                    Answer(
                        fieldId = "rating",
                        fieldType = FieldType.RATING,
                        question = Question(label = "Rating"),
                        value = AnswerValue.Rating(4)
                    ),
                    Answer(
                        fieldId = "feedback",
                        fieldType = FieldType.TEXT,
                        question = Question(label = "Feedback"),
                        value = AnswerValue.Text("Great service!")
                    )
                ),
                sensitiveDataRedacted = false
            )
        )
        
        val csv = service.exportToCsv(feedbacks)
        
        assertTrue(csv.contains("test-id-123"))
        assertTrue(csv.contains("test-app"))
        assertTrue(csv.contains("4"))
        assertTrue(csv.contains("Great service!"))
    }
    
    @Test
    fun `exportToCsv escapes commas in text`() {
        val feedbacks = listOf(
            FeedbackDto(
                id = "test-id",
                submittedAt = "2024-01-15T10:00:00Z",
                app = "app",
                surveyId = "survey",
                answers = listOf(
                    Answer(
                        fieldId = "feedback",
                        fieldType = FieldType.TEXT,
                        question = Question(label = "Feedback"),
                        value = AnswerValue.Text("Hello, world")
                    )
                ),
                sensitiveDataRedacted = false
            )
        )
        
        val csv = service.exportToCsv(feedbacks)
        
        // Should escape the comma
        assertTrue(csv.contains("\"Hello, world\""))
    }
    
    @Test
    fun `exportToJson returns valid JSON`() {
        val feedbacks = listOf(
            FeedbackDto(
                id = "test-id",
                submittedAt = "2024-01-15T10:00:00Z",
                app = "app",
                surveyId = "survey",
                answers = emptyList(),
                sensitiveDataRedacted = false
            )
        )
        
        val json = service.exportToJson(feedbacks)
        
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("test-id"))
    }
    
    @Test
    fun `exportToExcel returns non-empty byte array`() {
        val feedbacks = listOf(
            FeedbackDto(
                id = "test-id",
                submittedAt = "2024-01-15T10:00:00Z",
                app = "app",
                surveyId = "survey",
                answers = emptyList(),
                sensitiveDataRedacted = false
            )
        )
        
        val bytes = service.exportToExcel(feedbacks)
        
        assertTrue(bytes.isNotEmpty())
        // XLSX files start with PK (ZIP header)
        assertEquals(0x50.toByte(), bytes[0])
        assertEquals(0x4B.toByte(), bytes[1])
    }
}
