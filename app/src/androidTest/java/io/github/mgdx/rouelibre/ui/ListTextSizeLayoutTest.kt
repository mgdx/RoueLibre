package io.github.mgdx.rouelibre.ui

import android.content.Context
import android.content.res.Configuration
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mgdx.rouelibre.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Checks that no line of a list — a station, an address, a shortcut — and no
 * label laid over a map is cut off at any text size the system offers
 * (SPEC §7).
 *
 * The sibling of [TextSizeLayoutTest], which holds the settings screen and its
 * rows of choices to the same rule. What is measured here is the other shape
 * the defect takes: not a label too wide for the share of a row of buttons, but
 * a line of running text held to one line. At ×2.0 on a Fairphone 3 the station
 * list read "Alfre…", "Anato…", "59260…" — a station's name told from its
 * neighbour's at the fifth letter, which is the very grievance SPEC §7 makes
 * against a truncated availability figure.
 *
 * A line passes on the same three counts as a label: it is not ellipsized, it
 * is laid out to its last character, and no line of it ends in the middle of a
 * word. The third is what catches a column grown too narrow to hold one word of
 * a name: Android breaks a word wider than its line between two letters, with
 * no hyphen, so "Metropole" over "Metropo / le" reports no ellipsis at all.
 *
 * The rows are filled with the longest names the served network actually
 * publishes rather than with invented ones: the defect is about real data, and
 * a fixture shorter than the data would pass while the screen failed.
 *
 * Both languages are measured, because a translation is not a shorter copy:
 * "free docks" is thirteen characters shorter than "places libres", and the
 * detail line's "docks" is "points d'attache".
 */
@RunWith(AndroidJUnit4::class)
class ListTextSizeLayoutTest {

    @Test
    fun stationRowWritesItsNameWhole() {
        assertTextsWhole(R.layout.item_station, R.id.name) { row -> fillStationRow(row) }
    }

    @Test
    fun stationRowWritesItsDetailWhole() {
        assertTextsWhole(R.layout.item_station, R.id.detail) { row -> fillStationRow(row) }
    }

    /**
     * Above the normal text size, the name gets more of the row than everything
     * else on it put together.
     *
     * The sharing rule `StationRow` applies, checked from the outside: what one
     * sees on the screen is not a decision but two blocks, and the defect was
     * that the supporting one had taken the row. A row that has stacked leaves
     * the name the full width, which satisfies this the same way — the rule is
     * about what the name ends up with, not about which arrangement got it
     * there.
     *
     * The normal size is left out because the rule does not run there, which is
     * the whole of [stationRowKeepsItsShapeAtTheNormalTextSize].
     */
    @Test
    fun stationNameGetsHalfTheRowAboveTheNormalTextSize() {
        for (locale in LANGUAGES) {
            for (scale in TEXT_SIZE_STEPS.filter { it > NORMAL_TEXT_SIZE }) {
                for (width in SCREEN_WIDTHS_IN_DP) {
                    val row = inflate(R.layout.item_station, scale, locale, width) {
                        fillStationRow(it)
                    }
                    val name = row.findViewById<View>(R.id.name)
                    val room = row.width - row.paddingStart - row.paddingEnd
                    assertTrue(
                        "at ×$scale in $locale on ${width}dp the name is left ${name.width} " +
                            "of $room",
                        name.width >= room - name.width,
                    )
                }
            }
        }
    }

    /**
     * At the normal text size and below it, the row is arranged as it always
     * was: the count stands beside the name.
     *
     * The other side of the one threshold in this work. The sharing rule of
     * [stationNameGetsHalfTheRowAboveTheNormalTextSize] would move the count
     * below here on the two narrower screens — measured, in French on 360 dp and
     * in both languages on 320 — and it is held back for the reason SPEC §7
     * gives: a reader who has not turned the text size up has asked for nothing,
     * and a row that rearranges itself under their eyes is a regression for
     * them. This is what a test of the rule alone would not see.
     *
     * What is checked here is the **arrangement** and not the number of lines:
     * on 320 dp a name and its detail line did not fit their column at the
     * normal size before this change either, and were written away with an
     * ellipsis. That they now wrap is the repair.
     */
    @Test
    fun stationRowKeepsItsShapeAtTheNormalTextSize() {
        for (locale in LANGUAGES) {
            for (scale in TEXT_SIZE_STEPS.filter { it <= NORMAL_TEXT_SIZE }) {
                for (width in SCREEN_WIDTHS_IN_DP) {
                    val row = inflate(R.layout.item_station, scale, locale, width) {
                        fillStationRow(it)
                    }
                    val name = row.findViewById<View>(R.id.name)
                    val count = row.findViewById<View>(R.id.counterpart_block)
                    assertTrue(
                        "the count left the name's side at ×$scale in $locale on ${width}dp",
                        count.top < name.bottom,
                    )
                }
            }
        }
    }

