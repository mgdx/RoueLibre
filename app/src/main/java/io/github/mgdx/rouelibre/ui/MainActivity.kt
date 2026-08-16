package io.github.mgdx.rouelibre.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.BuildConfig
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.address.WordMatching
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.intent.PlaceRequest
import io.github.mgdx.rouelibre.data.NEVER_LAUNCHED
import io.github.mgdx.rouelibre.databinding.ActivityMainBinding
import io.github.mgdx.rouelibre.ui.address.toTitle
import io.github.mgdx.rouelibre.ui.journey.JourneyEndpoint
import io.github.mgdx.rouelibre.ui.journey.JourneyResultFragment
import io.github.mgdx.rouelibre.ui.journey.JourneySearchFragment
import io.github.mgdx.rouelibre.ui.map.MapFragment
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import io.github.mgdx.rouelibre.ui.welcome.WelcomeFragment
import io.github.mgdx.rouelibre.ui.welcome.WhatsNewFragment
import kotlinx.coroutines.launch

/**
 * How long the opening takes to clear once its work is done. Short enough to
 * be read as the screen handing over rather than as a wait of its own.
 */
private const val INTRO_FADE_MILLIS = 180L

/**
 * The least time the opening stays once it is on the screen (SPEC §7.0).
 *
 * Not a pause for effect: below this the screen is not read, it is glimpsed.
 * Measured on a Fairphone 5, where the whole opening lasted two tenths of a
 * second and the name never once showed on a plain green.
 *
 * It is counted from the moment the opening reaches the screen, so what it
 * adds is only what the start-up had not already spent past that moment: two
 * tenths of a second on a Fairphone 3, half a second on the faster Fairphone
 * 5. It is a floor, not a delay added to the wait.
 */
private const val INTRO_MINIMUM_MILLIS = 600L

/**
 * How the system bars look, so that the opening can borrow them for the length
 * of its green and give them back unchanged (SPEC §7.0).
 */
private data class SystemBars(val statusBar: Int, val navigationBar: Int, val lightIcons: Boolean)

/**
 * The application's single activity (SPEC §3).
 *
 * It hosts the fragments, and receives the places other applications send it
 * (SPEC §7.8). All the logic lives in the fragments and the view models.
 */
