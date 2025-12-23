package no.nav.flexjar.config.auth

import io.ktor.server.auth.*

data class BrukerPrincipal(
    val navIdent: String?,
    val name: String?,
    val token: String,
    val clientId: String?,
    /** AD group UUIDs the user belongs to */
    val groups: List<String> = emptyList()
) : Principal

/**
 * Caller identity extracted from the token's azp_name claim.
 * Used for both analytics routes and submission routes.
 */
data class CallerIdentity(
    val team: String,
    val app: String,
    val navIdent: String?,
    val name: String?
)

