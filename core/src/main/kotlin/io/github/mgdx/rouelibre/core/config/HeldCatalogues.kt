package io.github.mgdx.rouelibre.core.config

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * A catalogue as the device holds it: its cities, and the networks it withdrew.
 *
 * The two are read apart by [CityCatalogueReader] because most callers want
 * only one of them; deciding which catalogue speaks for a network needs both.
 */
public data class HeldCatalogue(
    public val catalogue: CityCatalogue,
    public val withdrawnCities: List<WithdrawnCity>,
) {
    /** Whether this catalogue names [id] at all, served or withdrawn. */
    internal fun names(id: String): Boolean =
        catalogue.entry(id) != null || withdrawnCities.any { it.id == id }
}

/**
 * The two catalogues a device can hold, and which of them decides (SPEC §15.1).
 *
 * **The more recent one decides, by its `generatedAt`.** The downloaded copy
 * lives in the application's files and outlives an update, so the build just
 * installed often ships a catalogue newer than the copy it finds — and taking
 * that copy because it was downloaded hid every city added since, Oslo and
 * Trondheim among them, from a phone whose APK carried their configuration. It
 * also made a network brought back read as withdrawn, the older copy still
 * listing it so. Being downloaded proves nothing about being current; the date
 * the catalogue carries does.
 *
 * A newer downloaded copy still replaces the shipped one entirely, without a
 * union of the two: removing a network from the published catalogue remains the
 * lever that retires it without a release.
 *
 * @param downloaded the copy held in the application's files, `null` when
 *   there is none or it cannot be read.
 * @param shipped the copy in the APK, which is always there.
 */
public class HeldCatalogues(downloaded: HeldCatalogue?, shipped: HeldCatalogue) {
    /** True when the downloaded copy is the one in force. */
    public val isDownloadedInForce: Boolean = downloaded != null &&
        downloadedCatalogueOutranks(downloaded.catalogue.generatedAt, shipped.catalogue.generatedAt)

    /** The catalogue that decides: the list shown, and what becomes of a network. */
    public val inForce: HeldCatalogue = if (isDownloadedInForce) {
        checkNotNull(
            downloaded,
        )
    } else {
        shipped
    }

    /** The one set aside, consulted only for the name of a network the other ignores. */
    private val outranked: HeldCatalogue? = if (isDownloadedInForce) shipped else downloaded

    /**
     * Where the city [id] stands, between not chosen, served and gone.
     *
     * - **Listed among the cities of the catalogue in force**, it is not
     *   withdrawn, whatever an older catalogue says: it was brought back. Served
     *   if this build carries its configuration; otherwise a newer version of
     *   the application serves it, and that is what is said, as the city list
     *   says of its refused rows.
     * - **Listed as withdrawn by the catalogue in force**, it is gone, even if
     *   this build still carries its configuration: retiring a network waits
     *   for no release, and a build kept querying a feed that answers nothing.
     * - **Named by neither list of the catalogue in force**, the configuration
     *   decides, as it did before withdrawals were published. A build that
     *   cannot serve it says it is gone, under the name the other catalogue
     *   gave it if it gave one.
     *
     * @param id the city the settings name, `null` if none.
     * @param configuration its configuration in this build, `null` if there is none.
     */
    public fun resolve(id: String?, configuration: CityConfiguration?): ActiveCity {
        if (id == null) return ActiveCity.None
        val entry = inForce.catalogue.entry(id)
        val withdrawal = inForce.withdrawnCities.firstOrNull { it.id == id }
        return when {
            entry != null && configuration != null -> ActiveCity.Served(configuration)
            entry != null -> ActiveCity.NoLongerServed(
                id,
                withdrawal = null,
                servedByNewerVersion = entry,
            )
            withdrawal != null -> ActiveCity.NoLongerServed(id, withdrawal)
            configuration != null -> ActiveCity.Served(configuration)
            else -> ActiveCity.NoLongerServed(id, outrankedName(id))
        }
    }

    /**
     * The networks served once and no more, for the city list to offer their
     * data for deletion.
     *
     * Those the catalogue in force withdrew, first; then those only the other
     * catalogue withdrew, when the catalogue in force names them nowhere and
     * this build cannot serve them — the same networks [resolve] would call
     * gone, under the same names.
     *
     * @param servable the cities this build carries a configuration for.
     */
    public fun withdrawnCities(servable: Set<String>): List<WithdrawnCity> {
        val older = outranked?.withdrawnCities.orEmpty()
            .filter { !inForce.names(it.id) && it.id !in servable }
        return (inForce.withdrawnCities + older).distinctBy { it.id }
    }

    /**
     * How the catalogue set aside named [id], withdrawn or still served, or
     * `null` when it did not name it either.
     */
    private fun outrankedName(id: String): WithdrawnCity? {
        val other = outranked ?: return null
        return other.withdrawnCities.firstOrNull { it.id == id }
            ?: other.catalogue.entry(id)?.let { WithdrawnCity(it.id, it.displayName, it.mainCity) }
    }
}

/**
 * Whether a downloaded catalogue produced at [downloadedGeneratedAt] outranks
 * the shipped one produced at [shippedGeneratedAt].
 *
 * On a tie the downloaded copy wins: it is then the same publication, and
 * keeping it in force is what lets its validators be offered and a `304` spare
 * the download. A date that cannot be read counts as older than any that can,
 * since every catalogue `tools/build_catalogue.py` ever produced is dated; when
 * neither can be read, the downloaded copy wins, as it always did.
 */
public fun downloadedCatalogueOutranks(
    downloadedGeneratedAt: String?,
    shippedGeneratedAt: String?,
): Boolean {
    val downloaded = instantOrNull(downloadedGeneratedAt)
    val shipped = instantOrNull(shippedGeneratedAt) ?: return true
    return downloaded != null && !downloaded.isBefore(shipped)
}

private fun instantOrNull(text: String?): Instant? = try {
    text?.let(Instant::parse)
} catch (_: DateTimeParseException) {
    null
}
