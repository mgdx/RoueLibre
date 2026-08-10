package io.github.mgdx.rouelibre

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.address.AddressNormalizer
import io.github.mgdx.rouelibre.core.address.AddressNormalizerReader
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.config.CityConfigurationReader
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.Router
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.data.AppPreferences
import io.github.mgdx.rouelibre.data.StationRepository
import io.github.mgdx.rouelibre.data.addresses.AddressIndex
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import io.github.mgdx.rouelibre.data.local.StationDatabase
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.data.network.GbfsRemoteSource
import io.github.mgdx.rouelibre.data.routing.OfflineRouter
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.time.Duration

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

/**
 * Assemble les dépendances de l'application, à la main.
 *
 * Pas de Hilt ni de Koin (SPEC §3) : l'arbre de dépendances tient sur un
 * écran, et un conteneur explicite se lit sans connaître de framework — ce qui
 * compte pour un projet destiné à être audité et repris.
 *
 * Tout est construit paresseusement : rien n'est initialisé tant qu'aucun
 * écran n'en a besoin.
 */
class AppContainer(private val context: Context) {

    /**
     * La configuration de ville, copiée dans les ressources au moment du
     * build depuis `config/cities/lille.json`.
     *
     * La lecture est synchrone : le fichier fait deux kilooctets et se trouve
     * déjà dans l'APK projeté en mémoire.
     *
     * @throws IllegalStateException si le fichier est absent ou illisible. Ce
     *   n'est pas une situation utilisateur mais un défaut de fabrication de
     *   l'APK : l'application ne peut alors rien faire de sensé.
     */
    val cityConfiguration: CityConfiguration by lazy {
        val document = context.assets.open(CITY_CONFIGURATION_ASSET)
            .bufferedReader()
            .use { it.readText() }
        when (val outcome = CityConfigurationReader.read(document)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> error(
                "Configuration de ville illisible dans l'APK : ${outcome.error}",
            )
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            // Aucun cache disque : la politique de fraîcheur est celle du
            // dépôt (SPEC §4.1), et un cache HTTP par-dessus la rendrait
            // impossible à raisonner.
            .cache(null)
            .build()
    }

    /** Réglages et favoris, partagés par les écrans qui les lisent. */
    val preferences: AppPreferences by lazy {
        AppPreferences(context.preferencesDataStore)
    }

    private val database: StationDatabase by lazy {
        Room.databaseBuilder(
            context,
            StationDatabase::class.java,
            StationDatabase.FILE_NAME,
        ).build()
    }

    /**
     * Les jeux de données hors ligne installés sur l'appareil.
     *
     * Créé tôt et partagé : la carte, le routage et la recherche d'adresses y
     * liront tous le fichier dont ils dépendent.
     */
    val datasetStore: DatasetStore by lazy {
        DatasetStore(context, Dispatchers.IO)
    }

    /**
     * Calcul d'itinéraires sur l'appareil.
     *
     * Le calcul est purement processeur : il tourne sur le répartiteur prévu
     * pour cela, pas sur celui des entrées-sorties.
     */
    val router: OfflineRouter by lazy {
        OfflineRouter(context, datasetStore, Dispatchers.Default)
    }

    /**
     * Les règles de normalisation des noms de voies, partagées avec le script
     * qui construit l'index (SPEC §4.3).
     *
     * Le fichier est copié dans l'APK au moment du build depuis
     * `config/address_normalization.json`, source unique des deux côtés : une
     * divergence rendrait des rues introuvables.
     *
     * @throws IllegalStateException si le fichier est absent ou illisible —
     *   défaut de fabrication de l'APK, pas situation utilisateur.
     */
    val addressNormalizer: AddressNormalizer by lazy {
        val document = context.assets.open(NORMALIZATION_RULES_ASSET)
            .bufferedReader()
            .use { it.readText() }
        when (val outcome = AddressNormalizerReader.read(document)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> error(
                "Règles de normalisation illisibles dans l'APK : ${outcome.error}",
            )
        }
    }

    /**
     * Position de l'appareil, demandée au moment de l'usage seulement.
     *
     * Sans état : chaque appel interroge le système. Rien n'est mis en cache,
     * donc rien ne survit à la session (SPEC §2, C3).
     */
    val deviceLocation: DeviceLocation by lazy { DeviceLocation(context) }

    /**
     * Recherche d'adresses hors ligne.
     *
     * Sur le répartiteur des entrées-sorties : la première recherche ouvre le
     * fichier et en lit le corpus, les suivantes le parcourent.
     */
    val addressIndex: AddressIndex by lazy {
        AddressIndex(datasetStore, addressNormalizer, Dispatchers.IO)
    }

    /**
     * Le moteur de l'appareil, vu par l'algorithme de trajet.
     *
     * Cet adaptateur existe pour que l'algorithme reste en Kotlin pur : il ne
     * connaît qu'une interface à deux points et un mode, jamais BRouter
     * (SPEC §14).
     *
     * Le planificateur, lui, n'est pas construit ici : ses réglages — les
     * temps forfaitaires du §6 — dépendent de ce que l'utilisateur a choisi, et
     * changent donc entre deux calculs.
     */
    val journeyRouter: Router by lazy {
        object : Router {
            override suspend fun route(
                from: Coordinates,
                to: Coordinates,
                mode: TravelMode,
            ): RouteResult = router.route(from, to, mode)
        }
    }

    /** Source unique des stations et de leur disponibilité. */
    val stationRepository: StationRepository by lazy {
        StationRepository(
            remote = GbfsRemoteSource(
                client = httpClient,
                parser = GbfsParser(),
                userAgent = userAgent(),
                ioDispatcher = Dispatchers.IO,
            ),
            dao = database.stationDao(),
            refreshTimestamps = preferences,
            // Le réglage utilisateur prime sur la configuration livrée ; il
            // est relu à chaque appel pour qu'un changement prenne effet sans
            // redémarrage (SPEC §4.1).
            discoveryUrlProvider = {
                preferences.gbfsDiscoveryUrlOverride()
                    ?: cityConfiguration.gbfs.discoveryUrl
            },
        )
    }

    /**
     * Identifie l'application et sa version auprès des producteurs de données,
     * sans aucun identifiant propre à l'utilisateur ou à l'appareil
     * (SPEC §4.4).
     */
    private fun userAgent(): String = "RoueLibre/${BuildConfig.VERSION_NAME} (+$REPOSITORY_URL)"

    private companion object {
        const val CITY_CONFIGURATION_ASSET = "city.json"
        const val NORMALIZATION_RULES_ASSET = "address_normalization.json"
        const val REPOSITORY_URL = "https://github.com/mgdx/RoueLibre"
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}
