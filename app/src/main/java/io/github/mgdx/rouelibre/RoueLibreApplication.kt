package io.github.mgdx.rouelibre

import android.app.Application
import io.github.mgdx.rouelibre.ui.settings.applyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The application's entry point.
 *
 * It does nothing beyond holding the dependency container and applying the
 * chosen theme. No third-party library initialisation, no service
 * registration: the application must emit nothing at launch (SPEC §2, C3).
 */
class RoueLibreApplication : Application() {

    /** The dependency container, shared by every screen. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applyChosenTheme()
        followActiveCity()
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
