package io.github.mgdx.rouelibre.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.BuildConfig
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.intent.PlaceRequest
import io.github.mgdx.rouelibre.data.NEVER_LAUNCHED
import io.github.mgdx.rouelibre.databinding.ActivityMainBinding
import io.github.mgdx.rouelibre.ui.journey.JourneyEndpoint
import io.github.mgdx.rouelibre.ui.journey.JourneyResultFragment
import io.github.mgdx.rouelibre.ui.journey.JourneySearchFragment
import io.github.mgdx.rouelibre.ui.map.MapFragment
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import io.github.mgdx.rouelibre.ui.welcome.WelcomeFragment
import io.github.mgdx.rouelibre.ui.welcome.WhatsNewFragment
import kotlinx.coroutines.launch

/**
 * The application's single activity (SPEC §3).
 *
 * It hosts the fragments, and receives the places other applications send it
 * (SPEC §7.8). All the logic lives in the fragments and the view models.
 */
class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null

    private val container
        get() = (application as RoueLibreApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The content runs under the system bars, which the theme colours like
        // the background: the screen reads as one piece.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val created = ActivityMainBinding.inflate(layoutInflater)
        binding = created
        setContentView(created.root)

        // On recreation — rotation, theme change — the fragments are restored
        // by the system; replacing them would erase their state, and replaying
        // the intent would reopen a screen the user has left.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, MapFragment())
                .commit()
            openFirstScreen()
            welcome(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        welcome(intent)
    }

    override fun onDestroy() {
        binding = null
        super.onDestroy()
    }

    /**
     * Shows the welcome or the what's-new screen, if either applies
     * (SPEC §7.9, §7.10).
     *
     * The last version code seen decides between the three cases: never
     * launched, updated since, or nothing new. The two screens are exclusive —
     * what's-new is **never** shown on a first installation, where the welcome
     * applies instead.
     *
     * The read is asynchronous: a blocking disk read would delay the first draw
     * for a setting that, most of the time, asks for nothing.
     */
    private fun openFirstScreen() {
        lifecycleScope.launch {
            val lastSeen = container.preferences.lastSeenVersionCode()
            when {
                lastSeen == NEVER_LAUNCHED -> replaceWith(WelcomeFragment())

                lastSeen < BuildConfig.VERSION_CODE &&
                    WhatsNewFragment.hasNotes(
                        this@MainActivity,
                        lastSeen,
                        BuildConfig.VERSION_CODE,
                    ) -> show(WhatsNewFragment.since(lastSeen))

                // Nothing to show, but the version seen is updated: a release
                // published without notes must not bring the previous
                // release's notes back on the next launch.
                lastSeen < BuildConfig.VERSION_CODE ->
                    container.preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            }
        }
    }

    private fun replaceWith(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .commit()
    }

    /**
     * Takes in a place received from another application (SPEC §7.8).
     *
     * Nothing is sent over the network on that occasion: an address in words is
     * resolved by the local index, as everywhere else.
     */
    private fun welcome(intent: Intent) {
        val request = intent.toPlaceRequest() ?: return
        lifecycleScope.launch {
            val destination = resolve(request) ?: return@launch
            openFor(destination)
        }
    }

    /**
     * Turns the request received into a named point.
     *
     * @return the point, or `null` if it could not be placed — in which case
     *   the user has already been told what was missing.
     */
    private suspend fun resolve(request: PlaceRequest): JourneyEndpoint? = when (request) {
        is PlaceRequest.Point -> JourneyEndpoint(
            label = request.label?.takeIf { it.isNotBlank() }
                // A point without a label gets the nearest street's: "Rue
                // Nationale" reads back, "50.63 / 3.06" does not.
                ?: container.addressIndex.nearestAddress(request.coordinates)?.streetName
                ?: getString(R.string.incoming_place_default_label),
            position = request.coordinates,
        )

        is PlaceRequest.Search -> searchAddress(request.text)
    }

    /**
     * Looks up in the index the address received in words.
     *
     * Without an index, that has to be said, with an offer to install one,
     * rather than failing (SPEC §7.8).
     */
    private suspend fun searchAddress(text: String): JourneyEndpoint? {
        if (!container.addressIndex.isInstalled()) {
            showMessage(getString(R.string.incoming_needs_index)) {
                show(StorageFragment())
            }
            return null
        }
        val origin = defaultOrigin() ?: return null
        val outcome = container.addressIndex.search(text, origin = origin, limit = 1)
        val found = (outcome as? Outcome.Success)?.value?.firstOrNull()
        if (found == null) {
            showMessage(getString(R.string.incoming_address_not_found, text)) {
                show(JourneySearchFragment.newInstance())
            }
            return null
        }
        return JourneyEndpoint(found.streetName, found.position)
    }

    /**
     * Opens the screen that suits the point received.
     *
     * Outside the covered area no route is attempted: the map shows the point
     * if it can, and the application says why it stops there (SPEC §4, §7.8).
     */
    private suspend fun openFor(destination: JourneyEndpoint) {
        val boundingBox = container.activeCity()?.boundingBox
        if (boundingBox != null && destination.position !in boundingBox) {
            show(MapFragment.showing(destination))
            showMessage(getString(R.string.incoming_outside_coverage))
            return
        }

        // The departure is the current position when it is already known.
        // Asking for it on this occasion would be the prompt SPEC §10 rules
        // out: the user did not open the application, it was opened for them.
        val here = container.deviceLocation.lastKnown()
        if (here == null) {
            show(JourneySearchFragment.newInstance(destination = destination))
            return
        }
        show(
            JourneyResultFragment.newInstance(
                origin = JourneyEndpoint(getString(R.string.journey_source_my_position), here),
                destination = destination,
            ),
        )
    }

    /**
     * The reference point for ranking addresses, for want of a position.
     *
     * The active city's centre, which is not a position of the user's but a
     * fixed point of the configuration. Without a city there is no address
     * index either: the caller never gets this far.
     */
    private suspend fun defaultOrigin(): Coordinates? = container.deviceLocation.lastKnown()
        ?: container.activeCity()?.map?.centre

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showMessage(message: String, action: (() -> Unit)? = null) {
        val views = binding ?: return
        val snackbar = Snackbar.make(views.root, message, Snackbar.LENGTH_LONG)
        if (action != null) {
            snackbar.setAction(R.string.incoming_show_me) { action() }
        }
        snackbar.show()
    }
}
