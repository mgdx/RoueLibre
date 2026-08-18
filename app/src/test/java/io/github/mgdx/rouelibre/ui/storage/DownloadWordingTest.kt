package io.github.mgdx.rouelibre.ui.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the storage screen says when a dataset transfer fails (SPEC §4.4, §14).
 *
 * It reported an interrupted download with the sentence written for the
 * availability refresh — "No connection. The last known availability stays on
 * screen." — read under the dataset's own name, "Map data: …". That answered
 * about bikes somebody who had just pressed "Download 35.0 MB", and said
 * nothing of the transfer they were watching. The two failures have their own
 * sentence now, and this test is what keeps one from being written back over
 * the other.
 *
 * The files are read from the disk, as `LocalesTest` reads the locale folders:
 * what is checked is what the application will be built with, and no Android
 * runtime is involved (SPEC §14).
 */
class DownloadWordingTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    @Test
    fun `a failed download does not borrow the availability refresh's sentence`() {
        listOf("values", "values-fr").forEach { folder ->
            assertNotEquals(
                "$folder answers a failed download with the refresh's sentence",
                stringOf(folder, "error_offline"),
                stringOf(folder, "error_offline_download"),
            )
        }
    }

    /**
     * The defect itself, and not merely the fact that two strings differ: what
     * made the message wrong is that it spoke of availability on a screen that
     * downloads files.
     */
    @Test
    fun `the sentence for a failed download speaks of no availability`() {
        assertFalse(
            stringOf("values", "error_offline_download").contains("availability", true),
        )
        assertFalse(
            stringOf("values-fr", "error_offline_download").contains("disponibilit", true),
        )
    }

    /**
     * A string missing from a started file is a string that language reads
     * nowhere, translated or not (SPEC §9).
     */
    @Test
    fun `every language carries the sentence`() {
        resources.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "strings.xml").isFile }
            .forEach { folder ->
                assertTrue(
                    "error_offline_download is missing from ${folder.name}",
                    File(folder, "strings.xml").readText().contains(DECLARATION),
                )
            }
    }

    private fun stringOf(folder: String, name: String): String {
        val file = File(resources, "$folder/strings.xml")
        val declaration = Regex("""<string name="$name">(.*?)</string>""")
            .find(file.readText())
        checkNotNull(declaration) { "$name is not declared in ${file.path}" }
        return declaration.groupValues[1]
    }

    private companion object {
        const val DECLARATION = """<string name="error_offline_download">"""
    }
}
