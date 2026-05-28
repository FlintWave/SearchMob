package org.searchmob.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class NetworkModeBindingTest {
    private fun v4(literal: String) = InetAddress.getByName(literal) as Inet4Address

    @Test
    fun pickReachableIpv4_prefersTailscaleCgnatOverSiteLocal() {
        // 100.64.0.0/10 (Tailscale) is preferred even when a site-local address comes first.
        assertEquals(
            "100.101.102.103",
            pickReachableIpv4(listOf(v4("192.168.1.20"), v4("100.101.102.103"))),
        )
    }

    @Test
    fun pickReachableIpv4_fallsBackToSiteLocal() {
        assertEquals("10.0.0.5", pickReachableIpv4(listOf(v4("10.0.0.5"))))
    }

    @Test
    fun pickReachableIpv4_dropsLoopbackLinkLocalAndPublic() {
        assertNull(
            pickReachableIpv4(listOf(v4("127.0.0.1"), v4("169.254.1.1"), v4("8.8.8.8"))),
        )
    }

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
