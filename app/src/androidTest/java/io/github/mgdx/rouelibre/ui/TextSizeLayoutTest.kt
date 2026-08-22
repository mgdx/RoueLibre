package io.github.mgdx.rouelibre.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.widget.NestedScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.mgdx.rouelibre.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that no label on the settings screen — nor on the two other screens
 * carrying a row of choices — is cut off at any text size the system offers
 * (SPEC §7).
 *
 * The defect this stands against is silent: a label reaches the edge of its
 * button and Android writes as much of it as fits, followed by an ellipsis.
 * Nothing crashes, nothing logs, and at ×2.0 "Mécanique" and "Électrique" both
 * came out as seven letters and three dots — two choices that decide a journey,
 * told apart at the third letter. Only a measurement catches it, which is what
 * this test is: the screen is inflated at each step of the system's own text
 * size slider, laid out at the width of the device running the test, and every
 * label is then asked what it managed to write.
 *
 * A label passes on three counts, all three of which have been seen to fail on
 * the settings screen: it is not ellipsized, it is laid out to its last
 * character, and no line of it ends in the middle of a word. The third is the
 * one a naive check misses — Android breaks a word too wide for its line
 * between two letters and with no hyphen, so "Mécanique" over "Mécaniq / ue"
 * reports no ellipsis at all while being just as unreadable.
 *
 * The text size is forced through the configuration of the context the views
 * are built from, not through the device's own setting: a test that wrote to
 * `Settings.System` would change the phone for whatever ran next, and would
 * need a permission it has no business holding.
 */
@RunWith(AndroidJUnit4::class)
class TextSizeLayoutTest {

    @Test
    fun themeRowKeepsItsLabelsWhole() {
        assertLabelsWhole(R.layout.fragment_settings, R.id.theme)
    }

    @Test
    fun unitsRowKeepsItsLabelsWhole() {
        assertLabelsWhole(R.layout.fragment_settings, R.id.units)
    }

    @Test
    fun openingScreenRowKeepsItsLabelsWhole() {
        assertLabelsWhole(R.layout.fragment_settings, R.id.opening_screen)
    }

    @Test
    fun ownBikeKindRowKeepsItsLabelsWhole() {
        assertLabelsWhole(R.layout.fragment_settings, R.id.own_bike_kind)
    }

    @Test
    fun walkingPaceRowKeepsItsLabelsWhole() {
        assertLabelsWhole(R.layout.fragment_settings, R.id.walking_pace)
    }

    @Test
    fun bikeKindRowKeepsItsLabelsWhole() {
        // The row is hidden until the city is known to lend both kinds of bike
        // (SPEC §7.3), and a hidden row is not measured. It is shown here for
        // the same reason the code shows it: a mixed fleet.
        assertLabelsWhole(R.layout.fragment_journey_search, R.id.bike_kind) { screen ->
            screen.findViewById<View>(R.id.bike_kind).visibility = View.VISIBLE
        }
    }

    @Test
    fun stationListModeRowKeepsItsLabelsWhole() {
        assertLabelsWhole(R.layout.fragment_station_list, R.id.mode_toggle)
    }

    /**
     * The other shape a setting takes: a label on the left and the control it
     * settles on the right, on one line.
     *
     * What must not happen is the label being written under the switch, or
     * shortened to stay clear of it. The line is free to grow taller — it is
     * the height that gives way, never the size of the characters — so the
     * check is that the label writes itself in full and that no line of it
     * reaches the track of its switch.
     *
     * The track and not the text's own box: a text view never lays out wider
     * than the box it was given, so measuring against that box would be
     * measuring nothing. What can be seen, and what would be a defect, is a
     * letter over the switch. A line is allowed the few pixels between the two
     * — at ×1.0 the longest French label overhangs its box by three of them,
     * the right side bearing of a letter, with the width of a finger still
     * between it and the track.
     */
    @Test
    fun switchLabelsStayClearOfTheirSwitch() {
        for (scale in TEXT_SIZE_STEPS) {
            val screen = inflate(R.layout.fragment_settings, scale)
            for (id in SWITCH_IDS) {
                val switch = screen.findViewById<MaterialSwitch>(id)
                val written = switch.layout ?: error("no layout for ${name(screen, id)} at ×$scale")
                assertWritesItsTextWhole(written, "${name(screen, id)} at ×$scale")

                val track = switch.trackDrawable?.intrinsicWidth ?: 0
                val roomBesideTheTrack =
                    switch.width - switch.compoundPaddingLeft - switch.paddingRight - track
                for (line in 0 until written.lineCount) {
                    assertTrue(
                        "${name(screen, id)} at ×$scale writes under its switch",
                        written.getLineWidth(line) <= roomBesideTheTrack.toFloat(),
                    )
                }
            }
        }
    }

