package io.github.mgdx.rouelibre.ui

import io.github.mgdx.rouelibre.core.measure.UnitSystem
import io.github.mgdx.rouelibre.core.measure.writeDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/**
 * The languages the interface offers, and what follows the language it speaks
 * (SPEC §7.6, §9).
 */
class LocalesTest {

    private companion object {
        /**
         * A language the interface does not speak, and is not going to.
         *
         * These fixtures stood on German until German was translated, which is
         * the trouble with borrowing a real target language for the part: it
         * gets finished. They then stood on Greek, on the stated ground that
         * no network in the catalogue is served in a Greek-speaking
         * conurbation — which was simply false. Nicosia is served, Cyprus is
         * Greek-speaking, and SPEC §9 therefore owed Greek a started file all
         * along; `values-el/` now exists.
         *
         * Latin is the answer to both mistakes. No conurbation publishes its
         * stations in it, so no started file will ever be owed to it (SPEC §9),
         * and nobody will finish translating into it. The claim this constant
         * rests on is one that cannot go stale — which is the only kind worth
         * writing into a fixture. The test below still fails, naming this
         * constant, if a `values-la/` ever appears.
         */
        private val UNSPOKEN: Locale = Locale.forLanguageTag("la")

        /**
         * The share of strings that may come back identical to the English
         * before a file is read as untranslated rather than as coincidentally
         * alike. `tools/check_translations.py` draws it at the same place.
         */
        private const val UNTRANSLATED_SHARE = 0.15
    }

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
        assertFalse(
            "UNSPOKEN stands for a language with no folder, and ${UNSPOKEN.language} now has one",
            UNSPOKEN.language in started,
        )
        // Which folders hold English is read from the files themselves rather
        // than from a list of names written here. A list would have to be
        // repointed every time a translation is finished — it named German,
        // Spanish and Japanese until they were — and repointing a fixture is
        // exactly the moment somebody quietly drops the check instead.
        for (language in started) {
            val english = holdsTheEnglishText(language)
            if (language in offered) {
                assertFalse(
                    "$language is offered, so its file may not still hold the English text",
                    english,
                )
            } else {
                assertTrue(
                    "$language is translated but not offered: TRANSLATED_LANGUAGES was forgotten",
                    english,
                )
            }
        }
    }

    /**
     * Whether a language's file is still the starting point it was copied as.
     *
     * Read as the share of strings that are byte-identical to the English —
     * a real translation lands near zero, and never at zero, since "Stations"
     * is the German for "Stations". `tools/check_translations.py` draws the
     * line at the same place and says which strings they are.
     */
    private fun holdsTheEnglishText(language: String): Boolean {
        val english = readStrings(resources.resolve("values/strings.xml"))
        val translated = readStrings(resources.resolve("values-$language/strings.xml"))
        val identical = english.count { (name, value) -> translated[name] == value }
        return identical > UNTRANSLATED_SHARE * english.size
    }

    /** The `<string name="…">…</string>` of a resource file, by name. */
    private fun readStrings(file: File): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

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
     * withdrawn, and English text under a translated heading is not a state to show.
     */
    @Test
    fun `an unknown stored choice follows the system`() {
        assertNull(knownLanguage(UNSPOKEN))
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

    /**
     * A language with no translation of its own reads English words, and the
     * figures that go with them (SPEC §11.13).
     *
     * Their digits are another matter, and the served locale's: see below.
     */
    @Test
    fun `an untranslated language reads English figures`() {
        assertEquals(Locale.ENGLISH, textLocaleFor(UNSPOKEN))
        assertEquals(Locale.ENGLISH, textLocaleFor(null))
    }

    /**
     * A language served with no translation of its own keeps English words and
     * takes the digits of the locale served (SPEC §9).
     *
     * The whole of the repair to the two numbering systems read on one line:
     * Android writes what the resources hold — `%d`, the plurals — in the
     * digits of the configuration's locale, so a device served in Arabic
     * digits was showing "٢٠ docks" beside a "1.2 km" that Kotlin had written
     * in Latin ones. The words stay English wherever the folder holds the
     * English text; the figures no longer disagree with the ones beside them.
     *
     * **The case is read on [UNSPOKEN] rather than on `ar`, which used to
     * carry it.** Arabic was the example while `values-ar/` still held English,
     * and it is translated since: an example that a later wave can translate
     * out from under the test says nothing durable. A language with no folder
     * at all cannot stop standing for one that has no translation.
     */
    @Test
    fun `an untranslated language keeps English words and the served digits`() {
        val figures = textLocaleFor(UNSPOKEN, "arab")

        assertEquals(Locale.ENGLISH.language, figures.language)
        assertEquals("٢٠", NumberFormat.getIntegerInstance(figures).format(20))
        // The digits alone: the separator between them is the numbering
        // system's own, and this test is about the figures.
        assertEquals(
            "١٢",
            writeDistance(1_240.0, UnitSystem.Metric, figures).amount.filter { it.isDigit() },
        )
    }

    /**
     * English and French are written exactly as they were before the digits
     * became the served locale's.
     *
     * The trap of this repair, and the reason the Latin case returns the plain
     * language rather than one carrying `-u-nu-latn`: the two format the same
     * figures, but a locale carrying the extension is no longer equal to the
     * one it was built from, and everything reading the language off it would
     * have had to be checked one by one.
     */
    @Test
    fun `Latin digits leave the language exactly as it was`() {
        assertEquals(Locale.FRENCH, textLocaleFor(Locale.FRENCH, "latn"))
        assertEquals(Locale.ENGLISH, textLocaleFor(Locale.ENGLISH, "latn"))
        assertEquals(Locale.ENGLISH, textLocaleFor(UNSPOKEN, "latn"))
        assertEquals(Locale.CANADA_FRENCH, textLocaleFor(Locale.CANADA_FRENCH, "latn"))
    }

    /** And the figures they write are the same, digit for digit. */
    @Test
    fun `the figures English and French write do not move`() {
        assertEquals("1,2 km", written(textLocaleFor(Locale.FRENCH, "latn")))
        assertEquals("1.2 km", written(textLocaleFor(Locale.ENGLISH, "latn")))
        // A device set to a language with no translation and Latin digits reads
        // English text, and English figures with it, as it always has.
        assertEquals("1.2 km", written(textLocaleFor(UNSPOKEN, "latn")))
    }

    /** A distance as the interface shows it, unit included. */
    private fun written(locale: Locale): String =
        writeDistance(1_240.0, UnitSystem.Metric, locale).amount + " km"

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
