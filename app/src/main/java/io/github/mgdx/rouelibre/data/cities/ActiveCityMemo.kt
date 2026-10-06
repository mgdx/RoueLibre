package io.github.mgdx.rouelibre.data.cities

import io.github.mgdx.rouelibre.core.config.ActiveCity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * The active city as last settled, held for as long as nothing it was settled
 * from has changed.
 *
 * Settling it reads both catalogues and a configuration, and a launch asks from
 * several screens at once — hence a memo, under a lock. It is keyed by the
 * identifier, so changing city invalidates it by itself; and it is forgotten
 * when the catalogue in force changes, since the answer depends on it as much
 * as on the identifier. Held for the life of the process instead, a network
 * brought back or withdrawn by a refresh kept its old verdict until the
 * application was restarted, re-choosing the same city changing nothing either.
 *
 * No Android import, so that this rule is tested on the JVM (SPEC §14).
 */
class ActiveCityMemo {

    private val lock = Mutex()

    /**
     * The last verdict, with the identifier and the generation it was settled
     * under. Only touched under [lock]; [forget], which is not, moves the
     * generation instead.
     */
    private var held: Settled? = null

    /**
     * Counts the calls to [forget]. A verdict is only good for the generation
     * it was settled under: one settled from catalogues replaced in the
     * meantime is never served, whichever thread finished first.
     */
    private val generation = AtomicInteger()

    /**
     * Where the city [id] stands, settled by [settle] unless it already was
     * since the last change.
     */
    suspend fun of(id: String, settle: suspend (String) -> ActiveCity): ActiveCity = lock.withLock {
        val current = generation.get()
        held?.takeIf { it.id == id && it.generation == current }?.city
            ?: settle(id).also { held = Settled(current, id, it) }
    }

    /** Forgets what was settled, so the next [of] settles again. */
    fun forget() {
        generation.incrementAndGet()
    }

    /**
     * Forgets what was settled when [refresh] put a new catalogue in force.
     *
     * Only then: an unchanged or failed refresh leaves the catalogue in force,
     * and the verdict drawn from it, exactly as they were.
     *
     * @return [refresh], for the caller to go on with.
     */
    fun after(refresh: CatalogueRefresh): CatalogueRefresh {
        if (refresh is CatalogueRefresh.Updated) forget()
        return refresh
    }

    private class Settled(val generation: Int, val id: String, val city: ActiveCity)
}
