package no.nav.flexjar.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.flexjar.TestDatabase
import no.nav.flexjar.config.DatabaseHolder
import no.nav.flexjar.domain.FieldType
import no.nav.flexjar.domain.FeedbackQuery
import no.nav.flexjar.domain.StatsQuery
import no.nav.flexjar.insertTestFeedback
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class FeedbackRepositoryTest : FunSpec({

    val repository = FeedbackRepository()

    beforeSpec {
        DatabaseHolder.initializeForTesting(TestDatabase.dataSource)
        TestDatabase.initialize()
    }

    beforeTest {
        TestDatabase.clearAllData()
    }

    context("findPaginated") {
        test("returns empty list when no feedback exists") {
            val (content, total, _) = repository.findPaginated(FeedbackQuery(team = "nonexistent"))
            
            content.shouldBeEmpty()
            total shouldBe 0
        }

        test("returns feedback filtered by team") {
            insertTestFeedback(team = "team-a", app = "app-1")
            insertTestFeedback(team = "team-b", app = "app-2")
            
            val (content, total, _) = repository.findPaginated(FeedbackQuery(team = "team-a"))
            
            content shouldHaveSize 1
            total shouldBe 1
        }

        test("returns feedback filtered by app") {
            insertTestFeedback(team = "flex", app = "spinnsyn")
            insertTestFeedback(team = "flex", app = "sykepengesoknad")
            
            val (content, _, _) = repository.findPaginated(FeedbackQuery(team = "flex", app = "spinnsyn"))
            
            content shouldHaveSize 1
            content.first().app shouldBe "spinnsyn"
        }

        test("paginates results correctly") {
            repeat(25) { i ->
                insertTestFeedback(
                    id = "feedback-$i",
                    team = "flex"
                )
            }
            
            val (page1, total, _) = repository.findPaginated(FeedbackQuery(team = "flex", page = 0, size = 10))
            val (page2, _, _) = repository.findPaginated(FeedbackQuery(team = "flex", page = 1, size = 10))
            val (page3, _, _) = repository.findPaginated(FeedbackQuery(team = "flex", page = 2, size = 10))
            
            total shouldBe 25
            page1 shouldHaveSize 10
            page2 shouldHaveSize 10
            page3 shouldHaveSize 5
        }

        test("filters by tags") {
            insertTestFeedback(id = "with-tag", team = "flex", tags = "important,bug")
            insertTestFeedback(id = "without-tag", team = "flex", tags = null)
            
            val (content, _, _) = repository.findPaginated(
                FeedbackQuery(team = "flex", tags = listOf("important"))
            )
            
            content shouldHaveSize 1
            content.first().id shouldBe "with-tag"
        }
    }

    context("findById") {
        test("returns feedback by id") {
            val id = UUID.randomUUID().toString()
            insertTestFeedback(id = id, team = "flex", text = "Test feedback text")
            
            val feedback = repository.findById(id)
            
            feedback.shouldNotBeNull()
            feedback.id shouldBe id
        }

        test("returns null for non-existent id") {
            val feedback = repository.findById("non-existent-id")
            
            feedback.shouldBeNull()
        }
    }

    context("findAllTags") {
        test("returns empty set when no tags exist") {
            insertTestFeedback(team = "flex", tags = null)
            
            val tags = repository.findAllTags("flex")
            
            tags.shouldBeEmpty()
        }

        test("returns unique tags from all feedback") {
            insertTestFeedback(id = "1", team = "flex", tags = "bug,feature")
            insertTestFeedback(id = "2", team = "flex", tags = "bug,improvement")
            insertTestFeedback(id = "3", team = "flex", tags = "feature")
            
            val tags = repository.findAllTags("flex")
            
            tags shouldHaveSize 3
            tags shouldContain "bug"
            tags shouldContain "feature"
            tags shouldContain "improvement"
        }
    }

    context("findDistinctApps") {
        test("returns apps for specific team") {
            insertTestFeedback(team = "flex", app = "spinnsyn")
            insertTestFeedback(team = "flex", app = "sykepengesoknad")
            insertTestFeedback(team = "arbeid", app = "pam-frontend")
            
            val flexApps = repository.findDistinctApps("flex")
            flexApps shouldHaveSize 2
            flexApps shouldContain "spinnsyn"
            flexApps shouldContain "sykepengesoknad"
            
            val arbeidApps = repository.findDistinctApps("arbeid")
            arbeidApps shouldHaveSize 1
            arbeidApps shouldContain "pam-frontend"
        }
    }

    context("tags operations") {
        test("addTag adds tag to existing feedback") {
            val id = UUID.randomUUID().toString()
            insertTestFeedback(id = id, tags = "existing-tag")
            
            val result = repository.addTag(id, "new-tag")
            
            result shouldBe true
            
            // Verify through findAllTags
            val allTags = repository.findAllTags("team-test")
            allTags shouldContain "new-tag"
            allTags shouldContain "existing-tag"
        }

        test("addTag returns false for non-existent feedback") {
            val result = repository.addTag("non-existent", "tag")
            
            result shouldBe false
        }

        test("removeTag removes tag from feedback") {
            val id = UUID.randomUUID().toString()
            insertTestFeedback(id = id, tags = "tag1,tag2")
            
            val result = repository.removeTag(id, "tag1")
            
            result shouldBe true
        }
    }

    context("updateJson") {
        test("updates feedback JSON") {
            val id = UUID.randomUUID().toString()
            insertTestFeedback(id = id, text = "Old text")
            
            val newJson = """{"surveyId": "test", "answers": []}"""
            val result = repository.updateJson(id, newJson)
            
            result shouldBe true
            val feedback = repository.findById(id)
            feedback.shouldNotBeNull()
        }
    }

        context("findContextTagsForSurvey") {
                fun insertTestFeedbackWithJson(
                        id: String = UUID.randomUUID().toString(),
                        team: String = "team-test",
                        app: String = "app-test",
                        feedbackJson: String,
                        opprettet: Timestamp = Timestamp.from(Instant.now()),
                ) {
                        TestDatabase.dataSource.connection.use { conn ->
                                conn.prepareStatement(
                                        """
                                        INSERT INTO feedback (id, opprettet, feedback_json, team, app, tags)
                                        VALUES (?, ?, ?::jsonb, ?, ?, ?)
                                        """.trimIndent()
                                ).use { stmt ->
                                        stmt.setString(1, id)
                                        stmt.setObject(2, opprettet)
                                        stmt.setString(3, feedbackJson)
                                        stmt.setString(4, team)
                                        stmt.setString(5, app)
                                        stmt.setString(6, null)
                                        stmt.executeUpdate()
                                }
                                conn.commit()
                        }
                }

                test("honors segments/date/device/hasText/lowRating") {
                        val surveyId = "survey-ctx-filters-repo-1"
                        val tsInRange = Timestamp.from(Instant.parse("2026-01-01T12:00:00Z"))
                        val tsOutOfRange = Timestamp.from(Instant.parse("2026-01-02T12:00:00Z"))

                        insertTestFeedbackWithJson(
                                team = "team-test",
                                app = "app-test",
                                opprettet = tsInRange,
                                feedbackJson = """
                                        {
                                            "surveyId": "$surveyId",
                                            "context": {
                                                "deviceType": "mobile",
                                                "tags": {"harAktivSykmelding": "Ja"}
                                            },
                                            "answers": [
                                                {"fieldId": "rating", "fieldType": "RATING", "question": {"label": "Hvordan?"}, "value": {"type": "rating", "rating": 2}},
                                                {"fieldId": "text", "fieldType": "TEXT", "question": {"label": "Hvorfor?"}, "value": {"type": "text", "text": "Bra nok"}}
                                            ]
                                        }
                                """.trimIndent(),
                        )

                        // Wrong segment
                        insertTestFeedbackWithJson(
                                team = "team-test",
                                app = "app-test",
                                opprettet = tsInRange,
                                feedbackJson = """
                                        {
                                            "surveyId": "$surveyId",
                                            "context": {
                                                "deviceType": "mobile",
                                                "tags": {"harAktivSykmelding": "Nei"}
                                            },
                                            "answers": [
                                                {"fieldId": "rating", "fieldType": "RATING", "question": {"label": "Hvordan?"}, "value": {"type": "rating", "rating": 2}},
                                                {"fieldId": "text", "fieldType": "TEXT", "question": {"label": "Hvorfor?"}, "value": {"type": "text", "text": "Ulik segment"}}
                                            ]
                                        }
                                """.trimIndent(),
                        )

                        // Out of date range
                        insertTestFeedbackWithJson(
                                team = "team-test",
                                app = "app-test",
                                opprettet = tsOutOfRange,
                                feedbackJson = """
                                        {
                                            "surveyId": "$surveyId",
                                            "context": {
                                                "deviceType": "mobile",
                                                "tags": {"harAktivSykmelding": "Ja"}
                                            },
                                            "answers": [
                                                {"fieldId": "rating", "fieldType": "RATING", "question": {"label": "Hvordan?"}, "value": {"type": "rating", "rating": 2}},
                                                {"fieldId": "text", "fieldType": "TEXT", "question": {"label": "Hvorfor?"}, "value": {"type": "text", "text": "Utenfor"}}
                                            ]
                                        }
                                """.trimIndent(),
                        )

                        // Wrong device and no text
                        insertTestFeedbackWithJson(
                                team = "team-test",
                                app = "app-test",
                                opprettet = tsInRange,
                                feedbackJson = """
                                        {
                                            "surveyId": "$surveyId",
                                            "context": {
                                                "deviceType": "desktop",
                                                "tags": {"harAktivSykmelding": "Ja"}
                                            },
                                            "answers": [
                                                {"fieldId": "rating", "fieldType": "RATING", "question": {"label": "Hvordan?"}, "value": {"type": "rating", "rating": 2}},
                                                {"fieldId": "text", "fieldType": "TEXT", "question": {"label": "Hvorfor?"}, "value": {"type": "text", "text": ""}}
                                            ]
                                        }
                                """.trimIndent(),
                        )

                        val result = repository.findContextTagsForSurvey(
                                surveyId = surveyId,
                                team = "team-test",
                                segments = listOf("harAktivSykmelding" to "Ja"),
                                fromDate = "2026-01-01",
                                toDate = "2026-01-01",
                                deviceType = "mobile",
                                hasText = true,
                                lowRating = true,
                        )

                        result.keys shouldContain "harAktivSykmelding"
                        val values = result["harAktivSykmelding"].shouldNotBeNull()
                        values shouldHaveSize 1
                        values.first().value shouldBe "Ja"
                        values.first().count shouldBe 1
                }
        }


})
