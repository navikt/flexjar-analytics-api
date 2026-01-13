package no.nav.flexjar.config.auth

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.flexjar.config.AZURE_REALM
import no.nav.flexjar.integrations.nais.NaisApiResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

private class FakeNaisTeamLookup(
    private val teamsByEmail: Set<String> = emptySet(),
    private val teamsByViewer: Set<String> = emptySet(),
) : NaisTeamLookup {
    override suspend fun getTeamSlugsForUserResult(email: String): NaisApiResult<Set<String>> =
        NaisApiResult.Success(teamsByEmail)

    override suspend fun getTeamSlugsForViewerResult(): NaisApiResult<Set<String>> =
        NaisApiResult.Success(teamsByViewer)
}

@Serializable
private data class TeamResponse(
    val team: String,
    val teams: List<String>,
)

class TeamAuthorizationPluginTest {

    @Test
    fun `defaults to a resolved team when NAIS lookup is used and team param is missing`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            install(Authentication) {
                bearer(AZURE_REALM) {
                    authenticate { _ ->
                        BrukerPrincipal(
                            navIdent = "Z123456",
                            name = "Test User",
                            email = "test@nav.no",
                            token = "token",
                            clientId = "client",
                            groups = emptyList(),
                        )
                    }
                }
            }

            routing {
                authenticate(AZURE_REALM) {
                    install(TeamAuthorizationPlugin) {
                        naisTeamLookupProvider = { FakeNaisTeamLookup(teamsByEmail = setOf("team-esyfo", "flex")) }
                    }

                    get("/team") {
                        call.respond(
                            TeamResponse(
                                team = call.authorizedTeam,
                                teams = call.authorizedTeams.sorted(),
                            )
                        )
                    }
                }
            }
        }

        val response = client.get("/team") {
            header(HttpHeaders.Authorization, "Bearer whatever")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("flex", json["team"]?.jsonPrimitive?.content)
        val teams = json["teams"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        assertEquals(listOf("flex", "team-esyfo"), teams)
    }

    @Test
    fun `rejects team param when not authorized`() = testApplication {
        val handlerInvoked = AtomicBoolean(false)

        application {
            install(ContentNegotiation) {
                json()
            }
            install(Authentication) {
                bearer(AZURE_REALM) {
                    authenticate { _ ->
                        BrukerPrincipal(
                            navIdent = "Z123456",
                            name = "Test User",
                            email = "test@nav.no",
                            token = "token",
                            clientId = "client",
                            groups = emptyList(),
                        )
                    }
                }
            }

            routing {
                authenticate(AZURE_REALM) {
                    install(TeamAuthorizationPlugin) {
                        naisTeamLookupProvider = { FakeNaisTeamLookup(teamsByEmail = setOf("team-esyfo")) }
                    }

                    get("/team") {
                        handlerInvoked.set(true)
                        call.respondText("ok")
                    }
                }
            }
        }

        val response = client.get("/team?team=flex") {
            header(HttpHeaders.Authorization, "Bearer whatever")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("TEAM_NOT_AUTHORIZED"))
        assertFalse(handlerInvoked.get())
    }

    @Test
    fun `stops pipeline when user has no team access`() = testApplication {
        val handlerInvoked = AtomicBoolean(false)

        application {
            install(ContentNegotiation) {
                json()
            }
            install(Authentication) {
                bearer(AZURE_REALM) {
                    authenticate { _ ->
                        BrukerPrincipal(
                            navIdent = "Z123456",
                            name = "Test User",
                            email = "test@nav.no",
                            token = "token",
                            clientId = "client",
                            groups = emptyList(),
                        )
                    }
                }
            }

            routing {
                authenticate(AZURE_REALM) {
                    install(TeamAuthorizationPlugin) {
                        naisTeamLookupProvider = { FakeNaisTeamLookup(teamsByEmail = emptySet(), teamsByViewer = emptySet()) }
                    }

                    get("/team") {
                        handlerInvoked.set(true)
                        call.respondText("ok")
                    }
                }
            }
        }

        val response = client.get("/team") {
            header(HttpHeaders.Authorization, "Bearer whatever")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("NO_TEAM_ACCESS"))
        assertFalse(handlerInvoked.get())
    }

    @Test
    fun `uses requested team when authorized`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            install(Authentication) {
                bearer(AZURE_REALM) {
                    authenticate { _ ->
                        BrukerPrincipal(
                            navIdent = "Z123456",
                            name = "Test User",
                            email = "test@nav.no",
                            token = "token",
                            clientId = "client",
                            groups = emptyList(),
                        )
                    }
                }
            }

            routing {
                authenticate(AZURE_REALM) {
                    install(TeamAuthorizationPlugin) {
                        naisTeamLookupProvider = { FakeNaisTeamLookup(teamsByEmail = setOf("team-esyfo", "flex")) }
                    }

                    get("/team") {
                        call.respondText(call.authorizedTeam)
                    }
                }
            }
        }

        val response = client.get("/team?team=team-esyfo") {
            header(HttpHeaders.Authorization, "Bearer whatever")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("team-esyfo", response.bodyAsText())
    }

    @Test
    fun `falls back to viewer teams when email lookup is empty`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            install(Authentication) {
                bearer(AZURE_REALM) {
                    authenticate { _ ->
                        BrukerPrincipal(
                            navIdent = "Z123456",
                            name = "Test User",
                            email = "test@nav.no",
                            token = "token",
                            clientId = "client",
                            groups = emptyList(),
                        )
                    }
                }
            }

            routing {
                authenticate(AZURE_REALM) {
                    install(TeamAuthorizationPlugin) {
                        naisTeamLookupProvider = {
                            FakeNaisTeamLookup(
                                teamsByEmail = emptySet(),
                                teamsByViewer = setOf("team-esyfo"),
                            )
                        }
                    }

                    get("/team") {
                        call.respondText(call.authorizedTeam)
                    }
                }
            }
        }

        val response = client.get("/team") {
            header(HttpHeaders.Authorization, "Bearer whatever")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("team-esyfo", response.bodyAsText())
    }

    @Test
    fun `denies access when NAIS lookup resolves no teams (no legacy fallback)`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            install(Authentication) {
                bearer(AZURE_REALM) {
                    authenticate { _ ->
                        BrukerPrincipal(
                            navIdent = "Z123456",
                            name = "Test User",
                            email = "test@nav.no",
                            token = "token",
                            clientId = "client",
                            // Groups should not grant access without NAIS team membership.
                            groups = listOf("ef4e9824-6f3a-4933-8f40-6edf5233d4d2"),
                        )
                    }
                }
            }

            routing {
                authenticate(AZURE_REALM) {
                    install(TeamAuthorizationPlugin) {
                        naisTeamLookupProvider = { FakeNaisTeamLookup(teamsByEmail = emptySet(), teamsByViewer = emptySet()) }
                    }

                    get("/team") {
                        call.respondText(call.authorizedTeam)
                    }
                }
            }
        }

        val response = client.get("/team") {
            header(HttpHeaders.Authorization, "Bearer whatever")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("NO_TEAM_ACCESS"))
    }
}
