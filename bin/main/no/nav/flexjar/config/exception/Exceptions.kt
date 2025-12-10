package no.nav.flexjar.config.exception

import io.ktor.http.HttpStatusCode

/**
 * Sealed class for API exceptions following the esyfo-narmesteleder pattern.
 * Each subclass knows how to convert itself to an ApiError response.
 */
sealed class ApiErrorException(
    message: String,
    val type: ErrorType,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    abstract fun toApiError(path: String?): ApiError

    class ForbiddenException(
        val errorMessage: String = "Forbidden",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.AUTHORIZATION_ERROR
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String?) = ApiError(
            status = HttpStatusCode.Forbidden.value,
            type = type,
            message = errorMessage,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )
    }

    class UnauthorizedException(
        val errorMessage: String = "Unauthorized",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.AUTHENTICATION_ERROR
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String?) = ApiError(
            status = HttpStatusCode.Unauthorized.value,
            type = type,
            message = errorMessage,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )
    }

    class NotFoundException(
        val errorMessage: String = "Not found",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.NOT_FOUND
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String?) = ApiError(
            status = HttpStatusCode.NotFound.value,
            type = type,
            message = errorMessage,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )
    }

    class BadRequestException(
        val errorMessage: String = "Bad request",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.BAD_REQUEST
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String?) = ApiError(
            status = HttpStatusCode.BadRequest.value,
            type = type,
            message = errorMessage,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )
    }

    class InternalServerErrorException(
        val errorMessage: String = "Internal server error",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.INTERNAL_SERVER_ERROR
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String?) = ApiError(
            status = HttpStatusCode.InternalServerError.value,
            type = type,
            message = errorMessage,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )
    }

    class ConflictException(
        val errorMessage: String = "Conflict",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.CONFLICT
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String?) = ApiError(
            status = HttpStatusCode.Conflict.value,
            type = type,
            message = errorMessage,
            timestamp = java.time.Instant.now().toString(),
            path = path
        )
    }
}

// Legacy aliases for backwards compatibility
@Deprecated("Use ApiErrorException.ForbiddenException", ReplaceWith("ApiErrorException.ForbiddenException"))
typealias ForbiddenException = ApiErrorException.ForbiddenException

@Deprecated("Use ApiErrorException.UnauthorizedException", ReplaceWith("ApiErrorException.UnauthorizedException"))
typealias UnauthorizedException = ApiErrorException.UnauthorizedException

@Deprecated("Use ApiErrorException.NotFoundException", ReplaceWith("ApiErrorException.NotFoundException"))
typealias NotFoundException = ApiErrorException.NotFoundException

@Deprecated("Use ApiErrorException.BadRequestException", ReplaceWith("ApiErrorException.BadRequestException"))
typealias BadRequestException = ApiErrorException.BadRequestException