class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null

    /**
     * The three conditions the opening's departure waits on (SPEC §7.0): that
     * the first screen is settled, that it has something drawn in it, and that
     * it has been looked at.
     *
     * None is a formality. The read that settles the first screen finishes in
     * a tenth of a second, whereas the map behind takes a good second to
     * appear on a cold start: on that signal alone the opening was taken down
     * while there was still nothing underneath it. And on a fast phone all of
     * it is over in two tenths of a second — measured on a Fairphone 5, the
     * name appeared only in the three frames of the fade-out, over a map
     * already showing through.
     */
    private var contentDrawn = false
    private var firstScreenSettled = false
    private var heldLongEnough = false

    /** The system bars as the interface's theme wants them — see [openIntro]. */
    private var barsBeforeIntro: SystemBars? = null

    private val container
        get() = (application as RoueLibreApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest declares the intro theme so that the window Android
        // draws before this activity exists already carries the identity's
        // green (SPEC §7.0). That theme has done its work here: the interface
        // runs on its own, and asking for it before the first inflation is
        // what keeps the intro's green bars from becoming the application's.
        setTheme(R.style.Theme_RoueLibre)
        super.onCreate(savedInstanceState)
        handOverSplashWithoutFading()
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
            openIntro()
            // Everything below is deferred by one frame, and that is the whole
            // reason the opening is ever seen. Committed here and now, the map
            // makes the activity's first frame cost some two and a half
            // seconds on a Fairphone 3, and Android holds its own splash
            // screen over the entire wait — which shows the mark but cannot
            // show a word of text. Posted, the first frame is the opening
            // alone and costs nothing: the splash hands over almost at once,
            // and it is the opening that covers the map's start-up, name and
            // all.
            // Hung on the first draw pass rather than posted straight away: a
            // plain post runs before the first traversal, which would put the
            // map back into the frame it is being kept out of.
            //
            // And run through `withStarted`, because a deferred fragment
            // transaction is one that can land after the activity has saved
            // its state — the screen going dark during a cold start was enough
            // to crash it — whereas this one waits for the activity to be back
            // in a state that can take it.
            created.root.doOnPreDraw {
                lifecycleScope.launch {
                    withStarted {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.content, MapFragment())
                            .commit()
                        whenContentIsDrawn()
                        openFirstScreen()
                        welcome(intent)
                    }
                }
            }
        }
    }

    /**
     * Notes that the screen under the opening now has something on it.
     *
     * The opening may not leave before that: it would hand over to a container
     * with no view in it yet, and the user would be shown a blank in the place
     * of the map they are waiting for.
     */
    private fun whenContentIsDrawn() {
        val views = binding ?: return
        views.content.doOnPreDraw {
            it.post {
                contentDrawn = true
                closeIntroIfDue()
            }
        }
    }

    /**
     * Cuts from Android's splash screen to ours instead of dissolving into it.
     *
     * Left to itself, Android 12 and later fade their splash out over the
     * arriving application. Measured on the device, the mark went almost fully
     * transparent for some three tenths of a second and then came back — a
     * blink in the middle of the very screen whose job is to hold still
     * (SPEC §7.0). It fades what it drew, and what is underneath is the same
     * drawing at the same size in the same place, so there is nothing to
     * dissolve into: removing the splash outright makes the handover a cut
     * nobody can see.
     *
     * Before Android 12 there is no such splash and nothing to take over.
     */
    private fun handOverSplashWithoutFading() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        splashScreen.setOnExitAnimationListener { splash ->
            splash.remove()
            holdIntro()
        }
    }

    /**
     * Starts counting the opening's minimum stay (SPEC §7.0).
     *
     * Counted from here and not from `onCreate`, because here is where the
     * opening actually reaches the screen: until Android's splash hands over,
     * whatever we drew was drawn underneath it. Counted from `onCreate`, the
     * six hundred milliseconds would already have run out on a phone whose
     * splash lasts longer than that, which is every phone this is meant to
     * help.
     *
     * Before Android 12 there is no splash, and the first draw is that moment.
     */
    private fun holdIntro() {
        val intro = binding?.intro?.root ?: return
        intro.postDelayed(
            {
                heldLongEnough = true
                closeIntroIfDue()
            },
            INTRO_MINIMUM_MILLIS,
        )
    }

    /**
     * Puts up the opening screen (SPEC §7.0).
     *
     * Called before the first fragment is committed, so that what the user has
     * been looking at since the launcher — the mark on its green — simply goes
     * on, now with the application's name under it.
     *
     * The system bars come along: the theme colours them like the background,
     * and left alone they would draw two pale strips across the green.
     */
    private fun openIntro() {
        val views = binding ?: return
        views.intro.root.isVisible = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            views.intro.root.doOnPreDraw { holdIntro() }
        }
        val controller = WindowInsetsControllerCompat(window, views.root)
        // Noted as the theme has just left them. What the opening gives back
        // has to be exactly what it borrowed, and taking a copy is surer than
        // reading the theme again later — and than naming the colours a second
        // time here, where the light and dark variants would drift apart.
        barsBeforeIntro = SystemBars(
            statusBar = window.statusBarColor,
            navigationBar = window.navigationBarColor,
            lightIcons = controller.isAppearanceLightStatusBars,
        )
        val ground = ContextCompat.getColor(this, R.color.identity_ground)
        window.statusBarColor = ground
        window.navigationBarColor = ground
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    /**
     * Takes the opening away once all three of its conditions are met.
     *
     * The wait is the longest of the three, not their sum: the minimum stay
     * runs while the map is starting up, so on a slow phone it has long
     * expired by the time there is anything to hand over to, and costs nothing.
     */
    private fun closeIntroIfDue() {
        if (contentDrawn && firstScreenSettled && heldLongEnough) closeIntro()
    }

    /**
     * Takes the opening screen away.
     *
     * The fade is short enough not to be a wait of its own, and is dropped
     * altogether when the device asks for less movement (SPEC §7).
     */
    private fun closeIntro() {
        val views = binding ?: return
        val intro = views.intro.root
        if (!intro.isVisible) return
        restoreSystemBars()
        if (prefersReducedMotion()) {
            intro.isVisible = false
            return
        }
        intro.animate()
            .alpha(0f)
            .setDuration(INTRO_FADE_MILLIS)
            .withEndAction {
                intro.isVisible = false
                // The view is kept, so it must be left as it was found.
                intro.alpha = 1f
            }
    }

    /**
     * Hands the system bars back as the opening found them.
     *
     * Both bars carry the same colour, so the same icon polarity suits them.
     */
    private fun restoreSystemBars() {
        val views = binding ?: return
        val bars = barsBeforeIntro ?: return
        window.statusBarColor = bars.statusBar
        window.navigationBarColor = bars.navigationBar
        WindowInsetsControllerCompat(window, views.root).run {
            isAppearanceLightStatusBars = bars.lightIcons
            isAppearanceLightNavigationBars = bars.lightIcons
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
            val opened = showFirstScreen()
            // That read was the whole of what the opening was hiding, so the
            // opening goes now — before any dialog, which must not be argued
            // with through a green sheet.
            firstScreenSettled = true
            closeIntroIfDue()
            if (!opened) proposeCityHere()
        }
    }

    /**
     * Puts up the welcome or the what's-new screen, whichever applies.
     *
     * @return true if one of the two was put up, in which case the user has a
     *   screen to read and nothing else may be laid over it.
     */
    private suspend fun showFirstScreen(): Boolean {
        val lastSeen = container.preferences.lastSeenVersionCode()
        when {
            lastSeen == NEVER_LAUNCHED -> {
                replaceWith(WelcomeFragment())
                return true
            }

            lastSeen < BuildConfig.VERSION_CODE &&
                WhatsNewFragment.hasNotes(
                    this@MainActivity,
                    lastSeen,
                    BuildConfig.VERSION_CODE,
                ) -> {
                show(WhatsNewFragment.since(lastSeen))
                return true
            }

            // Nothing to show, but the version seen is updated: a release
            // published without notes must not bring the previous release's
            // notes back on the next launch.
            lastSeen < BuildConfig.VERSION_CODE ->
                container.preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
        }
        return false
    }

    /**
     * Offers the network of the conurbation one happens to be in (SPEC §15.1).
     *
     * Someone who travels arrives in a city the catalogue serves, with the data
     * of the one they left installed: the map is then blank and nothing says
     * that the city they are standing in is one press away.
     *
     * What is read here is **what the system already holds**: no permission is
     * requested, no fix is asked for, and nothing happens at all if location is
     * denied or off (SPEC §10). The position serves that single question, is
     * compared against the catalogue shipped in the APK — no request goes out —
     * and is written nowhere (SPEC §2, C3).
     *
     * The proposal is an offer, never an action: nothing is downloaded and no
     * city changes until the user says so. Declined, it is not put again — not
     * for the rest of the session, and not at the next launch either, until the
     * user is somewhere that network does not serve (see
     * `AppContainer.rememberCityProposal`).
     */
    private suspend fun proposeCityHere() {
        // The application was opened for a place, not by its user: they came
        // for that journey, and a dialog about another city would be in the
        // way.
        if (intent.toPlaceRequest() != null) return
        // Before the first city is chosen, it is the welcome screen's job to
        // propose one: two proposals in a row would be one too many.
        val servedCityId = container.preferences.activeCityId() ?: return
        val position = container.deviceLocation.lastKnown() ?: return

        val catalogue = container.cityCatalogueSource.catalogue()
        // A city whose data is not published yet is not worth proposing:
        // accepting would lead to a download that has nothing to fetch. Nor is
        // the city already in service, which is no change of map at all.
        val here = catalogue.suggestionFor(position)
            ?.takeIf { it.isAvailable && it.id != servedCityId }
        // Asked even when there is nothing to propose: standing outside the
        // network one declined is what forgets that refusal.
        if (!container.rememberCityProposal(here?.id)) return
        val city = checkNotNull(here)

        val installed = container.datasetStore.occupiedBytesOf(city.id) > 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.city_here_title)
            .setMessage(
                getString(
                    if (installed) R.string.city_here_installed_body else R.string.city_here_body,
                    cityLabel(city.displayName, city.mainCity),
                ),
            )
            .setPositiveButton(
                if (installed) R.string.city_here_use else R.string.city_here_install,
            ) { _, _ -> switchTo(city.id, installed) }
            .setNegativeButton(R.string.action_cancel) { _, _ -> declineCity(city.id) }
            .show()
    }

    /**
     * Takes note that the offer was turned down.
     *
     * A "no" that lasted only until the application was closed put the same
     * question at the next launch, and at the one after that.
     */
    private fun declineCity(cityId: String) {
        lifecycleScope.launch { container.rememberCityRefusal(cityId) }
    }

    /**
     * Serves the accepted city.
     *
     * Its data already there, the map has everything it needs and reopens on
     * it. Otherwise the storage screen takes over: it announces the weight
     * before fetching anything (SPEC §4.4).
     */
    private fun switchTo(cityId: String, installed: Boolean) {
        lifecycleScope.launch {
            container.switchToCity(cityId)
            if (installed) {
                replaceWith(MapFragment())
            } else {
                show(StorageFragment.checkingForUpdates())
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
     * The very search the address box runs, asked in the same terms, so that
     * the two paths cannot answer differently: the same query, the same number
     * of results — of which the first is kept — and the same wording for it.
     * The one thing said differently is that this text is **finished**: nobody
     * is typing it, and its first result becomes a journey without anyone
     * choosing it, so a word must not stand for a longer one (SPEC §7.8).
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
        val outcome = container.addressIndex.search(
            text,
            origin = origin,
            matching = WordMatching.WholeWords,
        )
        val found = (outcome as? Outcome.Success)?.value?.firstOrNull()
        if (found == null) {
            showMessage(getString(R.string.incoming_address_not_found, text)) {
                show(JourneySearchFragment.newInstance())
            }
            return null
        }
        return JourneyEndpoint(found.toTitle(this), found.position)
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
