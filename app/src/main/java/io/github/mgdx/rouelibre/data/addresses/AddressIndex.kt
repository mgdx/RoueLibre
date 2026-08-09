package io.github.mgdx.rouelibre.data.addresses

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.address.AddressEntryKind
import io.github.mgdx.rouelibre.core.address.AddressNormalizer
import io.github.mgdx.rouelibre.core.address.AddressQuery
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.address.KnownHouseNumber
import io.github.mgdx.rouelibre.core.address.PositionPrecision
import io.github.mgdx.rouelibre.core.address.ScoredStreet
import io.github.mgdx.rouelibre.core.address.SearchableStreet
import io.github.mgdx.rouelibre.core.address.parseQuery
import io.github.mgdx.rouelibre.core.address.rankStreets
import io.github.mgdx.rouelibre.core.address.resolveHouseNumber
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Recherche d'adresses dans l'index hors ligne (SPEC §4.3).
 *
 * **Rien ne sort de l'appareil, jamais, pas même pendant la frappe.** C'est la
 * donnée la plus sensible de l'application : elle révèle où va l'utilisateur.
 * Aucun géocodeur en ligne n'est appelé, il n'y en a pas dans le projet.
 *
 * La recherche se fait en deux étages, comme le prescrit le SPEC :
 *
 * 1. **Index plein texte** sur les noms de voies, interrogé par préfixe, ce qui
 *    couvre la frappe en cours. C'est ce qui rend l'ensemble viable : les
 *    numéros, eux, ne sont jamais cherchés en texte.
 * 2. **Rattrapage par distance d'édition** quand le premier étage rend trop peu
 *    de résultats, parcouru en Kotlin sur les noms normalisés tenus en mémoire.
 *    Le *tokenizer* trigramme de SQLite aurait pu s'en charger, mais il est
 *    absent des SQLite embarqués dans les Android les plus anciens que vise
 *    l'application.
 *
 * @property datasetStore où trouver le fichier d'index installé.
 * @property normalizer les règles partagées avec le script d'indexation.
 * @property ioDispatcher contexte d'exécution : la lecture du fichier comme le
 *   parcours flou sont trop longs pour le fil principal.
 */
