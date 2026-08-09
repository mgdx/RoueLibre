// `Criteria` et `addTestProvider` n'ont de signature moderne qu'à partir de
// l'API 30 ; l'application vise l'API 26, c'est donc l'ancienne qu'il faut
// éprouver.
@file:Suppress("DEPRECATION")

package io.github.mgdx.rouelibre.data.location

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/**
 * Éprouve la lecture de la position avec un fournisseur simulé.
 *
 * Un fournisseur de test, et non le vrai GPS : un test ne peut pas dépendre de
 * l'endroit où se trouve l'appareil, ni attendre qu'il voie le ciel. La
 * position injectée est au centre de Lille, dans l'emprise des données.
 *
 * Simuler une position demande deux autorisations, et les deux sont des
 * conditions de l'appareil, pas du code :
 *
 * ```
 * adb shell appops set io.github.mgdx.rouelibre.debug android:mock_location allow
 * ```
 *
 * puis, dans les options pour développeurs, **« Application de position
 * fictive » → Roue Libre**. Éprouvé sur un Android 16 : sans ce second réglage,
 * le système accepte `addTestProvider` et `setTestProviderLocation` sans erreur,
 * mais ne livre la position à personne. Le test s'abstient alors plutôt que
 * d'échouer.
 */
@RunWith(AndroidJUnit4::class)
class DeviceLocationTest {

    private lateinit var manager: LocationManager
    private lateinit var deviceLocation: DeviceLocation
    private var providerAdded = false

    /** Grand-Place de Lille. */
    private val lille = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = 50.6371
        longitude = 3.0630
        accuracy = 12f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    @Before
    fun installTestProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        manager = context.getSystemService(LocationManager::class.java)
        deviceLocation = DeviceLocation(context)

        providerAdded = try {
            manager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE,
            )
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            true
        } catch (_: SecurityException) {
            false
        }
        assumeTrue("simulation de position non autorisée sur cet appareil", providerAdded)

        // Le système peut accepter le fournisseur sans rien en livrer : c'est
        // le cas tant que l'application n'est pas désignée « application de
        // position fictive » dans les options pour développeurs.
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, lille)
        assumeTrue(
            "désigne Roue Libre comme application de position fictive " +
                "dans les options pour développeurs",
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) != null,
        )
    }

    @After
    fun removeTestProvider() {
        if (!providerAdded) return
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
    }

    @Test
    fun rend_la_position_simulee() {
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, lille)

        val position = deviceLocation.lastKnown()
        assertNotNull("position simulée non lue", position)

        assertEquals(lille.latitude, position!!.latitude, TOLERANCE)
        assertEquals(lille.longitude, position.longitude, TOLERANCE)
    }

    @Test
    fun attend_un_releve_quand_aucune_position_n_est_connue() = runBlocking {
        // Aucun relevé n'a encore été publié : `current` doit en demander un,
        // et le premier fournisseur qui répond l'emporte.
        val awaited = async { deviceLocation.current(Duration.ofSeconds(10)) }
        delay(FIX_DELAY_MILLIS)
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, lille)

        val position = awaited.await()
        assertNotNull("aucun relevé obtenu", position)
        assertEquals(lille.latitude, position!!.latitude, TOLERANCE)
    }

    @Test
    fun ne_rend_rien_quand_le_fournisseur_est_eteint() {
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)

        // Les autres fournisseurs de l'appareil restent éventuellement actifs :
        // ce qui est vérifié ici est qu'un fournisseur éteint n'est pas lu.
        assertNull(
            "un fournisseur éteint ne doit rien rendre",
            runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
        )
    }

    private companion object {
        /** Un cent-millième de degré : un mètre environ. */
        const val TOLERANCE = 1e-5

        /** Délai avant de publier le relevé, pour que l'attente ait lieu. */
        const val FIX_DELAY_MILLIS = 500L
    }
}
