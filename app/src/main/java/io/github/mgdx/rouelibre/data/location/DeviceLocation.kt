package io.github.mgdx.rouelibre.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import io.github.mgdx.rouelibre.core.geo.Coordinates
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.time.Duration
import kotlin.coroutines.resume

/**
 * Position de l'appareil, obtenue du système (SPEC §10).
 *
 * **Le fournisseur du système, jamais les services de localisation fusionnés
 * de Google** : ceux-ci font partie des Play Services, que la contrainte C2
 * interdit. `LocationManager` est l'API de l'AOSP, présente sur un LineageOS
 * sans GApps.
 *
 * La position n'est ni écrite sur le disque, ni envoyée, ni conservée d'une
 * session à l'autre : elle vit en mémoire le temps du calcul qui la demande
 * (SPEC §2, C3).
 *
 * Aucune fonction ici ne demande de permission : c'est à l'écran qui déclenche
 * l'usage de le faire, au moment où l'utilisateur le comprend.
 */
class DeviceLocation(private val context: Context) {

    private val locationManager: LocationManager?
        get() = ContextCompat.getSystemService(context, LocationManager::class.java)

    /** Vrai si l'une des deux permissions de localisation est accordée. */
    fun isPermitted(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Vrai si l'appareil peut fournir une position.
     *
     * Distinguer ce cas du refus de permission : « la localisation est
     * désactivée » et « l'application n'y a pas droit » n'appellent pas le
     * même geste de la part de l'utilisateur.
     */
    fun isAvailable(): Boolean = locationManager?.let { manager ->
        USABLE_PROVIDERS.any { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    } ?: false

    /**
     * La dernière position connue, si elle est encore fraîche.
     *
     * Immédiate et sans coût énergétique : c'est ce que le système a déjà.
     * Une position d'il y a une heure ne dit plus où l'on est, d'où la limite
     * de fraîcheur.
     *
     * @return la position, ou `null` si aucune n'est connue, assez récente, ou
     *   permise.
     */
    fun lastKnown(): Coordinates? {
        if (!isPermitted()) return null
        val manager = locationManager ?: return null
        val now = System.currentTimeMillis()
        return USABLE_PROVIDERS
            .mapNotNull { provider -> lastKnownFrom(manager, provider) }
            .filter { now - it.time <= MAXIMUM_AGE.toMillis() }
            .maxByOrNull { it.time }
            ?.toCoordinates()
    }

    /**
     * Une position fraîche, en attendant un relevé si nécessaire.
     *
     * @param timeout délai au-delà duquel on renonce plutôt que de faire
     *   attendre indéfiniment sous un immeuble ou dans un parking.
     * @return la position, ou `null` si elle n'a pas pu être obtenue.
     */
    suspend fun current(timeout: Duration = REQUEST_TIMEOUT): Coordinates? {
        lastKnown()?.let { return it }
        if (!isPermitted()) return null
        val manager = locationManager ?: return null
        val providers = USABLE_PROVIDERS.filter { candidate ->
            runCatching { manager.isProviderEnabled(candidate) }.getOrDefault(false)
        }
        if (providers.isEmpty()) return null

        return try {
            withTimeout(timeout.toMillis()) {
                awaitFirstFix(manager, providers)
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (_: SecurityException) {
            // La permission a pu être retirée entre la vérification et l'appel.
            null
        }
    }

    /**
     * Attend le premier relevé venu, et se désabonne quoi qu'il arrive.
     *
     * **Tous les fournisseurs disponibles sont interrogés en même temps**, et
     * le premier qui répond l'emporte. N'en interroger qu'un ne marche pas : le
     * GPS est le plus précis mais reste muet en intérieur, où le réseau répond
     * en une seconde. Éprouvé sur appareil — à l'intérieur, la version qui
     * n'interrogeait que le GPS attendait dix secondes pour ne rien rendre.
     *
     * Pour ce que l'application en fait — cadrer une carte, mesurer une
     * distance à une station — le premier relevé suffit largement.
     *
     * `getCurrentLocation` ferait cela en une ligne, mais n'existe qu'à partir
     * de l'API 30 ; l'application vise l'API 26.
     */
    private suspend fun awaitFirstFix(
        manager: LocationManager,
        providers: List<String>,
    ): Coordinates? = suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                if (continuation.isActive) continuation.resume(location.toCoordinates())
            }

            // Obligatoires avant l'API 30, où leurs implémentations par défaut
            // n'existent pas encore.
            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Retiré à l'API 29, mais requis par l'interface avant elle.")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: android.os.Bundle?,
            ) = Unit
        }

        try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            manager.removeUpdates(listener)
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation { manager.removeUpdates(listener) }
    }

    private fun lastKnownFrom(manager: LocationManager, provider: String): Location? = try {
        if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        // Un fournisseur absent de cet appareil.
        null
    }

    private fun Location.toCoordinates() = Coordinates(latitude, longitude)

    companion object {
        /** Les deux permissions, dans l'ordre où elles seront demandées. */
        val PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        /**
         * Fournisseurs interrogés, du plus précis au moins gourmand. Aucun
         * n'appartient aux services Google.
         */
        private val USABLE_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        /** Au-delà, une position ne dit plus où l'on se trouve. */
        private val MAXIMUM_AGE: Duration = Duration.ofMinutes(2)

        /** Délai au-delà duquel on renonce à obtenir un relevé. */
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
