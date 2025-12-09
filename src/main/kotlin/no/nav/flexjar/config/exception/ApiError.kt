package no.nav.flexjar.config.exception

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

enum class ErrorType {
    AUTHENTICATION_ERROR,
    AUTHORIZATION_ERROR,
    NOT_FOUND,
    INTERNAL_SERVER_ERROR,
    ILLEGAL_ARGUMENT,
    BAD_REQUEST,
    CONFLICT
}

@Serializable
data class ApiError(
    val status: Int,
    val type: ErrorType,
    val message: String,
    val timestamp: String,
    val path: String? = null,
) {
    companion object {
        fun notFound(message: String, path: String? = null) = ApiError(
            status = HttpStatusCode.NotFound.value,
            type = ErrorType.NOT_FOUND,
            message = message,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )

        fun internalServerError(message: String, path: String? = null) = ApiError(
            status = HttpStatusCode.InternalServerError.value,
            type = ErrorType.INTERNAL_SERVER_ERROR,
            message = message,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )

        fun badRequest(message: String, path: String? = null) = ApiError(
            status = HttpStatusCode.BadRequest.value,
            type = ErrorType.BAD_REQUEST,
            message = message,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )

        fun unauthorized(message: String, path: String? = null) = ApiError(
            status = HttpStatusCode.Unauthorized.value,
            type = ErrorType.AUTHENTICATION_ERROR,
            message = message,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )

        fun forbidden(message: String, path: String? = null) = ApiError(
            status = HttpStatusCode.Forbidden.value,
            type = ErrorType.AUTHORIZATION_ERROR,
            message = message,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )
    }
}
