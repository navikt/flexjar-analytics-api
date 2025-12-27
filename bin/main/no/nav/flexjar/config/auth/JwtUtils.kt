package no.nav.flexjar.config.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.Base64

private val log = LoggerFactory.getLogger("JwtUtils")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Utility functions for JWT token parsing.
 * 
 * NOTE: These functions decode JWT claims WITHOUT verifying signatures.
 * Use only for non-security-critical purposes like logging, rate limiting,
 * or debugging. For authorization, always use validated principals from
 * the authentication plugins.
 */
object JwtUtils {
    
    /**
     * Extract a claim from a JWT token without verification.
     * Returns null if the token is malformed or the claim doesn't exist.
     */
    fun extractClaim(token: String, claim: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            val jsonElement = json.parseToJsonElement(payload)
            jsonElement.jsonObject[claim]?.jsonPrimitive?.content
        } catch (e: Exception) {
            log.debug("Failed to extract claim '$claim' from token", e)
            null
        }
    }
    
    /**
     * Extract azp_name (calling application identity) from a Bearer auth header.
     * Format: "cluster:namespace:app", e.g. "dev-gcp:team-esyfo:my-app"
     * 
     * @param authHeader The full Authorization header value (e.g., "Bearer eyJ...")
     * @return The azp_name claim value or null
     */
    fun extractAzpNameFromHeader(authHeader: String?): String? {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null
        }
        return extractClaim(authHeader.removePrefix("Bearer "), "azp_name")
    }
}
