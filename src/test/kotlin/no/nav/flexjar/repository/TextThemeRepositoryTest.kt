package no.nav.flexjar.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.flexjar.TestDatabase
import no.nav.flexjar.config.DatabaseHolder
import no.nav.flexjar.domain.AnalysisContext
import no.nav.flexjar.domain.CreateThemeRequest
import java.util.UUID

class TextThemeRepositoryTest : FunSpec({
    val repository = TextThemeRepository()

    beforeSpec {
        DatabaseHolder.initializeForTesting(TestDatabase.dataSource)
        TestDatabase.initialize()
    }

    beforeTest {
        // Clear text_theme table
        TestDatabase.dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE text_theme CASCADE")
            }
            conn.commit()
        }
    }

    context("CRUD operations") {
        test("create and findByTeam returns created theme") {
            repository.create("flex", CreateThemeRequest(
                name = "Sykepenger",
                keywords = listOf("syk", "sykemelding"),
                color = "#FF0000",
                priority = 10,
                analysisContext = AnalysisContext.GENERAL_FEEDBACK
            ))
            
            val themes = repository.findByTeam("flex")
            
            themes.size shouldBe 1
            themes[0].name shouldBe "Sykepenger"
            themes[0].keywords shouldBe listOf("syk", "sykemelding")
            themes[0].color shouldBe "#FF0000"
            themes[0].priority shouldBe 10
        }

        test("findByTeam returns empty for other team") {
            repository.create("flex", CreateThemeRequest(
                name = "Test",
                keywords = listOf("test"),
                analysisContext = AnalysisContext.GENERAL_FEEDBACK
            ))
            
            val themes = repository.findByTeam("other-team")
            
            themes.shouldBeEmpty()
        }

        test("findByTeam orders by priority descending") {
            repository.create("flex", CreateThemeRequest("Low", listOf("low"), priority = 1, analysisContext = AnalysisContext.GENERAL_FEEDBACK))
            repository.create("flex", CreateThemeRequest("High", listOf("high"), priority = 100, analysisContext = AnalysisContext.GENERAL_FEEDBACK))
            repository.create("flex", CreateThemeRequest("Medium", listOf("med"), priority = 50, analysisContext = AnalysisContext.GENERAL_FEEDBACK))
            
            val themes = repository.findByTeam("flex")
            
            themes[0].name shouldBe "High"
            themes[1].name shouldBe "Medium"
            themes[2].name shouldBe "Low"
        }

        test("update modifies theme") {
            val created = repository.create("flex", CreateThemeRequest(
                name = "Original",
                keywords = listOf("old"),
                analysisContext = AnalysisContext.GENERAL_FEEDBACK
            ))
            
            repository.update(UUID.fromString(created.id), no.nav.flexjar.domain.UpdateThemeRequest(
                name = "Updated",
                keywords = listOf("new", "keywords"),
                priority = 99,
                analysisContext = AnalysisContext.GENERAL_FEEDBACK
            ))
            
            val theme = repository.findById(UUID.fromString(created.id))
            theme?.name shouldBe "Updated"
            theme?.keywords shouldBe listOf("new", "keywords")
            theme?.priority shouldBe 99
        }

        test("delete removes theme") {
            val created = repository.create("flex", CreateThemeRequest(
                name = "ToDelete",
                keywords = listOf("delete"),
                analysisContext = AnalysisContext.GENERAL_FEEDBACK
            ))
            
            val deleted = repository.delete(UUID.fromString(created.id))
            
            deleted shouldBe true
            repository.findById(UUID.fromString(created.id)) shouldBe null
        }

        test("belongsToTeam returns true for matching team") {
            val created = repository.create("flex", CreateThemeRequest(
                name = "Owned",
                keywords = listOf("owned"),
                analysisContext = AnalysisContext.GENERAL_FEEDBACK
            ))
            
            repository.belongsToTeam(UUID.fromString(created.id), "flex") shouldBe true
            repository.belongsToTeam(UUID.fromString(created.id), "other") shouldBe false
        }
    }
})
