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
import no.nav.flexjar.config.configureSerialization
import no.nav.flexjar.config.configureStatusPages
import no.nav.flexjar.config.setDataSourceForTesting
import no.nav.flexjar.repository.FeedbackRepository
import no.nav.flexjar.routes.feedbackRoutes
import no.nav.flexjar.routes.internalRoutes
import no.nav.flexjar.routes.statsRoutes
import no.nav.flexjar.routes.exportRoutes
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
    svar: Int = 4,
    feedback: String = "Test feedback",
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
                    "feedbackId": "$id",
                    "team": "$team",
                    "app": "$app",
                    "answers": [
                        {"questionId": "svar", "value": $svar, "questionPrompt": "Hvordan opplevde du tjenesten?"},
                        {"questionId": "feedback", "value": "$feedback", "questionPrompt": "Har du tilbakemelding?"}
                    ],
                    "startedAt": "${opprettet.minusMinutes(1)}",
                    "submittedAt": "$opprettet"
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

/**
 * Test application module with authentication bypassed
 */
fun Application.testModule(
    feedbackRepository: FeedbackRepository = FeedbackRepository()
) {
    // Initialize test database
    setDataSourceForTesting(TestDatabase.dataSource)
    TestDatabase.initialize()
    
    configureSerialization()
    configureStatusPages()
    
    // Test auth that creates a BrukerPrincipal from any "Bearer" token
    install(Authentication) {
        bearer("test-azure") {
            realm = "flexjar-analytics-test"
            authenticate { tokenCredential ->
                if (tokenCredential.token.isNotBlank()) {
                    BrukerPrincipal(
                        navIdent = "A123456",
                        name = "Test User",
                        token = tokenCredential.token,
                        clientId = "dev-gcp:flexjar:flexjar-analytics"
                    )
                } else null
            }
        }
    }
    
    routing {
        internalRoutes()
        authenticate("test-azure") {
            feedbackRoutes(feedbackRepository)
            statsRoutes(feedbackRepository)
            exportRoutes(feedbackRepository)
        }
    }
}
