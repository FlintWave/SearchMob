package org.searchmob.server

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Best-effort discovery of the device's reachable LAN IPv4 address, used only to display the
 * reachable URL when the opt-in network mode is on.
 *
 * Enumerates [NetworkInterface]s and returns the first up, non-loopback, site-local IPv4 address (the
 * private ranges 10/8, 172.16/12, 192.168/16, plus link-local 169.254/16 which [Inet4Address] also
 * reports as site/link-local). Deliberately uses [NetworkInterface] rather than `WifiManager` so no
 * `ACCESS_WIFI_STATE` permission is needed and so Ethernet/Tailscale interfaces are covered too.
 *
 * Returns null when no such address exists (for example airplane mode or loopback-only), so callers can
 * show a "no network address available" state.
 */
fun lanIpv4Address(): String? =
    runCatching {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.map { it.hostAddress }
            ?.firstOrNull { !it.isNullOrBlank() }
    }.getOrNull()

/**
 * The `http://<host>:<port>/` URL another machine can use to reach SearchMob, or null when no LAN IPv4
 * address could be determined. Pure given [lanIp]; the default argument performs the discovery.
 */
fun networkReachableUrl(
    port: Int,
    lanIp: String? = lanIpv4Address(),
): String? = lanIp?.let { "http://$it:$port/" }
