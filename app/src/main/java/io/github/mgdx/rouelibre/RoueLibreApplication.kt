package io.github.mgdx.rouelibre

import android.app.Application
import io.github.mgdx.rouelibre.ui.DisplayedUnits
import io.github.mgdx.rouelibre.ui.settings.applyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The application's entry point.
 *
 * It does nothing beyond holding the dependency container and putting the
 * chosen theme and units into service. No third-party library initialisation,
 * no service registration: the application must emit nothing at launch
 * (SPEC §2, C3).
 */
class RoueLibreApplication : Application() {

    /** The dependency container, shared by every screen. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applyChosenUnits()
        applyChosenTheme()
        followActiveCity()
    }

    /**
     * Puts the chosen units into service, and follows their changes.
     *
     * **Read synchronously**, like the theme just below, and for a reason of
     * its own. Units arriving late leave a figure written in the wrong unit on
     * a screen nothing will redraw, which is not a wrong colour but a wrong
     * number. The read costs a few milliseconds of a start-up whose opening
     * screen is held for six hundred (SPEC §7.0), and it saves rebuilding the
     * first screen of every launch of everyone who chose units their region
     * does not use.
     */
    private fun applyChosenUnits() {
        DisplayedUnits.follow(runBlocking { container.preferences.units.first() })
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            container.preferences.units.collect(DisplayedUnits::follow)
        }
    }

    /**
     * Puts the active city's data into service, and follows its changes.
     *
     * The datasets are stored per city: without this, the store would not know
     * which directory to read from. The watching matters for the storage
     * screen, which observes the inventory without ever asking for a city.
     */
    private fun followActiveCity() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            container.preferences.activeCityIdFlow.collect(container.datasetStore::useCity)
        }
    }

    /**
     * Applies the stored theme, and follows its changes.
     *
     * **Read synchronously, before any screen exists**, and not for the
     * colours' sake. It used to be read in the background, on the reasoning
     * that a theme arriving a moment late only repaints the screen; but a
     * theme other than the system's is applied by recreating the activity,
     * and arriving late meant arriving in the middle of the opening (SPEC
     * §7.0), whose first screen is committed a frame after the activity is
     * born. Recreated in that gap, the activity came back as a restoration
     * with nothing to restore, and the screen stayed empty for good: a blank
     * in the theme's colour, at every cold start of anyone whose theme
     * differed from the system's on a phone slow enough to read it late
     * (issue #16). Read here, the theme is in place before the first activity
     * is built, which is then built once and in the right colours.
     *
     * The read costs what the units' costs, a few milliseconds under an
     * opening held for six hundred. The changes made in the settings still
     * apply at once, by the recreation the theme exists to trigger — the first
     * value collected is the one just applied, which changes nothing.
     */
    private fun applyChosenTheme() {
        applyTheme(runBlocking { container.preferences.theme.first() })
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            container.preferences.theme.collect(::applyTheme)
        }
    }
}
