package no.iktdev.networking.glide

import junit.framework.TestCase

class GlideUnsecuredHttpsTest : TestCase() {

    fun testCreatable() {
        val item = GlideUnsecuredHttps.getUnsafeOkHttpClient()
        assertNotNull(item)
    }
}