package no.nav.flexjar.config.exception

open class ApiException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ForbiddenException(
    message: String = "Forbidden"
) : ApiException(message)

class UnauthorizedException(
    message: String = "Unauthorized"
) : ApiException(message)

class NotFoundException(
    message: String = "Not found"
) : ApiException(message)

class BadRequestException(
    message: String = "Bad request"
) : ApiException(message)