    /**
     * At the normal text size the station row is the row it always was.
     *
     * The guard against repairing a screen by redrawing it: a name of ordinary
     * length stays on one line, the count stays beside it, and the row is no
     * taller than the indicator it carries. Everything above is about what
     * happens once the reader asks for larger characters; this says that until
     * they do, nothing happens at all.
     *
     * It is pinned on one width, and on the widest, for a reason worth writing
     * down: the narrower screens had the defect at the normal text size too. On
     * 360 dp in French the detail line already ran past its column and was
     * written "59650 · 40 poin…"; on 320 dp so was the name. Those screens have
     * no unchanged state to hold on to, and pinning them here would be pinning
     * the defect. What is pinned is the screen the row was designed on.
     */
    @Test
    fun stationRowIsUnchangedAtTheNormalTextSize() {
        for (locale in LANGUAGES) {
            val row = inflate(R.layout.item_station, 1.0f, locale, WIDEST_SCREEN_IN_DP) { screen ->
                fillStationRow(screen)
                screen.setText(R.id.name, ORDINARY_STATION_NAME)
            }
            val name = row.findViewById<TextView>(R.id.name)
            val detail = row.findViewById<TextView>(R.id.detail)
            val count = row.findViewById<View>(R.id.counterpart_block)
            assertEquals("$ORDINARY_STATION_NAME wraps at ×1.0 in $locale", 1, name.lineCount)
            assertEquals("the detail line wraps at ×1.0 in $locale", 1, detail.lineCount)
            assertTrue(
                "the count left the name's side at ×1.0 in $locale",
                count.top < name.bottom,
            )
            assertEquals(
                "the row is no longer as tall as its indicator at ×1.0 in $locale",
                row.findViewById<View>(R.id.indicator).height + row.paddingTop + row.paddingBottom,
                row.height,
            )
        }
    }

    @Test
    fun addressRowWritesItsTitleWhole() {
        assertTextsWhole(R.layout.item_address, R.id.title) { row ->
            row.setText(R.id.title, LONGEST_ADDRESS)
            row.setText(R.id.detail, addressDetail(row.context))
        }
    }

    @Test
    fun addressRowWritesItsDetailWhole() {
        assertTextsWhole(R.layout.item_address, R.id.detail) { row ->
            row.setText(R.id.title, LONGEST_ADDRESS)
            row.setText(R.id.detail, addressDetail(row.context))
        }
    }

    @Test
    fun searchShortcutWritesItsLabelWhole() {
        // The shortcut naming a favourite station carries that station's name,
        // so it is as long as a station row's (SPEC §7.3).
        assertTextsWhole(R.layout.item_search_shortcut, R.id.shortcut_label) { row ->
            row.setText(R.id.shortcut_label, LONGEST_STATION_NAME)
        }
    }

    @Test
    fun storageDownloadStateWritesItselfWhole() {
        assertTextsWhole(R.layout.fragment_storage, R.id.download_state) { screen ->
            // Hidden until a download runs, and a hidden view is not laid out.
            // It is shown here for the reason the code shows it: a download.
            screen.findViewById<View>(R.id.download_state).visibility = View.VISIBLE
            screen.setText(
                R.id.download_state,
                screen.context.getString(R.string.storage_downloading, "tiles.mbtiles", "12.4 MB", "35.0 MB"),
            )
        }
    }

    private fun fillStationRow(row: View) {
        val context = row.context
        row.setText(R.id.name, LONGEST_STATION_NAME)
        row.setText(
            R.id.detail,
            context.resources.getQuantityString(
                R.plurals.station_detail_with_capacity,
                DOCKS,
                POSTCODE,
                DOCKS,
            ),
        )
        row.setText(R.id.counterpart, DOCKS.toString())
        // The longer of the two wordings, and the one on the screen the defect
        // was seen on: the list opens counting bikes, so the count beside the
        // name is the free docks.
        row.setText(R.id.counterpart_label, context.getString(R.string.counterpart_docks))
    }