    /**
     * The four pages of the welcome screen (SPEC §7.9), at every text size.
     *
     * A page is a title, a paragraph that may need to scroll, a decorative
     * drawing, the step it is at and two buttons. What has to hold is what
     * SPEC §14 asks of every screen: at the largest text size nothing the
     * application writes is covered by anything else, and nothing is cut.
     *
     * Both halves have been seen to fail here, on a Fairphone 3 at ×2.0. The
     * paragraph was written over the step label, over the button and over the
     * link below it — a container measured as `wrap` takes the height of its
     * content, and a chain that no longer fits does not shrink, it spills. And
     * the title, justified, broke "téléchargement" between two letters to
     * reach the far bank, which reads as a title cut off at the edge of the
     * screen.
     *
     * The paragraph is the one text allowed not to be shown whole: it is what
     * the container scrolls. What is asked of it is that it stay inside its
     * room — everything below it is then reachable by scrolling. The texts
     * that cannot scroll are asked to be written to their last character.
     */
    @Test
    fun welcomePagesCoverNothingAndCutNothing() {
        for (page in WELCOME_PAGES) {
            for (scale in TEXT_SIZE_STEPS) {
                val screen = inflate(R.layout.fragment_welcome, scale) { it.showPage(page) }
                val what = "\"${screen.resources.getString(page.title)}\" at ×$scale"

                for (id in WELCOME_UNSCROLLED_TEXTS) {
                    val text = screen.findViewById<TextView>(id)
                    val written = text.layout ?: error("no layout for ${name(screen, id)} at $what")
                    assertWritesItsTextWhole(written, "${name(screen, id)} of $what")
                }

                // Not the paragraph against the step label alone: a page has
                // five things on it and any of them written over another is
                // the same defect. The drawing is in, and on purpose — it is
                // allowed to shrink to nothing, never to be laid over the
                // button.
                val boxes = WELCOME_BOXES
                    .map { it to screen.findViewById<View>(it) }
                    .filter { (_, view) -> view.visibility == View.VISIBLE }
                    .map { (id, view) -> name(screen, id) to boundsIn(view, screen) }
                for ((first, second) in boxes.indices.flatMap { one ->
                    (one + 1 until boxes.size).map { boxes[one] to boxes[it] }
                }) {
                    assertFalse(
                        "${first.first} is written over ${second.first} on $what",
                        Rect.intersects(first.second, second.second),
                    )
                }

                // A room shorter than what it holds has to scroll, and a
                // container of no height at all would satisfy the check above
                // while showing nothing.
                val container = screen.findViewById<NestedScrollView>(R.id.body_container)
                assertTrue("the paragraph has no room on $what", container.height > 0)
            }
        }
    }

