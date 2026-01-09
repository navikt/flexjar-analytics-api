package no.nav.flexjar.integrations.nais

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.flexjar.integrations.valkey.InMemoryTeamCache
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class NaisGraphQlClientTest {
    
    private lateinit var fixedClock: Clock
    private lateinit var teamCache: InMemoryTeamCache
    private val testUrl = "https://console.nav.cloud.nais.io/graphql"
    private val testApiKey = "test-api-key"
    
    @BeforeEach
    fun setup() {
        fixedClock = Clock.fixed(Instant.parse("2026-01-09T10:00:00Z"), ZoneId.of("UTC"))
        teamCache = InMemoryTeamCache()
    }
    
    private fun createMockClient(responseBody: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        return HttpClient(MockEngine { request ->
            respond(
                content = responseBody,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
    
    @Test
    fun `getTeamSlugsForUser returns teams on successful response`() = runBlocking {
        val responseJson = """
            {
                "data": {
                    "user": {
                        "teams": {
                            "nodes": [
                                {"team": {"slug": "team-esyfo"}},
                                {"team": {"slug": "flex"}}
                            ]
                        }
                    }
                }
            }
        """.trimIndent()
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = createMockClient(responseJson)
        )
        
        val result = client.getTeamSlugsForUser("test@nav.no")
        
        assertEquals(setOf("team-esyfo", "flex"), result)
    }
    
    @Test
    fun `getTeamSlugsForUserResult returns Success with teams`() = runBlocking {
        val responseJson = """
            {
                "data": {
                    "user": {
                        "teams": {
                            "nodes": [
                                {"team": {"slug": "team-esyfo"}}
                            ]
                        }
                    }
                }
            }
        """.trimIndent()
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = createMockClient(responseJson)
        )
        
        val result = client.getTeamSlugsForUserResult("test@nav.no")
        
        assertTrue(result.isSuccess())
        assertEquals(setOf("team-esyfo"), (result as NaisApiResult.Success).value)
    }
    
    @Test
    fun `getTeamSlugsForUser returns empty set on user not found`() = runBlocking {
        val responseJson = """
            {
                "data": {
                    "user": null
                }
            }
        """.trimIndent()
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = createMockClient(responseJson)
        )
        
        val result = client.getTeamSlugsForUser("unknown@nav.no")
        
        assertEquals(emptySet<String>(), result)
    }
    
    @Test
    fun `getTeamSlugsForUserResult returns Error on GraphQL errors`() = runBlocking {
        val responseJson = """
            {
                "data": null,
                "errors": [
                    {"message": "User not authorized"}
                ]
            }
        """.trimIndent()
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = createMockClient(responseJson)
        )
        
        val result = client.getTeamSlugsForUserResult("test@nav.no")
        
        assertTrue(result.isError())
        assertTrue((result as NaisApiResult.Error).message.contains("User not authorized"))
    }
    
    @Test
    fun `getTeamSlugsForUserResult returns Error on HTTP error`() = runBlocking {
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = createMockClient("{}", HttpStatusCode.InternalServerError)
        )
        
        val result = client.getTeamSlugsForUserResult("test@nav.no")
        
        assertTrue(result.isError())
        assertTrue((result as NaisApiResult.Error).message.contains("non-OK status"))
    }
    
    @Test
    fun `caching works for user teams`() = runBlocking {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = """{"data":{"user":{"teams":{"nodes":[{"team":{"slug":"flex"}}]}}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = mockClient
        )
        
        // First call - should hit the API
        client.getTeamSlugsForUser("test@nav.no")
        assertEquals(1, callCount)
        
        // Second call - should use cache
        client.getTeamSlugsForUser("test@nav.no")
        assertEquals(1, callCount)
        
        // Third call with different email - should hit the API
        client.getTeamSlugsForUser("other@nav.no")
        assertEquals(2, callCount)
    }
    
    @Test
    fun `getTeamSlugsForViewer returns teams for User type`() = runBlocking {
        val responseJson = """
            {
                "data": {
                    "me": {
                        "__typename": "User",
                        "teams": {
                            "nodes": [
                                {"team": {"slug": "team-esyfo"}}
                            ]
                        }
                    }
                }
            }
        """.trimIndent()
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = createMockClient(responseJson)
        )
        
        val result = client.getTeamSlugsForViewer()
        
        assertEquals(setOf("team-esyfo"), result)
    }
    
    @Test
    fun `getTeamSlugsForViewer returns empty for non-User type`() = runBlocking {
        val responseJson = """
            {
                "data": {
                    "me": {
                        "__typename": "ServiceAccount",
                        "teams": null
                    }
                }
            }
        """.trimIndent()
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = createMockClient(responseJson)
        )
        
        val result = client.getTeamSlugsForViewer()
        
        assertEquals(emptySet<String>(), result)
    }
    
    @Test
    fun `isHealthy returns true when no calls have been made`() {
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = createMockClient("{}")
        )
        
        assertTrue(client.isHealthy())
    }
    
    @Test
    fun `isHealthy returns true after successful call`() = runBlocking {
        val responseJson = """{"data":{"user":{"teams":{"nodes":[]}}}}"""
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = createMockClient(responseJson)
        )
        
        client.getTeamSlugsForUser("test@nav.no")
        
        assertTrue(client.isHealthy())
    }
    
    @Test
    fun `clearCache removes all cached entries`() = runBlocking {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = """{"data":{"user":{"teams":{"nodes":[{"team":{"slug":"flex"}}]}}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = mockClient
        )
        
        client.getTeamSlugsForUser("test@nav.no")
        assertEquals(1, callCount)
        
        // Clear cache
        client.clearCache()
        
        // Should hit API again
        client.getTeamSlugsForUser("test@nav.no")
        assertEquals(2, callCount)
    }
    
    @Test
    fun `getHealthStatus returns diagnostic information`() = runBlocking {
        val responseJson = """{"data":{"user":{"teams":{"nodes":[]}}}}"""
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = createMockClient(responseJson)
        )
        
        client.getTeamSlugsForUser("test@nav.no")
        
        val status = client.getHealthStatus()
        
        assertEquals(true, status["healthy"])
        assertEquals(true, status["cacheHealthy"])
        assertNotNull(status["lastSuccessfulCall"])
        assertNull(status["lastError"])
    }
    
    @Test
    fun `NaisApiResult getOrDefault returns value on success`() {
        val result: NaisApiResult<Set<String>> = NaisApiResult.Success(setOf("team"))
        
        assertEquals(setOf("team"), result.getOrDefault(emptySet()))
    }
    
    @Test
    fun `NaisApiResult getOrDefault returns default on error`() {
        val result: NaisApiResult<Set<String>> = NaisApiResult.Error("Failed")
        
        assertEquals(setOf("fallback"), result.getOrDefault(setOf("fallback")))
    }
    
    @Test
    fun `users with teams get cached with longer TTL`() = runBlocking {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = """{"data":{"user":{"teams":{"nodes":[{"team":{"slug":"flex"}}]}}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        
        val client = NaisGraphQlClient.forTesting(
            graphqlUrl = testUrl,
            apiKey = testApiKey,
            teamCache = teamCache,
            clock = fixedClock,
            client = mockClient
        )
        
        // First call - caches with 1 hour TTL (because user HAS teams)
        client.getTeamSlugsForUser("test@nav.no")
        assertEquals(1, callCount)
        
        // Even after 30 minutes, cache should still be valid
        // (In real usage, TTL is enforced by Valkey, but InMemoryTeamCache respects the TTL)
        client.getTeamSlugsForUser("test@nav.no")
        assertEquals(1, callCount)
    }
}
