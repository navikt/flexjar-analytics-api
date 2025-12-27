package no.nav.flexjar.config

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import no.nav.flexjar.config.auth.JwtUtils
import kotlin.time.Duration.Companion.minutes

/**
 * Rate limiting to prevent abuse.
 * 
 * Uses azp_name from JWT to group requests by calling application.
 * Falls back to IP address for unauthenticated requests.
 */

val SubmissionRateLimit = RateLimitName("submission")
val AnalyticsRateLimit = RateLimitName("analytics")

fun Application.configureRateLimiting() {
    install(RateLimit) {
        register(SubmissionRateLimit) {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
            requestKey { call -> call.rateLimitKey() }
        }
        
        register(AnalyticsRateLimit) {
            rateLimiter(limit = 300, refillPeriod = 1.minutes)
            requestKey { call -> call.rateLimitKey() }
        }
        
        global {
            rateLimiter(limit = 1000, refillPeriod = 1.minutes)
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.rateLimitKey(): String {
    return JwtUtils.extractAzpNameFromHeader(request.headers["Authorization"])
        ?: request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
        ?: request.local.remoteAddress
}

