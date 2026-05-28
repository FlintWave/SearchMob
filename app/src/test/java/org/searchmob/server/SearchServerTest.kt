package org.searchmob.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL

class SearchServerTest {
    private fun waitForHealthz(
        port: Int,
        attempts: Int = 30,
    ): Int {
        var lastError: Exception? = null
        repeat(attempts) {
            try {
                val connection = (URL("http://$LOOPBACK_HOST:$port/healthz").openConnection() as HttpURLConnection)
                connection.connectTimeout = 500
                connection.readTimeout = 500
                val code = connection.responseCode
                connection.disconnect()
                return code
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(100)
            }
        }
        throw AssertionError("server did not respond on $port", lastError)
    }

    @Test
    fun bindsLoopbackAndServesHealthz() {
        val server = SearchServer()
        val bound = server.start(freeLoopbackPort())
        try {
            assertTrue(bound > 0)
            assertEquals(bound, server.boundPort)
            assertEquals(200, waitForHealthz(bound))
        } finally {
            server.stop()
        }
    }

    @Test
    fun fallsBackWhenPreferredPortBusy() {
        val preferred = freeLoopbackPort()
        ServerSocket().use { occupier ->
            occupier.bind(InetSocketAddress(LOOPBACK_HOST, preferred))
            val server = SearchServer()
            val bound = server.start(preferred)
            try {
                assertNotEquals(preferred, bound)
                assertTrue(bound > 0)
                assertEquals(200, waitForHealthz(bound))
            } finally {
                server.stop()
            }
        }
    }

    @Test
    fun bindsLoopbackHostByDefault() {
        val server = SearchServer()
        val bound = server.start(freeLoopbackPort())
        try {
            assertEquals(LOOPBACK_HOST, server.boundHost)
            assertEquals(200, waitForHealthz(bound))
        } finally {
            server.stop()
        }
    }

    @Test
    fun bindsAllInterfacesWhenNetworkModeOn() {
        val server = SearchServer()
        val bound = server.start(freeLoopbackPort(), networkAccessEnabled = true)
        try {
            assertEquals(ALL_INTERFACES_HOST, server.boundHost)
            // Still reachable on loopback (0.0.0.0 is a superset of 127.0.0.1).
            assertEquals(200, waitForHealthz(bound))
        } finally {
            server.stop()
        }
    }

    @Test
    fun restartRebindsToNetworkHost() {
        val server = SearchServer()
        val port = freeLoopbackPort()
        server.start(port)
        try {
            assertEquals(LOOPBACK_HOST, server.boundHost)
            server.restart(port, networkAccessEnabled = true)
            assertEquals(ALL_INTERFACES_HOST, server.boundHost)
            assertEquals(200, waitForHealthz(server.boundPort))
        } finally {
            server.stop()
        }
    }

    @Test
    fun stopReleasesPortWithinBoundedTime() {
        val server = SearchServer()
        val bound = server.start(freeLoopbackPort())
        assertEquals(200, waitForHealthz(bound))
        server.stop()

        var freed = false
        repeat(30) {
            if (isLoopbackPortFree(bound)) {
                freed = true
                return@repeat
            }
            Thread.sleep(100)
        }
        assertTrue("port $bound should be released after stop()", freed)
        assertEquals(-1, server.boundPort)
    }
}
