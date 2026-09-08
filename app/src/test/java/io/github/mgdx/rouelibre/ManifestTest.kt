package io.github.mgdx.rouelibre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What the manifest and the rules it names promise about data leaving the
 * device (SPEC §2, C3; SPEC §8).
 *
 * These are files rather than code, and Android reads them from the resources,
 * so nothing on the JVM can be asked what the application does with them. The
 * files themselves are read instead, the way `LocalesTest` reads
 * `locales_config.xml`: what is checked is that they still say what they were
 * written to say. The behaviour they buy is checked on a telephone.
 */
class ManifestTest {

    private companion object {
        /**
         * Every place Android knows how to extract data from.
         *
         * The four `device_` ones are the direct-boot storage, which this
         * application does not use; they are named all the same, because a
         * rule covering "everything" has to name everything or it covers what
         * happens to exist today.
         */
        private val DOMAINS = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
    }

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /** The manifest, sibling of the resource directory the build names. */
    private val manifest = resources.resolveSibling("AndroidManifest.xml")

    private fun rules(name: String): Element = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(resources.resolve("xml/$name.xml"))
        .documentElement

    private fun Element.sections(name: String): List<Element> = (0 until childNodes.length)
        .map { childNodes.item(it) }
        .filterIsInstance<Element>()
        .filter { it.tagName == name }

    private fun Element.domainsOf(tag: String): Set<String> =
        sections(tag).map { it.getAttribute("domain") }.toSet()

    /**
     * Every section excludes every domain and includes nothing.
     *
     * A section naming an inclusion is the one thing that would make this file
     * lie by understatement: what is not excluded is carried away.
     */
    private fun assertNothingLeaves(section: Element) {
        assertEquals(
            "${section.tagName} does not exclude every domain",
            DOMAINS,
            section.domainsOf("exclude"),
        )
        assertTrue(
            "${section.tagName} includes something",
            section.sections("include").isEmpty(),
        )
    }

    @Test
    fun `nothing of the application is backed up or transferred`() {
        // `android:allowBackup="false"` answers for the cloud alone: from
        // Android 12 on, and on the devices of some manufacturers, the
        // device-to-device transfer runs whatever it says. The two places
        // somebody names for themselves (SPEC §7.6) are in the settings, and
        // SPEC §8 has them going nowhere.
        val extraction = rules("data_extraction_rules")
        val sections = extraction.sections("cloud-backup") + extraction.sections("device-transfer")

        assertEquals("both sections are declared", 2, sections.size)
        sections.forEach(::assertNothingLeaves)
        // Android 11 and below read this second file instead.
        assertNothingLeaves(rules("backup_rules"))
    }

    @Test
    fun `the backup rules carry nothing of the template they started as`() {
        // Android Studio writes both files as commented-out samples with a
        // TODO in them. In this repository a file nobody wrote is a file
        // nobody read either.
        listOf("data_extraction_rules", "backup_rules").forEach { name ->
            val text = resources.resolve("xml/$name.xml").readText()
            assertFalse("$name still holds the template's TODO", text.contains("TODO"))
        }
    }

    @Test
    fun `the activity claims no task affinity`() {
        // Left unwritten, the affinity is the package name, which any
        // application may declare as its own and reparent itself into. What a
        // counterfeit screen would be shown here is a home address (SPEC §7.6).
        assertTrue(
            "MainActivity does not empty android:taskAffinity",
            manifest.readText().contains("""android:taskAffinity=""""),
        )
    }
}