class AddressIndex(
    private val datasetStore: DatasetStore,
    private val normalizer: AddressNormalizer,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Base ouverte et corpus chargé, pour un fichier donné.
     *
     * Gardés d'une recherche à l'autre : rouvrir la base et relire vingt mille
     * lignes à chaque frappe coûterait plus que la recherche elle-même.
     */
    private class OpenIndex(
        val signature: String,
        val database: SQLiteDatabase,
        val streets: List<SearchableStreet>,
        val deltaScale: Double,
    )

    private val openMutex = Mutex()
    private var opened: OpenIndex? = null

    /** Vrai si l'index est installé sur l'appareil. */
    fun isInstalled(): Boolean = datasetStore.fileOf(DatasetKind.Addresses) != null

    /**
     * Cherche une adresse.
     *
     * @param rawQuery la saisie, telle que tapée. Un numéro de voirie y est
     *   reconnu dans les deux ordres d'écriture.
     * @param origin point de référence pour classer les résultats par
     *   proximité — position de l'utilisateur ou centre de la carte. `null` si
     *   aucun n'est connu.
     * @param limit nombre de résultats souhaités.
     * @return les adresses trouvées, la meilleure d'abord ; une liste vide si
     *   la saisie ne désigne rien ; un échec si l'index est absent ou illisible.
     */
    suspend fun search(
        rawQuery: String,
        origin: Coordinates?,
        limit: Int = DEFAULT_RESULT_COUNT,
    ): Outcome<List<AddressResult>> = withContext(ioDispatcher) {
        val query = normalizer.parseQuery(rawQuery)
        if (query.isEmpty) return@withContext Outcome.Success(emptyList())

        val index = try {
            open() ?: return@withContext Outcome.Failure(
                DataError.LocalStorageFailure("index d'adresses absent"),
            )
        } catch (error: RuntimeException) {
            return@withContext Outcome.Failure(
                DataError.LocalStorageFailure(error.message ?: "index d'adresses illisible"),
            )
        }

        try {
            val ranked = rank(index, query, origin, limit)
            Outcome.Success(ranked.map { scored -> index.toResult(scored, query) })
        } catch (error: RuntimeException) {
            // Un index corrompu ne doit pas faire tomber l'écran : il se
            // réimporte depuis l'écran de stockage.
            close()
            Outcome.Failure(
                DataError.LocalStorageFailure(error.message ?: "recherche impossible"),
            )
        }
    }

    /**
     * L'adresse la plus proche d'un point, s'il y en a une (SPEC §7.2).
     *
     * Le flux GBFS du réseau lillois ne publie pas d'adresse de station : il
     * donne un nom et un code postal. L'index, lui, sait où sont les numéros —
     * autant s'en servir plutôt que de laisser la feuille de détail muette.
     *
     * La recherche part des voies dont le point représentatif est dans un
     * rayon large, puis descend à leurs numéros. Ce détour est nécessaire :
     * le point d'une voie est sa médiane, et sur une artère d'un kilomètre il
     * peut être très loin du point cherché alors que la voie passe juste à
     * côté.
     *
     * @param point l'endroit dont on cherche l'adresse.
     * @return l'adresse retenue, ou `null` si rien d'assez proche n'est connu —
     *   mieux vaut ne rien afficher qu'annoncer la mauvaise rue.
     */
    suspend fun nearestAddress(point: Coordinates): AddressResult? = withContext(ioDispatcher) {
        val index = try {
            open() ?: return@withContext null
        } catch (_: RuntimeException) {
            return@withContext null
        }
        try {
            index.nearestAddressTo(point)
        } catch (_: RuntimeException) {
            close()
            null
        }
    }

    private fun OpenIndex.nearestAddressTo(point: Coordinates): AddressResult? {
        val candidates = streets
            .filter { it.position.distanceInMetresTo(point) <= CANDIDATE_STREET_RADIUS_METRES }
            .sortedBy { it.position.distanceInMetresTo(point) }
            .take(MAX_REVERSE_CANDIDATES)
        if (candidates.isEmpty()) return null

        var best: Pair<SearchableStreet, KnownHouseNumber?>? = null
        var bestDistance = Double.MAX_VALUE
        for (street in candidates) {
            val numbers = readHouseNumbers(street.id, street.position)
            val nearestNumber = numbers.minByOrNull { it.position.distanceInMetresTo(point) }
            // Une voie sans numéro n'est pas écartée : son point reste une
            // indication, à condition d'être vraiment proche.
            val distance = nearestNumber?.position?.distanceInMetresTo(point)
                ?: street.position.distanceInMetresTo(point)
            if (distance < bestDistance) {
                bestDistance = distance
                best = street to nearestNumber
            }
        }

        val (street, number) = best ?: return null
        if (bestDistance > NEAREST_ADDRESS_LIMIT_METRES) return null

        // Au-delà de quelques mètres, le numéro n'est plus celui du point : le
        // dire reviendrait à désigner un immeuble voisin. La voie, elle, reste
        // une indication juste — « à proximité de la rue Chanzy » vaut mieux
        // que rien, et mieux qu'un numéro faux.
        val isAtTheAddress = bestDistance <= NUMBERED_ADDRESS_LIMIT_METRES
        val row = readStreetRow(street.id)
        return AddressResult(
            streetId = street.id,
            houseNumber = number?.number?.takeIf { isAtTheAddress },
            houseNumberSuffix = if (isAtTheAddress) number?.suffix.orEmpty() else "",
            streetName = row.displayName,
            city = row.city,
            postcode = row.postcode,
            kind = row.kind,
            position = number?.position ?: street.position,
            precision = if (number == null || !isAtTheAddress) {
                PositionPrecision.StreetOnly
            } else {
                PositionPrecision.Exact
            },
            distanceInMetres = bestDistance,
        )
    }

    /**
     * Classe les voies candidates, en passant par le rattrapage flou au besoin.
     *
     * Le second étage n'est tenté que si le premier rend peu de choses : il
     * parcourt tout le corpus, ce qui ne se justifie pas quand l'index plein
     * texte a déjà répondu.
     */
    private suspend fun rank(
        index: OpenIndex,
        query: AddressQuery,
        origin: Coordinates?,
        limit: Int,
    ): List<ScoredStreet> {
        val byPrefix = index.matchingFullText(query)
        val exact = rankStreets(byPrefix, query, normalizer.stopWords, origin, limit)
        if (exact.size >= MINIMUM_PREFIX_RESULTS || exact.size >= limit) return exact

        coroutineContext.ensureActive()
        return rankStreets(index.streets, query, normalizer.stopWords, origin, limit)
    }

    // ------------------------------------------------------ premier étage --

    /**
     * Les voies dont un mot commence par chacun des mots saisis.
     *
     * Les mots vides sont écartés de l'interrogation lorsqu'il reste autre
     * chose : demander à l'index toutes les voies contenant « de » reviendrait
     * à en parcourir la moitié pour ne rien apprendre. Ils comptent en
     * revanche au classement, lui.
     */
    private fun OpenIndex.matchingFullText(query: AddressQuery): List<SearchableStreet> {
        val meaningful = query.terms.filterNot { it in normalizer.stopWords }
        val searched = meaningful.ifEmpty { query.terms }
        val expression = searched.joinToString(" ") { "$it*" }

        val matched = HashSet<Long>()
        database.rawQuery(
            "SELECT docid FROM $SEARCH_TABLE WHERE $SEARCH_TABLE MATCH ? LIMIT ?",
            arrayOf(expression, MAX_FULL_TEXT_ROWS.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) matched.add(cursor.getLong(0))
        }
        if (matched.isEmpty()) return emptyList()
        return streets.filter { it.id in matched }
    }

    // ------------------------------------------------------ mise en forme --

    /** Complète une voie retenue par ce qui ne sert qu'à l'affichage. */
    private fun OpenIndex.toResult(scored: ScoredStreet, query: AddressQuery): AddressResult {
        val row = readStreetRow(scored.street.id)
        val requested = query.houseNumber
        val resolved = if (requested == null) {
            null
        } else {
            resolveHouseNumber(
                requestedNumber = requested,
                requestedSuffix = query.houseNumberSuffix,
                knownNumbers = readHouseNumbers(scored.street.id, scored.street.position),
                streetPosition = scored.street.position,
            )
        }

        return AddressResult(
            streetId = scored.street.id,
            // Un numéro que la voie ne permet pas de placer n'est pas affiché :
            // écrire « 12 rue X » en pointant le milieu de la rue serait une
            // promesse que la position ne tient pas.
            houseNumber = requested.takeIf {
                resolved != null && resolved.precision != PositionPrecision.StreetOnly
            },
            houseNumberSuffix = query.houseNumberSuffix,
            streetName = row.displayName,
            city = row.city,
            postcode = row.postcode,
            kind = row.kind,
            position = resolved?.coordinates ?: scored.street.position,
            precision = resolved?.precision ?: PositionPrecision.StreetOnly,
            distanceInMetres = scored.distanceInMetres,
        )
    }

    private class StreetRow(
        val displayName: String,
        val city: String,
        val postcode: String?,
        val kind: AddressEntryKind,
    )

    private fun OpenIndex.readStreetRow(streetId: Long): StreetRow = database.rawQuery(
        "SELECT display_name, city, former_city, postcode, kind FROM street WHERE id = ?",
        arrayOf(streetId.toString()),
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            error("voie $streetId absente de l'index")
        }
        StreetRow(
            // La commune absorbée prime à l'affichage quand il y en a une :
            // une adresse de Lomme s'écrit « 59160 Lomme », même si la Base
            // Adresse Nationale la rattache administrativement à Lille. Le
            // code postal qui l'accompagne est d'ailleurs celui de Lomme.
            displayName = cursor.getString(0),
            city = cursor.getStringOrNull(2) ?: cursor.getString(1),
            postcode = cursor.getStringOrNull(3),
            kind = AddressEntryKind.fromCode(cursor.getInt(4)),
        )
    }

    /**
     * Les numéros d'une voie, coordonnées reconstituées.
     *
     * Toute la voie est lue d'un coup plutôt que ses seuls voisins immédiats :
     * une voie porte quelques dizaines de numéros, et cette lecture unique
     * évite de raisonner en SQL sur la parité et l'encadrement.
     */
    private fun OpenIndex.readHouseNumbers(
        streetId: Long,
        streetPosition: Coordinates,
    ): List<KnownHouseNumber> = database.rawQuery(
        "SELECT number, suffix, delta_lat, delta_lon FROM house_number WHERE street_id = ?",
        arrayOf(streetId.toString()),
    ).use { cursor ->
        val numbers = ArrayList<KnownHouseNumber>(cursor.count)
        while (cursor.moveToNext()) {
            numbers.add(
                KnownHouseNumber(
                    number = cursor.getInt(0),
                    suffix = cursor.getString(1),
                    position = Coordinates(
                        latitude = streetPosition.latitude + cursor.getInt(2) / deltaScale,
                        longitude = streetPosition.longitude + cursor.getInt(3) / deltaScale,
                    ),
                ),
            )
        }
        numbers
    }

    // ------------------------------------------------------- ouverture --

    /**
     * Ouvre l'index, ou rend celui déjà ouvert.
     *
     * Le fichier est identifié par son chemin, sa taille et sa date : une
     * réimportation depuis l'écran de stockage doit être prise en compte sans
     * redémarrer l'application.
     *
     * @return `null` si l'index n'est pas installé.
     */
    private suspend fun open(): OpenIndex? {
        val file = datasetStore.fileOf(DatasetKind.Addresses) ?: run {
            close()
            return null
        }
        val signature = file.signature()
        opened?.let { if (it.signature == signature) return it }

        return openMutex.withLock {
            opened?.let { if (it.signature == signature) return@withLock it }
            opened?.database?.close()
            val database = SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READONLY,
                // Le gestionnaire par défaut SUPPRIME le fichier qu'il juge
                // corrompu. Un index de six mégaoctets ne disparaît pas sur un
                // doute : l'utilisateur le réimportera s'il le faut.
                { /* ne rien supprimer */ },
            )
            OpenIndex(
                signature = signature,
                database = database,
                streets = database.readCorpus(),
                deltaScale = database.readDeltaScale(),
            ).also { opened = it }
        }
    }

    /** Referme l'index ouvert, s'il y en a un. */
    fun close() {
        opened?.database?.close()
        opened = null
    }

    private fun File.signature(): String = "$path:${length()}:${lastModified()}"

    /**
     * Charge en mémoire ce que le rattrapage flou doit parcourir.
     *
     * Les noms d'origine, les communes affichées et les codes postaux restent
     * sur le disque : seuls les huit résultats retenus en ont l'usage, et les
     * garder tripleraient l'empreinte de ce corpus.
     */
    private fun SQLiteDatabase.readCorpus(): List<SearchableStreet> = rawQuery(
        "SELECT id, normalized_type, normalized_name, normalized_city, " +
            "normalized_former_city, latitude, longitude FROM street",
        null,
    ).use { cursor ->
        val streets = ArrayList<SearchableStreet>(cursor.count)
        while (cursor.moveToNext()) {
            streets.add(
                SearchableStreet(
                    id = cursor.getLong(0),
                    normalizedType = cursor.getStringOrNull(1),
                    normalizedName = cursor.getString(2),
                    normalizedCity = cursor.getString(3),
                    normalizedFormerCity = cursor.getStringOrNull(4),
                    position = Coordinates(cursor.getDouble(5), cursor.getDouble(6)),
                ),
            )
        }
        streets
    }

    /**
     * L'unité dans laquelle les coordonnées des numéros sont stockées.
     *
     * Lue dans le fichier plutôt que fixée dans le code : c'est le script de
     * génération qui la décide, et un index produit avec une autre unité doit
     * rester lisible.
     */
    private fun SQLiteDatabase.readDeltaScale(): Double = rawQuery(
        "SELECT value FROM metadata WHERE key = ?",
        arrayOf("deltaScale"),
    ).use { cursor ->
        val stored = if (cursor.moveToFirst()) cursor.getString(0)?.toDoubleOrNull() else null
        stored?.takeIf { it > 0.0 } ?: DEFAULT_DELTA_SCALE
    }

    private fun Cursor.getStringOrNull(column: Int): String? =
        if (isNull(column)) null else getString(column)

    private companion object {
        const val SEARCH_TABLE = "street_search"

        /** Nombre de résultats montrés par défaut : ce qui tient sous le clavier. */
        const val DEFAULT_RESULT_COUNT = 8

        /**
         * En dessous de ce nombre de résultats, le second étage se déclenche.
         *
         * Trois lignes, c'est le moment où l'utilisateur commence à se demander
         * si sa rue existe. C'est donc là qu'il faut aller la chercher malgré
         * une faute de frappe, et pas avant : le parcours complet coûte des
         * dizaines de millisecondes qu'une recherche fructueuse n'a pas à payer.
         */
        const val MINIMUM_PREFIX_RESULTS = 3

        /**
         * Plafond de lignes rendues par l'index plein texte.
         *
         * Une saisie courte — « rue » — peut correspondre à la moitié de
         * l'index. Le classement, lui, ne montre que huit lignes : au-delà de
         * ce plafond, lire davantage ne changerait que le temps de réponse.
         */
        const val MAX_FULL_TEXT_ROWS = 400

        /** Cent-millièmes de degré, ce qu'écrit le script de génération. */
        const val DEFAULT_DELTA_SCALE = 100_000.0

        /**
         * Rayon dans lequel une voie est examinée pour un géocodage inverse.
         *
         * Large à dessein : c'est le point *représentatif* de la voie qui est
         * comparé, et la médiane d'une artère d'un kilomètre peut se trouver
         * loin d'un point qu'elle longe pourtant.
         */
        const val CANDIDATE_STREET_RADIUS_METRES = 900.0

        /** Au-delà, lire les numéros de voies supplémentaires n'apprend plus rien. */
        const val MAX_REVERSE_CANDIDATES = 40

        /**
         * Distance en deçà de laquelle le numéro trouvé est bien celui du
         * point. Cinquante mètres, c'est la largeur d'un carrefour.
         *
         * Mesuré sur les stations réelles du réseau : la moitié d'entre elles
         * sont à moins de quinze mètres d'une adresse connue, neuf sur dix à
         * moins de quarante.
         */
        const val NUMBERED_ADDRESS_LIMIT_METRES = 50.0

        /**
         * Distance au-delà de laquelle plus rien n'est dit.
         *
         * Entre les deux seuils, seule la voie est nommée, et comme un
         * voisinage : une station posée au milieu d'un rond-point n'a pas
         * d'adresse, mais dire de quelle rue elle est proche aide quand même.
         */
        const val NEAREST_ADDRESS_LIMIT_METRES = 150.0
    }
}
