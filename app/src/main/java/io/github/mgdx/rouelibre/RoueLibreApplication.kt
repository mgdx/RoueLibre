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
     * **Read synchronously**, unlike the theme just below, and that difference
     * is deliberate. A theme arriving a moment late repaints a screen; units
     * arriving late leave a figure written in the wrong unit on a screen
     * nothing will redraw, which is not a wrong colour but a wrong number. The
     * read costs a few milliseconds of a start-up whose opening screen is held
     * for six hundred (SPEC §7.0), and it saves rebuilding the first screen of
     * every launch of everyone who chose units their region does not use.
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
     * Read asynchronously: a blocking disk read at start-up would delay the
     * first draw for a setting most people leave on "system". The chosen theme
     * therefore applies just afterwards, by recreating the screen — visible on
     * the very first launch only.
     */
    private fun applyChosenTheme() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            container.preferences.theme.collect(::applyTheme)
        }
    }
}
