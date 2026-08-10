package io.github.mgdx.rouelibre.journey

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.JourneyPlanner
import io.github.mgdx.rouelibre.core.journey.Router
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.joinStationsWithAvailability
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import io.github.mgdx.rouelibre.data.routing.OfflineRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Éprouve l'algorithme de trajet sur le vrai graphe, avec les vraies stations.
 *
 * C'est le seul endroit où le critère d'acceptation §11.4 peut réellement être
 * vérifié : « un itinéraire entre deux points de la métropole renvoie un trajet
 * marche → vélo → marche en moins de 3 secondes ». Les tests JVM de
 * l'algorithme utilisent un moteur simulé et ne disent rien du temps de calcul
 * réel.
 *
 * Les données sont des captures du 9 août 2026 : 268 stations et leur état.
 */
@RunWith(AndroidJUnit4::class)
class JourneyPlannerOnDeviceTest {

    private lateinit var planner: JourneyPlanner
    private lateinit var stations: List<StationWithAvailability>

    /** Place du Théâtre, Lille. */
    private val lilleCentre = Coordinates(50.6383, 3.0640)

    /** Parc Barbieux, Roubaix — huit kilomètres au nord-est. */
    private val roubaix = Coordinates(50.6805, 3.1620)

    /** Gare de Villeneuve-d'Ascq Pont de Bois, à l'est. */
    private val villeneuveDAscq = Coordinates(50.6270, 3.1400)

    @Before
    fun prepare() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

        val datasets = DatasetStore(target, Dispatchers.IO)
        // Les jeux sont rangés par réseau : le graphe du test va dans le sien,
        // pour ne pas se mêler aux données d'une ville installée.
        datasets.useCity(TEST_CITY)
        val graph = checkNotNull(datasets.directoryOf(DatasetKind.Routing)).resolve(GRAPH_FILE)
        if (!graph.isFile) {
            testAssets.open(GRAPH_FILE).use { source ->
                graph.outputStream().use { source.copyTo(it) }
            }
        }

        val parser = GbfsParser()
        val information = parser.parseStationInformation(
            testAssets.open("station_information.json").bufferedReader().use { it.readText() },
        ).orFail()
        val status = parser.parseStationStatus(
            testAssets.open("station_status.json").bufferedReader().use { it.readText() },
        ).orFail()
        stations = joinStationsWithAvailability(information.stations, status.availabilities)

        val router = OfflineRouter(target, datasets, Dispatchers.Default)
        planner = JourneyPlanner(
            object : Router {
                override suspend fun route(
                    from: Coordinates,
                    to: Coordinates,
                    mode: TravelMode,
                ): RouteResult = router.route(from, to, mode)
            },
        )
    }

    @Test
    fun compose_un_trajet_complet_en_moins_de_trois_secondes() = runBlocking {
        // Un premier appel amorce les caches du moteur ; c'est le second qui
        // reflète ce que vit l'utilisateur, dont l'application aura déjà
        // affiché la carte.
        planner.plan(lilleCentre, roubaix, stations)

        lateinit var plan: JourneyPlan
        val elapsed = measureTimeMillis {
            plan = planner.plan(lilleCentre, villeneuveDAscq, stations)
        }

        // La mesure est journalisée : un test qui se contente de comparer à
        // un seuil ne dit pas de combien on est passé, et c'est justement ce
        // qu'on veut surveiller au fil des versions.
        Log.i(TAG, "trajet complet composé en $elapsed ms")

        assertTrue("aucun trajet trouvé : $plan", plan is JourneyPlan.Found)
        assertTrue("trop lent : $elapsed ms", elapsed < BUDGET_MILLIS)
    }

    @Test
    fun le_trajet_retenu_a_bien_un_velo_au_depart_et_une_place_a_l_arrivee() = runBlocking {
        // Critère d'acceptation §11.5.
        val plan = planner.plan(lilleCentre, roubaix, stations) as JourneyPlan.Found

        assertTrue(
            "station de départ sans vélo : ${plan.best.bikesAtDeparture}",
            plan.best.bikesAtDeparture >= 1,
        )
        assertTrue(
            "station d'arrivée sans place : ${plan.best.docksAtArrival}",
            plan.best.docksAtArrival >= 1,
        )
    }

    @Test
    fun le_trajet_enchaine_bien_marche_velo_marche() = runBlocking {
        val plan = planner.plan(lilleCentre, roubaix, stations) as JourneyPlan.Found
        val best = plan.best

        assertTrue(TravelMode.Walking == best.walkToStation.mode)
        assertTrue(TravelMode.Cycling == best.ride.mode)
        assertTrue(TravelMode.Walking == best.walkToDestination.mode)
        // Les marches d'accès doivent rester des marches d'accès.
        assertTrue(
            "marche d'accès démesurée : ${best.walkToStation.distanceMetres} m",
            best.walkToStation.distanceMetres < 2_000,
        )
        assertTrue("trajet à vélo vide", best.ride.distanceMetres > 500)
    }

    @Test
    fun propose_des_alternatives_distinctes() = runBlocking {
        val plan = planner.plan(lilleCentre, roubaix, stations) as JourneyPlan.Found

        val pairs = (listOf(plan.best) + plan.alternatives)
            .map { it.departureStation.id to it.arrivalStation.id }
        assertTrue("alternatives en double : $pairs", pairs.size == pairs.toSet().size)
    }

    private fun <T> Outcome<T>.orFail(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> throw AssertionError("capture illisible : $error")
    }

    private companion object {
        const val TAG = "RoueLibrePerf"
        const val GRAPH_FILE = "E0_N50.rd5"

        /** Réseau propre au test, pour n'effacer aucune donnée installée. */
        const val TEST_CITY = "reseau-de-test"

        /** Le budget du SPEC §6, avec la marge d'un émulateur. */
        const val BUDGET_MILLIS = 3_000L
    }
}
