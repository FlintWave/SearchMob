package org.searchmob.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkModeBindingTest {
    @Test
    fun bindHost_loopbackWhenNetworkModeOff() {
        assertEquals("127.0.0.1", bindHost(networkAccessEnabled = false))
        assertEquals(LOOPBACK_HOST, bindHost(networkAccessEnabled = false))
    }

    @Test
    fun bindHost_allInterfacesWhenNetworkModeOn() {
        assertEquals("0.0.0.0", bindHost(networkAccessEnabled = true))
        assertEquals(ALL_INTERFACES_HOST, bindHost(networkAccessEnabled = true))
    }

    @Test
    fun networkReachableUrl_buildsHttpUrlFromLanIp() {
        assertEquals("http://192.168.1.20:8787/", networkReachableUrl(8787, lanIp = "192.168.1.20"))
    }

    @Test
    fun networkReachableUrl_nullWhenNoLanIp() {
        assertNull(networkReachableUrl(8787, lanIp = null))
    }
}
