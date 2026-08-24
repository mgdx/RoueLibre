package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the screens say when a refresh fails for want of a connection
 * (SPEC §4.1, §14).
 *
 * There is one sentence for that failure and there are two states behind it.
 * Over counters that are merely old, "No connection. The last known
 * availability stays on screen." is true and is the whole answer. On a fresh
 * install whose first refresh never got through it was a promise of something
 * the reader had never had: an empty map, a pill reading "Never updated", and
 * a banner announcing the last known availability underneath it.
 *
 * The distinction was already made by the data — the pill is written from
 * `state.fetchedAt` and says "Never updated" on its own — and only the banner
 * failed to make it. `DataError.toUserMessage` now takes it as a parameter,
 * and this holds the second sentence to what makes it worth having.
 *
 * The files are read from the disk, as `LocalesTest` and `DownloadWordingTest`
 * read them: what is checked is what the application will be built with, and
 * no Android runtime is involved (SPEC §14).
 */
class OfflineWordingTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /**
     * A string missing from a started file is a string that language reads
     * nowhere, translated or not (SPEC §9) — and one `lint` fails the build on.
     */
    @Test
    fun `every language carries the sentence for an empty cache`() {
        startedFiles().forEach { folder ->
            assertTrue(
                "$EMPTY_CACHE is missing from ${folder.name}",
                File(folder, "strings.xml").readText()
                    .contains("""<string name="$EMPTY_CACHE">"""),
            )
        }
    }

    /**
     * The defect itself: one sentence answered both states. A translation that
     * copies the other one back over this one puts it straight back.
     */
    @Test
    fun `no language answers both states with one sentence`() {
        startedFiles().forEach { folder ->
            assertNotEquals(
                "${folder.name} says the same thing either way",
                stringOf(folder.name, WITH_CACHE),
                stringOf(folder.name, EMPTY_CACHE),
            )
        }
    }

    /**
     * What the sentence may not do, in the two languages this repository is
     * written and read in: promise availability on screen. Nothing was ever
     * received, so there is nothing to keep showing.
     */
    @Test
    fun `the sentence for an empty cache promises nothing on screen`() {
        val promises = mapOf(
            "values" to listOf("last known", "stays on screen"),
            "values-fr" to listOf("connues", "restent affichées"),
        )
        promises.forEach { (folder, forbidden) ->
            val sentence = stringOf(folder, EMPTY_CACHE)
            assertEquals(
                "$folder promises availability nobody ever received",
                emptyList<String>(),
                forbidden.filter { sentence.contains(it, ignoreCase = true) },
            )
        }
    }

    /** The folders that hold a `strings.xml`, `values/` included. */
    private fun startedFiles(): List<File> = resources.listFiles().orEmpty()
        .filter { it.isDirectory && File(it, "strings.xml").isFile }
        .sortedBy { it.name }

    private fun stringOf(folder: String, name: String): String {
        val file = File(resources, "$folder/strings.xml")
        val declaration = Regex("""<string name="$name">(.*?)</string>""")
            .find(file.readText())
        checkNotNull(declaration) { "$name is not declared in ${file.path}" }
        return declaration.groupValues[1]
    }

    private companion object {
        /** Offline over counters that are merely old, the ordinary case. */
        const val WITH_CACHE = "error_offline"

        /** Offline with nothing ever received, which is a different sentence. */
        const val EMPTY_CACHE = "error_offline_no_availability"
    }
}
