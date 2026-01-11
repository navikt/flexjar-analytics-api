package no.nav.flexjar.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import no.nav.flexjar.TestDatabase
import no.nav.flexjar.createTestClient
import no.nav.flexjar.domain.BlockerStatsResponse
import no.nav.flexjar.insertTestFeedbackWithJson
import no.nav.flexjar.insertTestTheme
import no.nav.flexjar.testModule
import java.time.OffsetDateTime

class BlockersRoutesTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    beforeSpec {
        TestDatabase.initialize()
    }

    beforeTest {
        TestDatabase.clearAllData()
    }

    test("GET /api/v1/intern/stats/blockers returns blocker stats and supports task filtering") {
        testApplication {
            application { testModule() }

            val team = "flex"
            val app = "spinnsyn"
            val surveyId = "survey-top"

            val themeInnloggingId = insertTestTheme(
                team = team,
                name = "Innlogging",
                keywords = listOf("bankid", "innlogging"),
                color = "var(--ax-status-warning)",
                analysisContext = "BLOCKER",
            )
            val themeTekniskId = insertTestTheme(
                team = team,
                name = "Teknisk",
                keywords = listOf("krasj"),
                color = "var(--ax-status-danger)",
                analysisContext = "BLOCKER",
            )

            fun topTasksJson(selectedTaskId: String, blocker: String): String {
                return """
                    {
                      "schemaVersion": 1,
                      "surveyId": "$surveyId",
                      "surveyType": "topTasks",
                      "context": {"deviceType": "desktop"},
                      "answers": [
                        {
                          "fieldId": "task",
                          "fieldType": "SINGLE_CHOICE",
                          "question": {
                            "label": "Hva skulle du gjøre?",
                            "options": [
                              {"id": "a", "label": "Søk"},
                              {"id": "b", "label": "Endre"}
                            ]
                          },
                          "value": {"type": "singleChoice", "selectedOptionId": "$selectedTaskId"}
                        },
                        {
                          "fieldId": "blocker",
                          "fieldType": "TEXT",
                          "question": {"label": "Hva hindret deg?"},
                          "value": {"type": "text", "text": "$blocker"}
                        }
                      ]
                    }
                """.trimIndent()
            }

            val t0 = OffsetDateTime.parse("2025-01-01T10:00:00+01:00")
            insertTestFeedbackWithJson(team = team, app = app, feedbackJson = topTasksJson("a", "Innlogging feiler bankid"), opprettet = t0)
            insertTestFeedbackWithJson(team = team, app = app, feedbackJson = topTasksJson("a", "Bankid virker ikke"), opprettet = t0.plusMinutes(1))
            insertTestFeedbackWithJson(team = team, app = app, feedbackJson = topTasksJson("b", "Skjema krasjer"), opprettet = t0.plusMinutes(2))
            insertTestFeedbackWithJson(team = team, app = app, feedbackJson = topTasksJson("b", ""), opprettet = t0.plusMinutes(3))

            val response = createTestClient().get("/api/v1/intern/stats/blockers?team=$team&app=$app&surveyId=$surveyId") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }

            response.status shouldBe HttpStatusCode.OK
            val stats = json.decodeFromString(BlockerStatsResponse.serializer(), response.bodyAsText())

            stats.totalBlockers shouldBe 3

            val innlogging = stats.themes.first { it.themeId == themeInnloggingId }
            innlogging.count shouldBe 2

            val teknisk = stats.themes.first { it.themeId == themeTekniskId }
            teknisk.count shouldBe 1

            // Most recent blocker is the last one we inserted with non-empty text
            stats.recentBlockers.first().blocker shouldBe "Skjema krasjer"
            stats.recentBlockers.first().task shouldBe "Endre"

            // Task filter
            val filteredResponse = createTestClient().get("/api/v1/intern/stats/blockers?team=$team&app=$app&surveyId=$surveyId&task=Søk") {
                header(HttpHeaders.Authorization, "Bearer test-token")
            }

            filteredResponse.status shouldBe HttpStatusCode.OK
            val filtered = json.decodeFromString(BlockerStatsResponse.serializer(), filteredResponse.bodyAsText())

            filtered.totalBlockers shouldBe 2
            filtered.recentBlockers.all { it.task == "Søk" } shouldBe true
        }
    }
})
