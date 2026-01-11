package no.nav.flexjar

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import no.nav.flexjar.config.auth.BrukerPrincipal
import no.nav.flexjar.config.auth.TeamAuthorizationPlugin
import no.nav.flexjar.config.configureSerialization
import no.nav.flexjar.config.configureStatusPages
import no.nav.flexjar.config.configureRateLimiting
import no.nav.flexjar.config.DatabaseHolder
import no.nav.flexjar.repository.FeedbackRepository
import no.nav.flexjar.routes.feedbackRoutes
import no.nav.flexjar.routes.internalRoutes
import no.nav.flexjar.routes.statsRoutes
import no.nav.flexjar.routes.exportRoutes
import no.nav.flexjar.routes.surveyFacetRoutes
import no.nav.flexjar.routes.submissionRoutes
import no.nav.flexjar.repository.FeedbackStatsRepository
import java.sql.Timestamp
import no.nav.flexjar.service.FeedbackService
import no.nav.flexjar.service.StatsService
import no.nav.flexjar.service.ExportService
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Creates a test client configured with JSON serialization
 */
fun ApplicationTestBuilder.createTestClient() = createClient {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

/**
 * Insert test feedback into database
 */
fun insertTestFeedback(
    id: String = UUID.randomUUID().toString(),
    team: String = "team-test",
    app: String = "app-test",
    rating: Int = 4,
    text: String = "Test feedback",
    surveyId: String = "survey-$id",
    surveyType: String = "rating",
    tags: String? = null,
    opprettet: OffsetDateTime = OffsetDateTime.now()
) {
    TestDatabase.dataSource.connection.use { conn ->
        conn.prepareStatement("""
            INSERT INTO feedback (id, opprettet, feedback_json, team, app, tags) 
            VALUES (?, ?, ?::jsonb, ?, ?, ?)
        """).use { stmt ->
            stmt.setString(1, id)
            stmt.setObject(2, java.sql.Timestamp.from(opprettet.toInstant()))
            stmt.setString(3, """
                {
                    "schemaVersion": 1,
                    "surveyId": "$surveyId",
                    "surveyType": "$surveyType",
                    "context": {
                        "pathname": "/test/path",
                        "deviceType": "desktop"
                    },
                    "answers": [
                        {
                          "fieldId": "svar",
                          "fieldType": "RATING",
                          "question": {"label": "Hvordan opplevde du tjenesten?"},
                          "value": {"type": "rating", "rating": $rating, "ratingVariant": "emoji", "ratingScale": 5}
                        },
                        {
                          "fieldId": "feedback",
                          "fieldType": "TEXT",
                          "question": {"label": "Har du tilbakemelding?"},
                          "value": {"type": "text", "text": "$text"}
                        }
                    ],
                    "startedAt": "${opprettet.minusMinutes(1).toInstant()}",
                    "submittedAt": "${opprettet.toInstant()}"
                }
            """.trimIndent())
            stmt.setString(4, team)
            stmt.setString(5, app)
            stmt.setString(6, tags)
            stmt.executeUpdate()
        }
        conn.commit()
    }
}

fun insertTestFeedbackWithJson(
    id: String = UUID.randomUUID().toString(),
    team: String = "team-test",
    app: String = "app-test",
    feedbackJson: String,
    tags: String? = null,
    opprettet: OffsetDateTime = OffsetDateTime.now(),
) {
    TestDatabase.dataSource.connection.use { conn ->
        conn.prepareStatement(
            """
            INSERT INTO feedback (id, opprettet, feedback_json, team, app, tags)
            VALUES (?, ?, ?::jsonb, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, id)
            stmt.setObject(2, Timestamp.from(opprettet.toInstant()))
            stmt.setString(3, feedbackJson)
            stmt.setString(4, team)
            stmt.setString(5, app)
            stmt.setString(6, tags)
            stmt.executeUpdate()
        }
        conn.commit()
    }
}

fun insertTestTheme(
    team: String,
    name: String,
    keywords: List<String>,
    color: String? = null,
    priority: Int = 0,
    analysisContext: String = "GENERAL_FEEDBACK",
): String {
    val id = UUID.randomUUID().toString()

    TestDatabase.dataSource.connection.use { conn ->
        conn.prepareStatement(
            """
            INSERT INTO text_theme (id, team, name, keywords, color, priority, analysis_context)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, id)
            stmt.setString(2, team)
            stmt.setString(3, name)
            stmt.setArray(4, conn.createArrayOf("text", keywords.toTypedArray()))
            stmt.setString(5, color)
            stmt.setInt(6, priority)
            stmt.setString(7, analysisContext)
            stmt.executeUpdate()
        }
        conn.commit()
    }

    return id
}

/**
 * Test application module with authentication bypassed
 */
fun Application.testModule(
    feedbackService: FeedbackService = FeedbackService(),
    statsRepository: FeedbackStatsRepository = FeedbackStatsRepository()
) {
    // Initialize test database
    DatabaseHolder.initializeForTesting(TestDatabase.dataSource)
    TestDatabase.initialize()
    
    configureSerialization()
    configureStatusPages()
    configureRateLimiting()
    install(io.ktor.server.resources.Resources)
    
    // Test auth that creates a BrukerPrincipal from any "Bearer" token
    install(Authentication) {
        bearer("test-azure") {
            realm = "flexjar-analytics-test"
            authenticate { tokenCredential ->
                if (tokenCredential.token.isNotBlank()) {
                    BrukerPrincipal(
                        navIdent = "A123456",
                        name = "Test User",
                        email = "test.user@nav.no",
                        token = tokenCredential.token,
                        clientId = "dev-gcp:team-esyfo:flexjar-analytics",
                        // Include test groups that map to teams
                        groups = listOf(
                            "5066bb56-7f19-4b49-ae48-f1ba66abf546", // teamsykefravr
                            "ef4e9824-6f3a-4933-8f40-6edf5233d4d2", // team-esyfo
                            "00000000-0000-0000-0000-000000000001", // flex (test)
                            "00000000-0000-0000-0000-000000000002"  // team-test
                        )
                    )
                } else null
            }
        }
    }
    
    // Create services with the injected repositories/services
    val statsService = StatsService(FeedbackRepository(), statsRepository)
    val exportService = ExportService(FeedbackRepository())
    
    routing {
        internalRoutes()

        // Public submission API (local mode in tests)
        submissionRoutes(feedbackService)

        authenticate("test-azure") {
            // Install TeamAuthorizationPlugin like production
            install(TeamAuthorizationPlugin)
            
            feedbackRoutes(feedbackService)
            surveyFacetRoutes(feedbackService)
            statsRoutes(statsService)
            exportRoutes(exportService)
        }
    }
}