    private fun addressDetail(context: Context) =
        context.getString(R.string.address_detail, "$POSTCODE Villeneuve-d'Ascq", "1,2 km")

    private fun View.setText(@IdRes id: Int, text: String) {
        findViewById<TextView>(id).text = text
    }

    private fun assertTextsWhole(
        @LayoutRes layout: Int,
        @IdRes textId: Int,
        fill: (View) -> Unit,
    ) {
        for (locale in LANGUAGES) {
            for (scale in TEXT_SIZE_STEPS) {
                for (width in SCREEN_WIDTHS_IN_DP) {
                    val screen = inflate(layout, scale, locale, width, fill)
                    val text = screen.findViewById<TextView>(textId)
                    val written = text.layout
                        ?: error("no layout for ${name(screen, textId)} at ×$scale in $locale")
                    assertWritesItsTextWhole(
                        written,
                        "${name(screen, textId)} \"${text.text}\" at ×$scale in $locale " +
                            "on ${width}dp",
                    )
                }
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
     * Builds a view at [scale] in [locale] and lays it out at the width of this
     * device.
     *
     * The height is left unbounded: what is under test is a row that is free to
     * grow taller, and measuring it inside the screen's height would say
     * nothing about whether it did.
     */
    private fun inflate(
        @LayoutRes layout: Int,
        scale: Float,
        locale: Locale,
        widthInDp: Int,
        fill: (View) -> Unit,
    ): View {
        val context = contextFor(scale, locale)
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = LayoutInflater.from(context).inflate(layout, null, false)
            fill(view)
            val metrics = context.resources.displayMetrics
            view.measure(
                View.MeasureSpec.makeMeasureSpec(
                    (widthInDp * metrics.density).toInt(),
                    View.MeasureSpec.EXACTLY,
                ),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        return view
    }

    private fun contextFor(scale: Float, locale: Locale): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(target.resources.configuration).apply {
            fontScale = scale
            setLocale(locale)
        }
        return ContextThemeWrapper(
            target.createConfigurationContext(configuration),
            R.style.Theme_RoueLibre,
        )
    }

    private fun name(screen: View, @IdRes id: Int) = screen.resources.getResourceEntryName(id)

    private companion object {
        /** The steps of Android's text size slider on a Fairphone 3. */
        val TEXT_SIZE_STEPS = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f)

        /** The interface's own language, and its one translation (SPEC §9). */
        val LANGUAGES = listOf(Locale.ENGLISH, Locale.FRENCH)

        /**
         * The widths the rows are laid out at, in dp.
         *
         * Not only the device's: a row that shares its width between two blocks
         * is at its worst on the narrowest screen, and the phone this is run on
         * is one of the wider ones. 320 dp is the narrowest an Android phone
         * reports; 360 is the commonest; 411 is this one. Measuring the three
         * costs a millisecond and catches what a single device cannot say.
         */
        val SCREEN_WIDTHS_IN_DP = listOf(320, 360, 411)

        /** The last of those, named where the reason for choosing it matters. */
        val WIDEST_SCREEN_IN_DP = SCREEN_WIDTHS_IN_DP.last()

        /**
         * The longest station name the served network publishes, read from
         * V'lille's `station_information` feed on 17 August 2026. Real data and
         * not an invented string: a fixture shorter than what the feed sends
         * would pass while the screen failed.
         */
        const val LONGEST_STATION_NAME = "Metropole Europeenne de Lille (CB)"

        /**
         * The system's text size when nobody has touched it — the one threshold
         * of this work, and the line these tests are written on both sides of.
         */
        const val NORMAL_TEXT_SIZE = 1.0f

        /** A name of the length most of the network's stations carry. */
        const val ORDINARY_STATION_NAME = "Gare Lille Flandres"

        /** The longest street name of the conurbation's address index. */
        const val LONGEST_ADDRESS = "Avenue du Colonel Jean-Baptiste Lebas"

        const val POSTCODE = "59650"
        const val DOCKS = 40
    }
}
