package io.github.mgdx.rouelibre.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The settings keep the place they were left at (SPEC §7.6).
 *
 * The screen is the longest in the application and it opens three others — the
 * city, the offline data, "about" — each of which replaces it and so destroys
 * its views. What brings the reader back where they were is Android's own view
 * state, and Android saves the state of a view **only when that view has an
 * id**: a scrolling container without one is skipped in silence, and the
 * settings came back at the top every time.
 *
 * The check is on the layout file rather than on a running screen because that
 * is where the omission lives, and because an id is deleted as easily as it is
 * added — a rename, a container swapped for another. No Android runtime is
 * involved, which is what keeps this on the JVM (SPEC §14).
 */
class SettingsScrollPositionTest {

    /**
     * `app/src/main/res`, handed over by the build. The property is named after
     * the locales test that first asked for the resource directory; it points
     * at the whole of `res`, not at the languages — see `app/build.gradle.kts`.
     */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    @Test
    fun `everything that scrolls on the settings screen carries an id`() {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(resources.resolve("layout/fragment_settings.xml"))

        val elements = document.getElementsByTagName("*")
        val scrollingViews = (0 until elements.length)
            .map { elements.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName.endsWith("ScrollView") }

        assertTrue("The settings screen has stopped scrolling", scrollingViews.isNotEmpty())
        scrollingViews.forEach { view ->
            assertTrue(
                "${view.tagName} needs an id for Android to save its scroll position",
                view.getAttribute("android:id").isNotEmpty(),
            )
        }
    }
}
