package io.github.mgdx.rouelibre.data.routing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.RoutingFailure
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Éprouve BRouter là où il tournera vraiment : sur Android.
 *
 * Ce test existe parce que le reste de la chaîne ne prouve rien de ce qui
 * compte ici. Que le sous-module compile ne dit pas que son code s'exécute
 * sous ART ; qu'il calcule un itinéraire sur la machine de développement ne
 * dit pas qu'il retrouvera ses fichiers dans le stockage privé d'une
 * application.
 *
 * Le graphe de routage est dans les ressources du test, jamais dans celles de
 * l'application : le SPEC §5 exige qu'il soit téléchargé ou importé, pas
 * embarqué.
 */
@RunWith(AndroidJUnit4::class)
class OfflineRouterTest {

    private lateinit var router: OfflineRouter
    private lateinit var datasets: DatasetStore

    /** Grand-Place de Lille. */
    private val lilleCentre = Coordinates(50.6371, 3.0630)

    /** Grand-Place de Roubaix, à une douzaine de kilomètres. */
    private val roubaixCentre = Coordinates(50.6900, 3.1750)

    @Before
    fun installGraph() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

        datasets = DatasetStore(target, Dispatchers.IO)
        // Les jeux sont rangés par réseau : le graphe du test va dans le sien,
        // pour ne pas se mêler aux données d'une ville installée.
        datasets.useCity(TEST_CITY)
        val segments = checkNotNull(datasets.directoryOf(DatasetKind.Routing))
        val graph = segments.resolve(GRAPH_FILE)
        if (!graph.isFile) {
            testAssets.open(GRAPH_FILE).use { source ->
                graph.outputStream().use { source.copyTo(it) }
            }
        }
        router = OfflineRouter(target, datasets, Dispatchers.Default)
    }

    @Test
    fun computes_a_bike_route_between_two_towns_of_the_metropolis() = runBlocking {
        val result = router.route(lilleCentre, roubaixCentre, TravelMode.Cycling)

        val leg = (result as? RouteResult.Success)?.leg
            ?: throw AssertionError("échec inattendu : $result")

        assertEquals(TravelMode.Cycling, leg.mode)
        // À vol d'oiseau il y a environ 10 km ; un trajet praticable fait
        // nécessairement plus, sans pouvoir doubler la distance.
        assertTrue(
            "distance invraisemblable : ${leg.distanceMetres} m",
            leg.distanceMetres in 10_000..20_000,
        )
        assertTrue("durée invraisemblable : ${leg.duration}", leg.duration.inWholeMinutes in 20..90)
        assertTrue("tracé trop pauvre : ${leg.geometry.size} points", leg.geometry.size > 100)
    }

    @Test
    fun computes_a_walking_route_slower_than_the_same_one_by_bike() = runBlocking {
        val onFoot = router.route(lilleCentre, roubaixCentre, TravelMode.Walking)
        val onBike = router.route(lilleCentre, roubaixCentre, TravelMode.Cycling)

        val walking = (onFoot as? RouteResult.Success)?.leg
            ?: throw AssertionError("échec à pied : $onFoot")
        val cycling = (onBike as? RouteResult.Success)?.leg
            ?: throw AssertionError("échec à vélo : $onBike")

        assertTrue(
            "la marche devrait être plus lente que le vélo",
            walking.duration > cycling.duration,
        )
    }

    @Test
    fun the_track_really_starts_at_the_origin_and_ends_at_the_destination() = runBlocking {
        val result = router.route(lilleCentre, roubaixCentre, TravelMode.Cycling)
        val leg = (result as? RouteResult.Success)?.leg
            ?: throw AssertionError("échec inattendu : $result")

        // Le moteur accroche les extrémités au nœud praticable le plus proche ;
        // quelques dizaines de mètres d'écart sont normales, pas des
        // kilomètres.
        assertTrue(
            "le tracé ne part pas du point demandé",
            leg.geometry.first().distanceInMetresTo(lilleCentre) < TOLERANCE_METRES,
        )
        assertTrue(
            "le tracé n'arrive pas au point demandé",
            leg.geometry.last().distanceInMetresTo(roubaixCentre) < TOLERANCE_METRES,
        )
    }

    @Test
    fun a_point_outside_the_box_is_reported_and_does_not_crash() = runBlocking {
        // Bruxelles : hors du graphe téléchargé.
        val outside = Coordinates(50.8467, 4.3525)

        val result = router.route(lilleCentre, outside, TravelMode.Cycling)

        assertTrue("échec attendu, obtenu : $result", result is RouteResult.Failure)
        val reason = (result as RouteResult.Failure).reason
        assertTrue(
            "cause inattendue : $reason",
            reason is RoutingFailure.OutsideCoverage ||
                reason is RoutingFailure.NoRouteFound ||
                reason is RoutingFailure.EngineFailure,
        )
    }

    private companion object {
        const val GRAPH_FILE = "E0_N50.rd5"

        /** Réseau propre au test, pour n'effacer aucune donnée installée. */
        const val TEST_CITY = "reseau-de-test"
        const val TOLERANCE_METRES = 200.0
    }
}
