package no.nav.flexjar.repository

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.flexjar.TestDatabase
import no.nav.flexjar.service.FeedbackService

class FeedbackSecurityTest : DescribeSpec({

    beforeSpec {
        TestDatabase.initialize()
    }
    
    beforeTest {
        TestDatabase.clearAllData()
    }

    describe("escapeLikePattern") {
        it("should escape percent sign") {
            val input = "100% complete"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe "100\\% complete"
        }
        
        it("should escape underscore") {
            val input = "test_value"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe "test\\_value"
        }
        
        it("should escape backslash") {
            val input = "path\\to\\file"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe "path\\\\to\\\\file"
        }
        
        it("should escape multiple special characters") {
            val input = "100% of _users have \\ in path"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe "100\\% of \\_users have \\\\ in path"
            // Check that raw % is escaped to \%
            escaped shouldContain "\\%"
        }
        
        it("should not modify text without special characters") {
            val input = "normal text without special chars"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe input
        }
        
        it("should handle empty string") {
            val input = ""
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe ""
        }
        
        it("should handle SQL injection attempt with LIKE wildcards") {
            val malicious = "' OR tags LIKE '%admin%' --"
            val escaped = malicious.escapeLikePattern()
            
            escaped shouldContain "\\%"
            escaped shouldNotContain "%admin%"
        }
        
        it("should escape consecutive special characters") {
            val input = "%%__\\\\"
            val escaped = input.escapeLikePattern()
            
            escaped shouldBe "\\%\\%\\_\\_\\\\\\\\"
        }
    }

    describe("redactFeedbackJson - integration") {
        val service = FeedbackService()
        val repository = FeedbackRepository()
        
        it("should redact fødselsnummer from answers") {
            val feedbackJson = """
                {
                    "surveyId": "test-survey",
                    "answers": [
                        {
                            "fieldId": "feedback",
                            "value": {
                                "type": "text",
                                "text": "Min fødselsnummer er 12345678901 og jeg har problemer"
                            }
                        }
                    ]
                }
            """.trimIndent()
            
            val saved = service.save(feedbackJson, "team-test", "test-app")
            val retrieved = repository.findRawById(saved, "team-test")
            
            retrieved?.feedbackJson shouldNotContain "12345678901"
        }
        
        it("should redact email from text answers") {
            val feedbackJson = """
                {
                    "surveyId": "test-survey",
                    "answers": [
                        {
                            "fieldId": "feedback",
                            "value": {
                                "type": "text",
                                "text": "Kontakt meg på test.user@example.com"
                            }
                        }
                    ]
                }
            """.trimIndent()
            
            val saved = service.save(feedbackJson, "team-test", "test-app")
            val retrieved = repository.findRawById(saved, "team-test")
            
            retrieved?.feedbackJson shouldNotContain "test.user@example.com"
            retrieved?.feedbackJson shouldContain "[E-POST FJERNET]"
        }
        
        it("should redact phone numbers") {
            val feedbackJson = """
                {
                    "surveyId": "test-survey",
                    "answers": [
                        {
                            "fieldId": "comment",
                            "value": {
                                "type": "text",
                                "text": "Ring meg på 98765432 for mer info"
                            }
                        }
                    ]
                }
            """.trimIndent()
            
            val saved = service.save(feedbackJson, "team-test", "test-app")
            val retrieved = repository.findRawById(saved, "team-test")
            
            retrieved?.feedbackJson shouldNotContain "98765432"
            retrieved?.feedbackJson shouldContain "[TELEFON FJERNET]"
        }
        
        it("should preserve non-text answers") {
            val feedbackJson = """
                {
                    "surveyId": "test-survey",
                    "answers": [
                        {
                            "fieldId": "rating",
                            "value": {
                                "type": "rating",
                                "rating": 5
                            }
                        }
                    ]
                }
            """.trimIndent()
            
            val saved = service.save(feedbackJson, "team-test", "test-app")
            val retrieved = repository.findRawById(saved, "team-test")
            
            retrieved?.feedbackJson shouldContain "\"rating\":5"
        }
        
        it("should handle JSON without answers gracefully") {
            val feedbackJson = """{"surveyId": "simple"}"""
            
            val saved = repository.save(feedbackJson, "team-test", "test-app")
            val retrieved = repository.findRawById(saved, "team-test")
            
            retrieved?.feedbackJson shouldContain "simple"
        }
    }

    describe("Team Isolation - Patch Verification") {
        val service = FeedbackService()
        val repository = FeedbackRepository()

        it("should isolate tags between teams") {
            // Seed data for two teams
            val idA = service.save("""{"surveyId":"A","answers":[{"fieldId":"f","value":{"type":"text","text":"..."}}]}""", "team-A", "app")
            service.addTag(idA, "team-A", "tag-A")
            
            val idB = service.save("""{"surveyId":"B","answers":[{"fieldId":"f","value":{"type":"text","text":"..."}}]}""", "team-B", "app")
            service.addTag(idB, "team-B", "tag-B")

            // Verify team-A only sees its tags
            repository.findAllTags("team-A") shouldBe setOf("tag-a")
            repository.findAllTags("team-B") shouldBe setOf("tag-b")
        }

        it("should isolate distinct apps between teams") {
            service.save("""{"surveyId":"A"}""", "team-A", "app-A")
            service.save("""{"surveyId":"B"}""", "team-B", "app-B")

            repository.findDistinctApps("team-A") shouldBe listOf("app-A")
            repository.findDistinctApps("team-B") shouldBe listOf("app-B")
        }

        it("should isolate metadata keys between teams") {
            val feedbackA = """{"surveyId":"S","context":{"tags":{"key-A":"val-A"}},"answers":[{"fieldId":"f","value":{"type":"text","text":"..."}}]}"""
            val feedbackB = """{"surveyId":"S","context":{"tags":{"key-B":"val-B"}},"answers":[{"fieldId":"f","value":{"type":"text","text":"..."}}]}"""
            
            service.save(feedbackA, "team-A", "app")
            service.save(feedbackB, "team-B", "app")

            val keysA = repository.findMetadataKeysForSurvey("S", "team-A")
            keysA.keys shouldBe setOf("key-A")
            keysA["key-A"] shouldBe setOf("val-A")

            val keysB = repository.findMetadataKeysForSurvey("S", "team-B")
            keysB.keys shouldBe setOf("key-B")
        }

        it("should deny access to feedback from another team") {
            val idA = service.save("""{"surveyId":"S","answers":[{"fieldId":"f","value":{"type":"text","text":"..."}}]}""", "team-A", "app")
            
            // Should be found by authorized team
            service.findById(idA, "team-A")?.id shouldBe idA
            
            // Should be null (isolated) for another team
            service.findById(idA, "team-B") shouldBe null
        }
    }
})
