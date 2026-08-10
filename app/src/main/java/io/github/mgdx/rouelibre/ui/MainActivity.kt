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
 * L'unique activité de l'application (SPEC §3).
 *
 * Elle héberge les fragments, et accueille les lieux que d'autres
 * applications lui envoient (SPEC §7.8). Toute la logique vit dans les
 * fragments et dans les modèles de vue.
 */
class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null

    private val container
        get() = (application as RoueLibreApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Le contenu passe sous les barres système, que le thème colore comme
        // le fond : l'écran se lit d'un seul tenant.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val created = ActivityMainBinding.inflate(layoutInflater)
        binding = created
        setContentView(created.root)

        // Sur recréation — rotation, changement de thème — les fragments sont
        // restaurés par le système ; les replacer effacerait leur état, et
        // rejouer l'intention rouvrirait un écran que l'utilisateur a quitté.
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
     * Montre l'accueil ou les nouveautés, s'il y a lieu (SPEC §7.9, §7.10).
     *
     * Le dernier code de version vu tranche entre les trois cas : jamais
     * lancée, mise à jour depuis, ou rien de neuf. Les deux écrans sont
     * exclusifs — les nouveautés ne s'affichent **jamais** à une première
     * installation, où c'est l'accueil qui s'applique.
     *
     * La lecture est asynchrone : une lecture bloquante du disque retarderait
     * le premier dessin pour un réglage qui, la plupart du temps, ne demande
     * rien.
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

                // Rien à montrer, mais la version vue se met à jour : une
                // version publiée sans note ne doit pas faire réapparaître les
                // notes de la précédente au lancement suivant.
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
     * Prend en charge un lieu reçu d'une autre application (SPEC §7.8).
     *
     * Rien n'est envoyé sur le réseau à cette occasion : une adresse en toutes
     * lettres est résolue par l'index local, comme partout ailleurs.
     */
    private fun welcome(intent: Intent) {
        val request = intent.toPlaceRequest() ?: return
        lifecycleScope.launch {
            val destination = resolve(request) ?: return@launch
            openFor(destination)
        }
    }

    /**
     * Transforme la demande reçue en point nommé.
     *
     * @return le point, ou `null` si l'on n'a pas pu le placer — auquel cas
     *   l'utilisateur a déjà été informé de ce qui manquait.
     */
    private suspend fun resolve(request: PlaceRequest): JourneyEndpoint? = when (request) {
        is PlaceRequest.Point -> JourneyEndpoint(
            label = request.label?.takeIf { it.isNotBlank() }
                // Un point sans libellé reçoit celui de la voie la plus
                // proche : « Rue Nationale » se relit, « 50,63 / 3,06 » non.
                ?: container.addressIndex.nearestAddress(request.coordinates)?.streetName
                ?: getString(R.string.incoming_place_default_label),
            position = request.coordinates,
        )

        is PlaceRequest.Search -> searchAddress(request.text)
    }

    /**
     * Cherche dans l'index l'adresse reçue en toutes lettres.
     *
     * Sans index, il faut le dire et proposer de l'installer plutôt que
     * d'échouer (SPEC §7.8).
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
                show(JourneySearchFragment.newInstance(null))
            }
            return null
        }
        return JourneyEndpoint(found.streetName, found.position)
    }

    /**
     * Ouvre l'écran qui convient au point reçu.
     *
     * Hors de l'emprise couverte, aucun itinéraire n'est tenté : la carte
     * montre le point si elle le peut, et l'application dit pourquoi elle
     * s'arrête là (SPEC §4, §7.8).
     */
    private suspend fun openFor(destination: JourneyEndpoint) {
        val boundingBox = container.activeCity()?.boundingBox
        if (boundingBox != null && destination.position !in boundingBox) {
            show(MapFragment.showing(destination))
            showMessage(getString(R.string.incoming_outside_coverage))
            return
        }

        // Le départ est la position courante quand elle est déjà connue. La
        // demander à cette occasion serait une relance que le SPEC §10 exclut :
        // l'utilisateur n'a pas ouvert l'application, on la lui a ouverte.
        val here = container.deviceLocation.lastKnown()
        if (here == null) {
            show(JourneySearchFragment.newInstance(destination))
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
     * Point de référence du classement des adresses, faute de position.
     *
     * Le centre de la ville active, qui n'est pas une position de l'utilisateur
     * mais un point fixe de la configuration. Sans ville, il n'y a pas d'index
     * d'adresses non plus : l'appelant n'arrive jamais jusqu'ici.
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