    /** Puts one welcome page on the screen, as `WelcomeFragment` does. */
    private fun View.showPage(page: WelcomePage) {
        findViewById<TextView>(R.id.title).setText(page.title)
        findViewById<TextView>(R.id.body).setText(page.body)
        findViewById<View>(R.id.illustration).visibility =
            if (page.marks) View.GONE else View.VISIBLE
        findViewById<View>(R.id.fleet_marks).visibility =
            if (page.marks) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.step).text =
            resources.getString(R.string.welcome_step, 1, WELCOME_PAGES.size)
        findViewById<MaterialButton>(R.id.next).setText(page.next)
        findViewById<MaterialButton>(R.id.skip).setText(page.skip)
    }

    /** Where [view] sits on [root], whatever it is nested inside. */
    private fun boundsIn(view: View, root: View): Rect {
        val box = Rect(0, 0, view.width, view.height)
        var current = view
        while (current !== root) {
            box.offset(current.left, current.top)
            current = current.parent as View
        }
        return box
    }

    /**
     * One page of the welcome screen, as this test needs to know it.
     *
     * The pages are written down a second time here rather than read from
     * `WelcomeFragment`: what is measured is what the reader is shown, so the
     * test states it, and a page added there without a line here would simply
     * go unmeasured rather than quietly change what this test covers.
     */
    private data class WelcomePage(
        val title: Int,
        val body: Int,
        val marks: Boolean,
        val next: Int,
        val skip: Int,
    )

    private fun assertLabelsWhole(
        @LayoutRes screenLayout: Int,
        @IdRes rowId: Int,
        prepare: (View) -> Unit = {},
    ) {
        for (scale in TEXT_SIZE_STEPS) {
            val screen = inflate(screenLayout, scale, prepare)
            // The supertype, and on purpose: what is checked here is what the
            // row writes, not which class writes it. A row that grew its own
            // way of holding a label would be measured just the same.
            val row = screen.findViewById<MaterialButtonToggleGroup>(rowId)
            for (index in 0 until row.childCount) {
                val button = row.getChildAt(index) as MaterialButton
                val written = button.layout ?: error("no layout for ${button.text} at ×$scale")
                assertWritesItsTextWhole(written, "\"${button.text}\" at ×$scale")
            }
        }
    }

    private fun assertWritesItsTextWhole(written: Layout, what: String) {
        val lastLine = written.lineCount - 1
        assertEquals("$what is ellipsized", 0, written.getEllipsisCount(lastLine))
        assertEquals(
            "$what is not written to its end",
            written.text.length,
            written.getLineEnd(lastLine),
        )
        for (line in 0 until lastLine) {
            val lastCharacter = written.text[written.getLineEnd(line) - 1]
            assertTrue(
                "$what breaks a word in two",
                lastCharacter.isWhitespace() || lastCharacter == '-',
            )
        }
    }

    /**
     * Builds a screen at [scale] and lays it out at the size of this device.
     *
     * The inflation runs on the main thread because that is where the views
     * being built expect to be, and the measurement runs with it: a view
     * measured from another thread while the first one is still inflating
     * reads half-built layout parameters.
     */
    private fun inflate(
        @LayoutRes screenLayout: Int,
        scale: Float,
        prepare: (View) -> Unit = {},
    ): View {
        val context = contextAtTextSize(scale)
        lateinit var screen: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            screen = LayoutInflater.from(context).inflate(screenLayout, null, false)
            prepare(screen)
            val metrics = context.resources.displayMetrics
            screen.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            screen.layout(0, 0, screen.measuredWidth, screen.measuredHeight)
        }
        return screen
    }

    private fun contextAtTextSize(scale: Float): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(target.resources.configuration).apply {
            fontScale = scale
        }
        return ContextThemeWrapper(
            target.createConfigurationContext(configuration),
            R.style.Theme_RoueLibre,
        )
    }

    private fun name(screen: View, @IdRes id: Int) = screen.resources.getResourceEntryName(id)

    private companion object {
        /**
         * Every step of Android's text size slider, measured on a Fairphone 3
         * running Android 15: ×2.0 is the largest a user can ask for, and each
         * of the steps below it is a size somebody is reading the screen at.
         * Testing the ladder rather than its top costs nothing and catches the
         * label that fits at ×2.0 only because its row has stacked by then.
         */
        val TEXT_SIZE_STEPS = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f)

        /**
         * The four welcome pages, in the order `WelcomeFragment` shows them:
         * what the application is, what it does not do with your data, how to
         * read the bike it draws, and what it needs in order to work.
         */
        val WELCOME_PAGES = listOf(
            WelcomePage(
                title = R.string.welcome_hello_title,
                body = R.string.welcome_hello_body,
                marks = false,
                next = R.string.welcome_continue,
                skip = R.string.welcome_skip,
            ),
            WelcomePage(
                title = R.string.welcome_privacy_title,
                body = R.string.welcome_privacy_body,
                marks = false,
                next = R.string.welcome_continue,
                skip = R.string.welcome_skip,
            ),
            WelcomePage(
                title = R.string.welcome_fleet_title,
                body = R.string.welcome_fleet_body,
                marks = true,
                next = R.string.welcome_continue,
                skip = R.string.welcome_skip,
            ),
            WelcomePage(
                title = R.string.welcome_data_title,
                body = R.string.welcome_data_body,
                marks = false,
                next = R.string.welcome_choose_city,
                skip = R.string.welcome_later,
            ),
        )

        /**
         * What a welcome page writes outside the container that scrolls, and
         * which therefore has to be written whole.
         */
        val WELCOME_UNSCROLLED_TEXTS = listOf(R.id.title, R.id.step, R.id.next, R.id.skip)

        /** Everything a welcome page draws, none of which may cover another. */
        val WELCOME_BOXES = listOf(
            R.id.title,
            R.id.body_container,
            R.id.illustration,
            R.id.step,
            R.id.next,
            R.id.skip,
        )

        /** The settings that write their label beside their control. */
        val SWITCH_IDS = listOf(
            R.id.hide_out_of_service_stations,
            R.id.hide_empty_stations,
            R.id.download_unmetered_only,
        )
    }
}
