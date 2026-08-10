package io.github.mgdx.rouelibre

import android.app.Application
import io.github.mgdx.rouelibre.ui.settings.applyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Point d'entrée de l'application.
 *
 * Ne fait rien d'autre que porter le conteneur de dépendances et appliquer le
 * thème choisi. Aucune initialisation de bibliothèque tierce, aucun
 * enregistrement de service : l'application ne doit rien émettre au lancement
 * (SPEC §2, C3).
 */
class RoueLibreApplication : Application() {

    /** Conteneur de dépendances, partagé par tous les écrans. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applyChosenTheme()
    }

    /**
     * Applique le thème enregistré, et suit ses changements.
     *
     * Lu de façon asynchrone : une lecture bloquante du disque au démarrage
     * retarderait le premier dessin pour un réglage que la plupart des gens
     * laissent sur « système ». Le thème choisi s'applique donc juste après,
     * en recréant l'écran — visible au tout premier lancement seulement.
     */
    private fun applyChosenTheme() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            container.preferences.theme.collect(::applyTheme)
        }
    }
}
