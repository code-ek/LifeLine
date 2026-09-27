package com.lifeline.app.net

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OkHttpProviderTest {

    @Test
    fun `clients are cached until reset`() {
        OkHttpProvider.reset()
        val http = OkHttpProvider.httpClient()
        val webSocket = OkHttpProvider.webSocketClient()
        assertSame(http, OkHttpProvider.httpClient())
        assertSame(webSocket, OkHttpProvider.webSocketClient())

        OkHttpProvider.reset()

        assertNotSame(http, OkHttpProvider.httpClient())
        assertNotSame(webSocket, OkHttpProvider.webSocketClient())
    }
}
