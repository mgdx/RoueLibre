package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The room the welcome screen gives its paragraph, and what the title does
 * with the line it is given (SPEC §7.9, SPEC §14).
 *
 * Both were defects seen on a Fairphone 3 with the system text size at ×2.0,
 * and both are silent on any smaller setting — which is why they lived through
 * several passes over this screen.
 *
 * The paragraph was written over the step label, over the button and over the
 * link below it. Its container is a scrolling one and it never scrolled: a
 * container measured as `wrap` asks for the height of its content, capped at
 * the height of the layout it is measured in, and a chain that no longer fits
 * does not shrink — it spills over what comes after it. Giving the container
 * and the drawing a layout of their own is what turns that cap into the room
 * they actually have, and the padding of that layout is what the cap is
 * measured against, which a margin would not be.
 *
 * The geometry itself is measured on a device, by `TextSizeLayoutTest`. What
 * is held here is the reasoning the geometry rests on, so that a hand undoing
 * it is told what it is undoing rather than finding out on a phone nobody
 * tests at ×2.0. The file is read from the disk, as `WelcomeFleetMarksTest`
 * reads it: no Android runtime is involved (SPEC §14).
 */
class WelcomeBodyRoomTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private val welcomeLayout by lazy {
        File(resources, "layout/fragment_welcome.xml").readText()
    }

    /**
     * The attributes of the view declaring [identity], up to [end].
     *
     * A view whose identity is gone from the layout is a failed assertion and
     * not an exception thrown while cutting the file up, so that what a run
     * reports is the thing that is missing.
     */
    private fun attributesOf(identity: String, end: String): String {
        val declared = welcomeLayout.indexOf(identity)
        assertTrue("The layout still declares $identity", declared > 0)
        return welcomeLayout.substring(declared, welcomeLayout.indexOf(end, declared))
    }

    /**
     * The text and the drawing share a layout of their own, and the container
     * that scrolls is inside it.
     *
     * That is the whole of the fix: a widget measured as `wrap` is measured
     * against the height of the layout that holds it. Held straight in the
     * screen, the paragraph was capped at the screen; held in the room below
     * the title, it is capped at that room.
     */
    @Test
    fun `the text and the drawing are held in a room of their own`() {
        val room = welcomeLayout.indexOf("""android:id="@+id/page_body"""")
        assertTrue("The page has a room for the text and the drawing", room > 0)

        val opened = welcomeLayout.lastIndexOf(
            "<androidx.constraintlayout.widget.ConstraintLayout",
            room,
        )
        val closed = welcomeLayout.indexOf(
            "</androidx.constraintlayout.widget.ConstraintLayout>",
            room,
        )
        for (held in listOf("""@+id/body_container"""", """@+id/illustration"""")) {
            assertTrue(
                "$held stands in the room, whose height is what caps the text",
                welcomeLayout.indexOf(held) in opened until closed,
            )
        }
    }

    /**
     * The room spaces itself with padding, and that is not a matter of taste.
     *
     * Padding is taken off the height its children are measured against; a
     * margin is not. Written as margins, the container would be measured
     * against a height it cannot have, and would go back to claiming more room
     * than there is.
     */
    @Test
    fun `the room keeps its spacing as padding`() {
        val room = attributesOf("""android:id="@+id/page_body"""", ">")
        for (spacing in listOf("android:paddingTop", "android:paddingBottom")) {
            assertTrue("The room spaces itself with $spacing", spacing in room)
        }
        for (spacing in listOf("android:layout_marginTop", "android:layout_marginBottom")) {
            assertTrue("The room does not space itself with $spacing", spacing !in room)
        }
    }

    /**
     * The title refuses the justification the theme gives every text view.
     *
     * Justifying spreads a line bank to bank, and reaching the far bank is
     * worth breaking a word for: at ×2.0 "Une ville, un téléchargement" came
     * out as "Une ville, un téléchar" filling the line to the pixel, with
     * "gement" underneath — a title that reads as cut off at the edge of the
     * screen. The settings screen's headings refuse it for the same reason.
     */
    @Test
    fun `the title is not justified`() {
        val title = attributesOf("""android:id="@+id/title"""", "/>")
        assertTrue(
            "The title is left unjustified, so a word too wide stays whole on its own line",
            """android:justificationMode="none"""" in title,
        )
    }
}
