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
            insertTestFeedback(tags = null)
            
            val tags = repository.findAllTags()
            
            tags.shouldBeEmpty()
        }

        test("returns unique tags from all feedback") {
            insertTestFeedback(id = "1", tags = "bug,feature")
            insertTestFeedback(id = "2", tags = "bug,improvement")
            insertTestFeedback(id = "3", tags = "feature")
            
            val tags = repository.findAllTags()
            
            tags shouldHaveSize 3
            tags shouldContain "bug"
            tags shouldContain "feature"
            tags shouldContain "improvement"
        }
    }

    context("findAllTeamsAndApps") {
        test("returns teams with their apps") {
            insertTestFeedback(team = "flex", app = "spinnsyn")
            insertTestFeedback(team = "flex", app = "sykepengesoknad")
            insertTestFeedback(team = "arbeid", app = "pam-frontend")
            
            val teamsAndApps = repository.findAllTeamsAndApps()
            
            teamsAndApps.keys shouldHaveSize 2
            teamsAndApps["flex"]?.shouldContain("spinnsyn")
            teamsAndApps["flex"]?.shouldContain("sykepengesoknad")
            teamsAndApps["arbeid"]?.shouldContain("pam-frontend")
        }
    }

    context("tags operations") {
        test("addTag adds tag to existing feedback") {
            val id = UUID.randomUUID().toString()
            insertTestFeedback(id = id, tags = "existing-tag")
            
            val result = repository.addTag(id, "new-tag")
            
            result shouldBe true
            
            // Verify through findAllTags
            val allTags = repository.findAllTags()
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

    context("softDelete") {
        test("soft deletes feedback") {
            val id = UUID.randomUUID().toString()
            insertTestFeedback(id = id, text = "Sensitive feedback")
            
            val result = repository.softDelete(id)
            
            result shouldBe true
            val feedback = repository.findById(id)
            // After soft delete, the feedback should still exist but be marked
            feedback.shouldNotBeNull()
        }

        test("returns false for non-existent feedback") {
            val result = repository.softDelete("non-existent")
            
            result shouldBe false
        }
    }


})
