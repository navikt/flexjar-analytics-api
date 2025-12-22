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
