package no.nav.flexjar.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.flexjar.TestDatabase
import no.nav.flexjar.config.DatabaseHolder
import no.nav.flexjar.insertTestFeedback
import no.nav.flexjar.insertTestFeedbackWithJson
import no.nav.flexjar.repository.FeedbackRepository
import java.util.UUID

class FeedbackServiceTest : FunSpec({

    val repository = FeedbackRepository()
    val service = FeedbackService(repository)

    beforeSpec {
        DatabaseHolder.initializeForTesting(TestDatabase.dataSource)
        TestDatabase.initialize()
    }

    beforeTest {
        TestDatabase.clearAllData()
    }

    context("save with redaction") {
        test("redacts sensitive data in feedback JSON") {
            val feedbackJson = """
                {
                    "answers": [
                        {
                            "fieldId": "text-answer",
                            "value": {
                                "type": "text",
                                "text": "Mitt fødselsnummer er 12345678901"
                            }
                        }
                    ]
                }
            """.trimIndent()
            
            val id = service.save(feedbackJson, "flex", "test-app")
            
            val saved = repository.findRawById(id)
            saved?.feedbackJson shouldNotContain "12345678901"
            saved?.feedbackJson shouldContain "FJERNET"
        }
    }

    context("softDelete") {
        test("removes text answers from feedback") {
            val id = UUID.randomUUID().toString()
            val feedbackJson = """
                {
                    "answers": [
                        {
                            "fieldId": "q1",
                            "fieldType": "TEXT",
                            "value": {"type": "text", "text": "Sensitive text"}
                        },
                        {
                            "fieldId": "q2",
                            "fieldType": "RATING",
                            "value": {"type": "rating", "rating": 5}
                        }
                    ]
                }
            """.trimIndent()
            
            insertTestFeedbackWithJson(id = id, team = "flex", feedbackJson = feedbackJson)
            
            val result = service.softDelete(id, "flex")
            
            result shouldBe true
            val updated = repository.findRawById(id)
            updated?.feedbackJson shouldNotContain "Sensitive text"
            updated?.feedbackJson shouldContain "RATING"
        }

        test("does not modify feedback from another team") {
            val id = UUID.randomUUID().toString()
            val feedbackJson = """
                {
                    "answers": [
                        {
                            "fieldId": "q1",
                            "fieldType": "TEXT",
                            "value": {"type": "text", "text": "Team secret"}
                        }
                    ]
                }
            """.trimIndent()

            insertTestFeedbackWithJson(id = id, team = "team-a", feedbackJson = feedbackJson)

            val result = service.softDelete(id, "team-b")

            result shouldBe false
            val unchanged = repository.findRawById(id)
            unchanged?.feedbackJson shouldContain "Team secret"
        }
    }
})
