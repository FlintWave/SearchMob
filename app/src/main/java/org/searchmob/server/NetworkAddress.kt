package org.searchmob.server

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Best-effort discovery of the device's reachable IPv4 address, used only to display the reachable URL
 * when the opt-in network mode is on.
 *
 * Enumerates [NetworkInterface]s, collects the up, non-loopback IPv4 addresses, and hands them to
 * [pickReachableIpv4]. A Tailscale address is preferred because that is the recommended way to use
 * network mode; failing that, a private site-local address is used. Deliberately uses [NetworkInterface]
 * rather than `WifiManager` so no `ACCESS_WIFI_STATE` permission is needed and so Ethernet and Tailscale
 * interfaces are covered too.
 *
 * Returns null when no such address exists (for example airplane mode or loopback-only), so callers can
 * show a "no network address available" state.
 */
fun lanIpv4Address(): String? =
    runCatching {
        val addresses =
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<Inet4Address>()
                ?.toList()
                .orEmpty()
        pickReachableIpv4(addresses)
    }.getOrNull()

/**
 * Pure selection from a list of candidate IPv4 addresses. Drops loopback and link-local (169.254/16),
 * keeps Tailscale CGNAT (100.64.0.0/10) and private site-local addresses (10/8, 172.16/12, 192.168/16),
 * and prefers the Tailscale address when present. Returns the chosen host string, or null if none fit.
 */
fun pickReachableIpv4(addresses: List<Inet4Address>): String? {
    val usable =
        addresses.filter {
            !it.isLoopbackAddress &&
                !it.isLinkLocalAddress &&
                (it.isSiteLocalAddress || isTailscaleCgnat(it))
        }
    val chosen = usable.firstOrNull { isTailscaleCgnat(it) } ?: usable.firstOrNull()
    return chosen?.hostAddress?.takeIf { it.isNotBlank() }
}

/** True for the 100.64.0.0/10 carrier-grade NAT range (RFC 6598), which Tailscale assigns from. */
private fun isTailscaleCgnat(ip: Inet4Address): Boolean {
    val octets = ip.address
    return (octets[0].toInt() and 0xFF) == 100 && (octets[1].toInt() and 0xFF) in 64..127
}

/**
 * The `http://<host>:<port>/` URL another machine can use to reach SearchMob, or null when no reachable
 * IPv4 address could be determined. Pure given [lanIp]; the default argument performs the discovery.
 */
fun networkReachableUrl(
    port: Int,
    lanIp: String? = lanIpv4Address(),
): String? = lanIp?.let { "http://$it:$port/" }
