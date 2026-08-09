package io.github.mgdx.rouelibre

import android.app.Application

/**
 * Point d'entrée de l'application.
 *
 * Ne fait rien d'autre que porter le conteneur de dépendances. Aucune
 * initialisation de bibliothèque tierce, aucun enregistrement de service :
 * l'application ne doit rien émettre au lancement (SPEC §2, C3).
 */
class RoueLibreApplication : Application() {

    /** Conteneur de dépendances, partagé par tous les écrans. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
