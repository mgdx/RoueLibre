package io.github.mgdx.rouelibre.data.cities

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * The downloaded catalogue held on the device, and what the server said of it.
 *
 * Two files in [directory]: the document itself, and the validators the
 * response carried with it. They are written and forgotten together, and that
 * is the whole point of gathering them in one class — validators describing a
 * document the device no longer holds would have the server answer "nothing
 * changed" about a copy that cannot be shown.
 *
 * No Android import: files are the whole of the work, which is what lets that
 * rule be tested on the JVM (SPEC §14).
 */
internal class CatalogueCache(private val directory: File) {

    private val documentFile: File
        get() = File(directory, DOCUMENT_NAME)

    private val validatorsFile: File
        get() = File(directory, VALIDATORS_NAME)

    /** The catalogue held, or `null` if there is none or it cannot be read. */
    fun document(): String? = try {
        documentFile.takeIf { it.isFile }?.readText()
    } catch (_: IOException) {
        null
    }

    /**
     * What to ask the server a conditional question with, or `null` if there is
     * nothing to ask it with.
     *
     * An unreadable file answers `null` rather than failing: the cost is one
     * full download, against an application that would otherwise not start.
     */
    fun validators(): CatalogueValidators? {
        val file = validatorsFile
        if (!file.isFile) return null
        return try {
            json.decodeFromString<CatalogueValidators>(file.readText())
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        }
    }

    /**
     * Replaces the catalogue held, and the validators that describe it.
     *
     * @param etag what the response's `ETag` header carried, if any.
     * @param lastModified what its `Last-Modified` header carried, if any.
     * @return `false` if the document could not be written. The previous one
     *   and its own validators are then left as they were, since that copy is
     *   still what a later launch will read.
     */
    fun keep(document: String, etag: String?, lastModified: String?): Boolean {
        if (!write(document)) return false
        // Forgetting matters as much as keeping: validators left over from an
        // earlier document would be offered for this one, and the server would
        // answer `304` for a copy that is no longer on the device.
        val kept = CatalogueValidators(
            // Blank counts as absent: a header sent empty carries nothing to ask
            // a question with, and offering it back would have the host compare
            // its document against the empty string.
            etag = etag?.takeIf { it.isNotBlank() },
            lastModified = lastModified?.takeIf { it.isNotBlank() },
        )
        if (kept.isEmpty) {
            validatorsFile.delete()
            return true
        }
        try {
            validatorsFile.writeText(json.encodeToString(kept))
        } catch (_: IOException) {
            // Only an optimisation lost: the next refresh downloads the whole
            // document, exactly as every refresh did before this file existed.
            validatorsFile.delete()
        }
        return true
    }

    private fun write(contents: String): Boolean {
        val staging = File(directory, "$DOCUMENT_NAME.partial")
        return try {
            staging.writeText(contents)
            // Atomic rename: a cut in the middle of the write leaves the
            // previous catalogue intact rather than a half-written file.
            staging.renameTo(documentFile).also { renamed -> if (!renamed) staging.delete() }
        } catch (_: IOException) {
            staging.delete()
            false
        }
    }

    private companion object {
        const val DOCUMENT_NAME = "catalogue.json"
        const val VALIDATORS_NAME = "catalogue-validators.json"

        /** Reads the validators alone, whose shape this file owns. */
        val json = Json
    }
}

/**
 * What the publication host said about the catalogue it handed over, so the
 * next request can ask for the document only if it has changed since.
 *
 * Both fields are optional because a host is free to publish neither; the
 * fields are kept as the server wrote them, opaque strings that go back out
 * unexamined.
 */
@Serializable
internal data class CatalogueValidators(
    val etag: String? = null,
    val lastModified: String? = null,
) {
    /** Whether there is anything here to ask a conditional question with. */
    val isEmpty: Boolean
        get() = etag == null && lastModified == null
}
