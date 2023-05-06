package no.iktdev.networking.client

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.lang.Exception
import java.lang.reflect.Type
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

abstract class HttpMethodImplementation(val url: URL) {
    abstract val http: HttpURLConnection

    protected fun open(): HttpURLConnection {
        return url.openConnection() as HttpURLConnection
    }

    fun setTimeout(timeOut: Int) {
        val finalTimeout = if (timeOut < 100) 100 else timeOut
        http.connectTimeout = finalTimeout
        http.readTimeout = finalTimeout
    }

    @Throws(SocketTimeoutException::class, FileNotFoundException::class, ConnectException::class)
    inline fun <reified T> Get(timeOut: Int): Http.HttpObjectResponse<T?> {
        http.requestMethod = "GET"
        setTimeout(timeOut)
        return getObjectFromHttp<T>(http)
    }

    @Throws(SocketTimeoutException::class, FileNotFoundException::class, ConnectException::class)
    fun Get(timeOut: Int): Http.HttpStringResponse {
        http.requestMethod = "GET"
        setTimeout(timeOut)
        return getStringFromHttp(http)
    }


    @Throws(SocketTimeoutException::class, FileNotFoundException::class, ConnectException::class)
    inline fun <reified T> Post(payload: String, timeOut: Int, contentType: String = "application/json"): Http.HttpObjectResponse<T?> {
        return PayloadRequestBuilder(http)
            .method(Methods.POST)
            .payload(payload)
            .timeOut(timeOut)
            .contentType(contentType)
            .execute()
            .toObjectResponse<T>()
    }

    @Throws(SocketTimeoutException::class, FileNotFoundException::class, ConnectException::class)
    fun Post(payload: String, timeOut: Int, contentType: String = "application/json"): Http.HttpStringResponse {
        return PayloadRequestBuilder(http)
            .method(Methods.POST)
            .payload(payload)
            .timeOut(timeOut)
            .contentType(contentType)
            .execute()
            .toStringResponse()
    }


    @Throws(SocketTimeoutException::class, FileNotFoundException::class, ConnectException::class)
    fun Delete(payload: String, timeOut: Int, contentType: String = "application/json"): Http.HttpStringResponse {
        return PayloadRequestBuilder(http)
            .method(Methods.DELETE)
            .payload(payload)
            .timeOut(timeOut)
            .contentType(contentType)
            .execute()
            .toStringResponse()
    }

    @Throws(SocketTimeoutException::class, FileNotFoundException::class, ConnectException::class)
    inline fun <reified T> Delete(payload: String, timeOut: Int, contentType: String = "application/json"): Http.HttpObjectResponse<T?> {
        return PayloadRequestBuilder(http)
            .method(Methods.DELETE)
            .payload(payload)
            .timeOut(timeOut)
            .contentType(contentType)
            .execute()
            .toObjectResponse<T>()
    }


    fun Size(): Long {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            http.contentLengthLong
        } else {
            http.contentLength.toLong()
        }
    }

    protected fun getStringFromHttp(http: HttpURLConnection): Http.HttpStringResponse
    {
        val reader = BufferedReader(InputStreamReader(http.inputStream))
        val builder: StringBuilder = StringBuilder()

        var line: String?
        while (reader.readLine().also { line = it } != null)
        {
            builder.appendLine(line)
        }

        http.inputStream.close()
        reader.close()
        return Http.HttpStringResponse(http.responseCode, builder.toString(), http.url.toString())
    }

    inline fun <reified T> getObjectFromHttp(http: HttpURLConnection): Http.HttpObjectResponse<T?>
    {
        val reader = BufferedReader(InputStreamReader(http.inputStream))
        val builder: StringBuilder = StringBuilder()

        var line: String?
        while (reader.readLine().also { line = it } != null)
        {
            builder.appendLine(line)
        }

        http.inputStream.close()
        reader.close()
        val content = builder.toString()
        return try {
            val type = object: TypeToken<T>() {}.type
            val deserialized = Gson().fromJson<T>(content, type)
            //val deserialized = fromJsonToObject<T>(content, type) //Gson().fromJson(builder.toString(), )
            Http.HttpObjectResponse(http.responseCode, deserialized, http.url.toString())
        } catch (e: Exception) {
            Log.e("Decode to ${T::class.java} failed", content)
            if (http.contentType == "application/json" || http.contentType.contains("json"))
                throw e
            else {
                Log.e("Avoided THROW", "As content type is not json, throwing will not propagate to system")
                Http.HttpObjectResponse(http.responseCode, null, http.url.toString())
            }
        }

    }

    data class PayloadRequestBuilder(
        val http: HttpURLConnection
    )
    {
        private var method: String = "POST"
        private var contentType: String = "application/json"
        private var timeOut: Int = 5000
        private var payload: String? = null
        private val headers: MutableMap<String, MutableList<String>> = mutableMapOf()

        fun method(method: Methods) = apply { this.method = if (method == Methods.GET) throw Exception("GET cannot be used with payload") else method.name }
        fun contentType(type: String) = apply { this.contentType = type }
        fun timeOut(timeOut: Int) = apply { this.timeOut = timeOut }
        fun payload(payload: String) = apply { this.payload = payload }
        fun headers(headerName: String, headerValues: List<String>) = apply {
            val _values = headers[headerName] ?: mutableListOf()
            _values.addAll(headerValues)
            headers[headerName] = _values
        }

        fun execute(): PayloadRequest {

            if (payload.isNullOrEmpty()) {
                throw RuntimeException("Payload for PayloadRequestBuilder has not been provided or is null!")
            }
            headers.forEach {
                if (http.headerFields.containsKey(it.key)) {
                    if (http.headerFields[it.key] == null)
                        http.headerFields[it.key] = it.value
                    else
                        http.headerFields[it.key]?.addAll(it.value)
                } else {
                    http.headerFields[it.key] = it.value
                }
            }


            http.requestMethod = method
            http.doOutput = true

            val finalTimeout = if (timeOut < 100) 100 else timeOut
            http.connectTimeout = finalTimeout
            http.readTimeout = finalTimeout

            http.setRequestProperty("Content-Type", contentType)

            val dos = DataOutputStream(http.outputStream)
            dos.writeBytes(payload)
            dos.flush()

            return PayloadRequest(http)
        }

        inner class PayloadRequest(val http: HttpURLConnection) {
            fun getResponse(): String {
                val reader = BufferedReader(InputStreamReader(http.inputStream))
                val builder: StringBuilder = StringBuilder()

                var line: String?
                while (reader.readLine().also { line = it } != null)
                {
                    builder.appendLine(line)
                }

                http.inputStream.close()
                reader.close()
                return builder.toString()
            }

            fun toStringResponse(): Http.HttpStringResponse
            {
                return Http.HttpStringResponse(http.responseCode, getResponse(), http.url.toString())
            }

            inline fun <reified T> toObjectResponse(): Http.HttpObjectResponse<T?>
            {
                try {
                    val type = object: TypeToken<T>() {}.type
                    val deserialized = Gson().fromJson<T>(getResponse(), type)
                    //val deserialized = Gson().fromJson(getResponse(), T::class.java)
                    return Http.HttpObjectResponse(http.responseCode, deserialized, http.url.toString())
                } catch (e: Exception) {
                    Log.e("Decode to ${T::class.java} failed", getResponse())
                    throw e
                }

            }
        }
    }

    enum class Methods {
        POST,
        DELETE,
        PUT,
        GET
    }




}