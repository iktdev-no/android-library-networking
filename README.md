# Android Networking

An Android/Kotlin networking library with a coroutine-based `DefaultClient`, URL and query-parameter helpers, and download utilities.

## Install

Add the release repository and the library dependency to your app module:

```groovy
repositories {
    maven { url 'https://reposilite.iktdev.no/releases' }
}

dependencies {
    implementation 'no.iktdev.android:networking:<version>'
}
```

The library supports Android API 21 and newer. Its Java/Kotlin bytecode target is Java 11.

## Make a request

`DefaultClient` methods are suspending functions, so call them from a coroutine:

```kotlin
val client = DefaultClient()
    .usingUrl("https://streamit.example/api")
    .withBearerToken(token)
    .withTimeout(10_000)

val response = client
    .withPaths("/v1/catalog/genre")
    .withQueryParams(
        "genreId" to 42L,
        "offset" to 0,
        "limit" to 100,
    )
    .get<List<Catalog>>()

when (response.status) {
    in 200..299 -> showCatalogs(response.result.orEmpty())
    401 -> requestSignIn()
    403 -> showAccessDenied()
    else -> showServerError(response.status, response.error)
}
```

Available methods are `get<T>()`, `post<T>(body)`, `put<T>(body)`, `patch<T>(body)`, and `delete<T>(body?)`. They return `DefaultClient.HttpResponse<T>`.

### Query parameters

Call `withPaths` before adding query parameters. Calling `withPaths` resets query parameters from the previous request.

- `withQueryParams("offset" to 0, "limit" to 100)` replaces the current query-parameter list.
- `withQueryParam("tag", "one")` appends a parameter. Call it more than once for repeated keys.
- Null values are omitted, and names and values are UTF-8 URL-encoded.

## Response and failure handling

Actual HTTP responses keep their HTTP status. Successful `2xx` responses have a decoded `result` (or `null` for an empty body). Non-`2xx` responses have `result == null`; `error` contains the raw response body when the server sent one. HTTP errors such as `401` and `403` are returned as responses rather than thrown by this library, so the calling API layer can choose how to handle them.

Failures in the client itself are thrown as typed exceptions. All of them inherit from `DefaultClient.NetworkingException`:

| Exception | Thrown when |
| --- | --- |
| `HttpConnectionConstructionException` | The URL cannot be created or the connection cannot be configured. |
| `HttpConnectionException` | Connecting to the server or reading a successful response fails. |
| `RequestSerializationException` | The request body cannot be serialized. |
| `RequestWriteException` | The serialized request body cannot be sent. |
| `ReadObjectException` | A successful response body cannot be decoded as the requested type. Includes `responseStatus`, `responseBody`, and `request`. |

Coroutine cancellation is rethrown as `CancellationException`; it is not converted to a networking failure.

```kotlin
try {
    val response = client.withPaths("/v1/catalog").get<List<Catalog>>()
    // Handle response.status and response.result here.
} catch (e: DefaultClient.ReadObjectException) {
    reportDecodeError(e.responseStatus, e.responseBody)
} catch (e: DefaultClient.NetworkingException) {
    reportClientFailure(e.request?.url, e)
}
```

An optional exception handler can be registered to observe client exceptions. The exception is still thrown after the handler runs:

```kotlin
val client = DefaultClient()
    .usingUrl("https://streamit.example/api")
    .setExceptionHandler { error -> logger.error("Networking client failed", error) }
```

## HTTPS trust

TLS certificate and hostname verification are enabled by default. `withoutTrust()` disables those checks and should only be used for development or a controlled self-signed server. Use `withTrust()` to restore the default verification behavior.

## IDE exception documentation

Public request methods document their possible exceptions with KDoc `@throws` entries and Kotlin `@Throws` annotations. In Android Studio or IntelliJ, use Quick Documentation on a method or exception type to view them.
