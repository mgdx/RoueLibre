package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The languages the interface offers, and what follows the language it speaks
 * (SPEC §7.6, §9).
 */
class LocalesTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /**
     * The chooser is derived from the translations that exist, and from nothing
     * else.
     *
     * Written as an equality rather than as a list of expected names on
     * purpose: it is what fails the day somebody offers a language by writing
     * it out a second time somewhere, since the new entry would then be in one
     * of the two sets and not the other.
     */
    @Test
    fun `the languages offered are the translated ones and nothing else`() {
        assertEquals(
            TRANSLATED_LANGUAGES,
            offeredLanguages().map { it.language }.toSet(),
        )
    }

    /**
     * A `values-xx/` folder is no proof of a translation: most of the thirty in
     * this repository still hold the English text, and offering their language
     * would be offering English under another name.
     */
    @Test
    fun `a language whose folder exists but holds English is not offered`() {
        val started = resources.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .map { it.name.removePrefix("values-") }
            // values-night and values-v31 qualify the resources, not a language.
            .filter { it.length == 2 }
            .toSet()
        val offered = offeredLanguages().map { it.language }.toSet()

        // English excepted: it has no folder of its own, being `values/`, the
        // file with no qualifier that Android serves when nothing matches.
        assertTrue(
            "Every language offered needs its own folder",
            started.containsAll(offered - BASE_LOCALE.language),
        )
        assertTrue(
            "The repository holds started files beyond its translations, and this test is " +
                "about them",
            started.size > offered.size,
        )
        // Three of them, named so that the day one is translated the failure
        // says which list was forgotten rather than merely that a count moved.
        for (language in listOf("de", "es", "ja")) {
            assertTrue("$language is a started file", language in started)
            assertFalse("$language still holds the English text", language in offered)
        }
    }

    /**
     * Android's per-application language settings offer the same list.
     *
     * `res/xml/locales_config.xml` is the one place that list is written twice
     * — Android reads it from the APK's resources, so it cannot be computed —
     * and a language offered by the system but not by the settings screen, or
     * the other way round, would read as a defect.
     */
    @Test
    fun `Android's own language settings offer the same languages`() {
        val declared = Regex("""android:name="([^"]+)"""")
            .findAll(resources.resolve("xml/locales_config.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(TRANSLATED_LANGUAGES, declared)
    }

    /** Each language is offered under its own name for itself, never translated. */
    @Test
    fun `a language is offered under its own name`() {
        assertEquals("English", Locale.ENGLISH.endonym())
        assertEquals("Français", Locale.FRENCH.endonym())
    }

    /** Absent, the choice is the system's — which is what the application did before. */
    @Test
    fun `no stored choice follows the system`() {
        assertNull(knownLanguage(null))
    }

    /**
     * So is a choice naming a language the interface does not speak: Android's
     * per-application picker can leave one behind when a translation is
     * withdrawn, and English text under a German heading is not a state to show.
     */
    @Test
    fun `an unknown stored choice follows the system`() {
        assertNull(knownLanguage(Locale.GERMAN))
        assertNull(knownLanguage(Locale.forLanguageTag("und")))
        assertEquals(Locale.FRENCH, knownLanguage(Locale.FRENCH))
    }

    /**
     * Figures follow the language on the screen, not the one the device is set
     * to (SPEC §9).
     *
     * The system's language is put somewhere else entirely for the length of
     * this test: what the reading answers must come from what was handed to it,
     * which on the device is the configuration the chosen language was applied
     * to.
     */
    @Test
    fun `figures follow the language displayed, not the system's`() {
        val system = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals(Locale.FRENCH, textLocaleFor(Locale.FRENCH))
            assertEquals(Locale.ENGLISH, textLocaleFor(Locale.ENGLISH))
            assertNotEquals(Locale.getDefault(), textLocaleFor(Locale.FRENCH))
        } finally {
            Locale.setDefault(system)
        }
    }

    /** A language with no translation of its own reads English figures (SPEC §11.13). */
    @Test
    fun `an untranslated language reads English figures`() {
        assertEquals(Locale.ENGLISH, textLocaleFor(Locale.GERMAN))
        assertEquals(Locale.ENGLISH, textLocaleFor(null))
    }

    /**
     * The units are read from a region, and the language offered carries none
     * (SPEC §7.6, §9).
     *
     * This is the guard on the trap of this chantier: choosing a language puts
     * a bare tag — `fr`, never `fr-FR` — at the head of the process's locale
     * list, so units read off the language the interface speaks could only ever
     * answer "metric", and somebody in Boston who put the interface into French
     * would have lost their miles to a setting about words. `regionUnitSystem`
     * therefore reads the device's own configuration, which no per-application
     * language overrides. What this test pins is the shape of that reading: a
     * region decides, and no language names one.
     */
    @Test
    fun `the language chosen names no region for the units to read`() {
        for (language in offeredLanguages()) {
            assertEquals("A language offered must carry no country", "", language.country)
        }
    }
}
