package no.iktdev.demoapplication

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import no.iktdev.networking.Security
import no.iktdev.networking.client.Http

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.time.LocalDateTime

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("no.iktdev.demoapplication", appContext.packageName)
    }

    fun getDemoString(): String {
        return """
            {
            	"deviceInfo": {
            	 	"deviceManufacturer":"samsung",
            		"deviceModel":"SM-N970F",
            		"deviceName":"Brage sin Note10",
            		"osPlatform":"Android",
            		"osVersion":"12"
             },
             "pin":"${generateRandomCode(8)}",
             "requesterId":"20c53b7634ad08e35da42fee37438b5524324be4917f82f05a5d9925334b2d75"
            }
        """.trimIndent()
    }

    @Test
    fun assertPostIsSuccessful() {

        val post = object: Post("http://streamit.lan") {
            override val apiPaths: ArrayList<String> = arrayListOf("api")

            fun stringRequest(paths: List<String> = listOf(), payload: String): Http.HttpStringResponse {
                return executeRequest(method = Method.POST, paths = paths, payload = payload, securityAuthMode = Security.HttpAuthenticate.Defaults)
            }

            fun createDelegateQrRequest(): Http.HttpStringResponse {

                return stringRequest(arrayListOf("auth","delegate","request", "qr"), getDemoString())
            }

            fun createDelegateQrRequestObject(): Http.HttpObjectResponse<DelegatedRequestData?> {
                return request<DelegatedRequestData>(arrayListOf("auth","delegate","request", "qr"), DelegatedEntryData(
                    deviceInfo = DelegatedDeviceInfo(
                        deviceManufacturer = "samsung",
                                deviceModel = "SM-N970F",
                                deviceName = "Brage sin Note10",
                                osPlatform = "Android",
                                osVersion = "12"
                    ),
                    pin = generateRandomCode(8),
                    requesterId = "20c53b7634ad08e35da42fee37438b5524324be4917f82f05a5d9925334b2d75"
                ))
            }
        }

        val response = post.createDelegateQrRequest()
        assertEquals(response.status, 200)

        val response2 = post.createDelegateQrRequestObject()
        assertEquals(response2.status, 200)

    }

    @Test
    fun assertGetIsSuccessful() {
        val get = object: Get("http://192.168.2.20:8080/open") {
            //override val apiPaths: ArrayList<String> = arrayListOf("api")

            fun pull(): Http.HttpObjectResponse<String?> {
                val requesterId = "20c53b7634ad08e35da42fee37438b5524324be4917f82f05a5d9925334b2d75"
                val pin = "FAWKPN66"
                return request<String>(paths = arrayListOf("auth", "delegate", requesterId, pin, "new"))
            }
        }

        val response = get.pull()
        assertEquals(response.status, 200)
    }

}

fun generateRandomCode(length: Int): String {
    val charset = ('A'..'Z') + ('0'..'9')
    return List(length) { charset.random() }.joinToString("")
}

data class DelegatedEntryData(
    val requesterId: String,
    val pin: String,
    val deviceInfo: DelegatedDeviceInfo
)

data class DelegatedDeviceInfo(
    val deviceName: String?,
    val deviceModel: String?,
    val deviceManufacturer: String?,
    val osVersion: String?,
    val osPlatform: String?
)

data class DelegatedRequestData(
    val requesterId: String,
    val pin: String,
    val deviceInfo: DelegatedDeviceInfo,
    val created: LocalDateTime,
    val expires: LocalDateTime,
    val permitted: Boolean,
    val consumed: Boolean,
    val method: AuthMethod,
    val ipaddress: String?
)

enum class AuthMethod {
    PIN, QR
}