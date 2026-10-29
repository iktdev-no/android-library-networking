package no.iktdev.networking.rest

import java.net.URL


/** Base type for failures produced by the networking client itself. */
open class NetworkingException(
    override val message: String,
    val request: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/** Thrown when a request URL or its connection cannot be constructed or configured. */
class HttpConnectionConstructionException(
    message: String,
    request: String? = null,
    cause: Throwable? = null
) : NetworkingException(message, request, cause)

/** Thrown when connecting to a server or reading a successful response fails. */
class HttpConnectionException(
    message: String,
    request: String? = null,
    cause: Throwable? = null
) : NetworkingException(message, request, cause)

/** Thrown when a request payload cannot be serialized. */
class RequestSerializationException(
    message: String,
    cause: Throwable? = null
) : NetworkingException(message, cause = cause)

/** Thrown when the serialized request payload cannot be written to the connection. */
class RequestWriteException(
    message: String,
    request: String?,
    cause: Throwable? = null
) : NetworkingException(message, request, cause)

/** Thrown when a successful HTTP response body cannot be decoded as the requested type. */
class ReadObjectException(
    val responseStatus: Int,
    val responseBody: String,
    request: String?,
    message: String,
    cause: Throwable? = null
) : NetworkingException(message, request, cause)