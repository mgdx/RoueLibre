package io.github.mgdx.rouelibre.data.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the connection in use bills, and whether there is one (SPEC §4.4).
 *
 * Few members, so that everything reading them stays testable: only
 * Android can answer the question, but the rule that acts on the answer
 * ([io.github.mgdx.rouelibre.core.data.MeteredTransferGate]) never has to meet
 * Android to be exercised.
 *
 * **Billing rather than transport.** Android reasons about what a network
 * charges for, not about what carries it, and that is the truer notion here: a
 * phone's shared connection is Wi-Fi and is a mobile plan, while a capped hotel
 * Wi-Fi declares itself billed.
 */
interface ConnectionCost {

    /** Whether the connection in use bills what goes over it, right now. */
    fun isMetered(): Boolean

    /**
     * Follows that billing.
     *
     * A flow because a transfer outlives the moment it started in: a Wi-Fi
     * dropped for a mobile plan in the middle of a gigabyte has to reach the
     * screen while the file is still coming down.
     */
    val metered: Flow<Boolean>

    /**
     * Follows whether there is a connection at all.
     *
     * Read by the same watch as [metered], for the case next to it: a transfer
     * that failed for want of a connection must hear of its return while the
     * screen is open, to say so at once rather than keep saying it is offline.
     */
    val connected: Flow<Boolean>
}

/**
 * Asks the system, through `ACCESS_NETWORK_STATE` and no other permission
 * (SPEC §10).
 *
 * @param connectivity the system service, whose default network is the one
 *   everything the application sends goes over.
 */
class SystemConnectionCost(private val connectivity: ConnectivityManager) : ConnectionCost {

    override fun isMetered(): Boolean =
        connectivity.getNetworkCapabilities(connectivity.activeNetwork).bills()

    override val metered: Flow<Boolean> =
        defaultNetwork().map { it.bills() }.distinctUntilChanged().conflate()

    override val connected: Flow<Boolean> =
        defaultNetwork().map { it.reaches() }.distinctUntilChanged().conflate()

    /**
     * The capabilities of the default network, or `null` when there is none, as
     * they change.
     *
     * One watch for both questions, so that billing and presence are read from
     * the same callbacks and cannot drift apart.
     */
    private fun defaultNetwork(): Flow<NetworkCapabilities?> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(connectivity.getNetworkCapabilities(network))
            }

            override fun onLost(network: Network) {
                trySend(connectivity.getNetworkCapabilities(connectivity.activeNetwork))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(capabilities)
            }
        }
        // Before any change comes in: a screen opened on a mobile plan must not
        // wait for that plan to change to learn it is on one.
        trySend(connectivity.getNetworkCapabilities(connectivity.activeNetwork))
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
}

/**
 * Whether these capabilities describe a connection that bills.
 *
 * No capabilities at all means no connection at all, which is not a billing
 * matter: read as "does not bill", a transfer then starts and fails saying it
 * is offline, which is the true reason. Read the other way round it would be
 * refused for a plan nobody is on.
 */
private fun NetworkCapabilities?.bills(): Boolean =
    this != null && !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

/**
 * Whether these capabilities describe a connection that reaches the internet.
 *
 * Not whether Android has validated it: a device without Google's services may
 * check against another server, or not at all, and a connection that is never
 * validated would then never be announced as back. Offering a resumption that
 * fails again costs a press; never offering it is the defect this answers.
 */
private fun NetworkCapabilities?.reaches(): Boolean =
    this != null && hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
