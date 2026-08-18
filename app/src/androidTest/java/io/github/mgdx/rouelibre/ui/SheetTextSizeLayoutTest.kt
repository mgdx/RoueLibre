package io.github.mgdx.rouelibre.ui

import android.content.Context
import android.content.res.Configuration
import android.text.Layout
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
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
 * Checks that nothing on a station's sheet is cut off at any text size the
 * system offers (SPEC §7).
 *
 * The third of the family, after [TextSizeLayoutTest] for the settings screen
 * and [ListTextSizeLayoutTest] for the lists. The sheet is where the defect
 * takes both of its shapes at once: a label beside a disc that runs off the
 * edge of the screen, and a button label held to one line and ellipsized. On a
 * Fairphone 3 in French it read "Ouvrir dans une appli de navig…" at ×1.3,
 * lost the "LIBRES" of "PLACES LIBRES" at ×1.8, and at ×2.0 offered "PLAC",
 * "Partir d'i…" and "Ouvrir dans une appli…".
 *
 * Everything the sheet writes is measured: the station's name, its address,
 * the banner saying it is out of service, the two labels naming the discs, the
 * breakdown of what the bikes are, the capacity line, and the three buttons.
 * Each passes on the same three counts the other two tests use — not
 * ellipsized, written to its last character, and no line ending in the middle
 * of a word.
 *
 * The fixtures are the longest the served network actually publishes rather
 * than invented ones, for the reason [ListTextSizeLayoutTest] gives: a fixture
 * shorter than the data would pass while the screen failed.
 */
@RunWith(AndroidJUnit4::class)
class SheetTextSizeLayoutTest {

    @Test
    fun sheetWritesTheStationNameWhole() = assertTextWhole(R.id.name)

    @Test
    fun sheetWritesTheAddressWhole() = assertTextWhole(R.id.address)

    @Test
    fun sheetWritesTheServiceStateWhole() = assertTextWhole(R.id.service_state)

    @Test
    fun sheetWritesTheBikesLabelWhole() = assertTextWhole(R.id.bikes_label)

    @Test
    fun sheetWritesTheDocksLabelWhole() = assertTextWhole(R.id.docks_label)

    @Test
    fun sheetWritesTheBikesSplitWhole() = assertTextWhole(R.id.bikes_split)

    @Test
    fun sheetWritesTheCapacityLineWhole() = assertTextWhole(R.id.capacity)

    @Test
    fun sheetWritesTheOriginButtonWhole() = assertTextWhole(R.id.set_as_origin)

    @Test
    fun sheetWritesTheDestinationButtonWhole() = assertTextWhole(R.id.set_as_destination)

    @Test
    fun sheetWritesTheNavigationButtonWhole() = assertTextWhole(R.id.open_in_navigation)

    /**
     * The label of a disc stays beside its disc rather than under it.
     *
     * What the reader is meant to see is a figure and the word naming it, read
     * as one thing. A label that has stepped below its own disc would satisfy
     * the tests above — nothing is cut — while saying something else, so the
     * arrangement is pinned separately. The two discs may go one under the
     * other; a label may not leave its disc.
     */
    @Test
    fun eachLabelStaysBesideItsDisc() {
        forEverySize { sheet, where ->
            assertBeside(sheet, R.id.bikes_indicator, R.id.bikes_label, where)
            assertBeside(sheet, R.id.docks_indicator, R.id.docks_label, where)
        }
    }

    /**
     * At the normal text size the sheet is the sheet it always was.
     *
     * The guard against repairing a screen by redrawing it, written as
     * [ListTextSizeLayoutTest] writes it: the two discs stand side by side, the
     * two journey buttons stand side by side, and every line the sheet writes
     * fits on one. Everything else in this file is about what happens once the
     * reader asks for larger characters; this says that until they do, nothing
     * happens at all.
     *
     * Pinned on the widest of the three screens for the reason that test gives:
     * the narrower ones had the defect at the normal size too, so they have no
     * unchanged state to hold on to.
     */
    @Test
    fun sheetIsUnchangedAtTheNormalTextSize() {
        for (locale in LANGUAGES) {
            val sheet = inflate(NORMAL_TEXT_SIZE, locale, WIDEST_SCREEN_IN_DP) { screen ->
                screen.setText(R.id.name, ORDINARY_STATION_NAME)
            }
            val where = "at ×$NORMAL_TEXT_SIZE in $locale"
            assertEquals(
                "the discs are no longer side by side $where",
                sheet.top(R.id.bikes_indicator),
                sheet.top(R.id.docks_indicator),
            )
            assertEquals(
                "the journey buttons are no longer side by side $where",
                sheet.top(R.id.set_as_origin),
                sheet.top(R.id.set_as_destination),
            )
            for (id in ONE_LINE_AT_THE_NORMAL_SIZE) {
                assertEquals(
                    "${sheet.name(id)} wraps $where",
                    1,
                    sheet.findViewById<TextView>(id).lineCount,
                )
            }
        }
    }

    private fun assertTextWhole(@IdRes textId: Int) {
        forEverySize { sheet, where ->
            val text = sheet.findViewById<TextView>(textId)
            val written = text.layout ?: error("no layout for ${sheet.name(textId)} $where")
            val what = "${sheet.name(textId)} \"${text.text}\" $where"
            assertWritesItsTextWhole(written, what)
            assertStaysInsideTheSheet(sheet, text, what)
        }
    }

    /**
     * Whether the view is drawn where the sheet can be read.
     *
     * The question the three counts below cannot put. A view given more width
     * than its parent has is laid out all the same and painted over the edge of
     * the screen: its text carries no ellipsis and reaches its last character,
     * so it passes every test of what was written while half of it is not
     * there. That is exactly how "PLACES LIBRES" lost its second word at ×1.8.
     */
    private fun assertStaysInsideTheSheet(sheet: View, view: View, what: String) {
        var end = view.width
        var walk: View? = view
        while (walk != null && walk !== sheet) {
            end += walk.left
            walk = walk.parent as? View
        }
        assertTrue(
            "$what runs $end past the ${sheet.width - sheet.paddingEnd} the sheet has",
            end <= sheet.width - sheet.paddingEnd,
        )
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
     * Whether [labelId] shares a line with [discId].
     *
     * Their tops are not compared: the label is centred on its disc and the two
     * have different heights. What says they are side by side is that neither
     * starts after the other has ended.
     */
    private fun assertBeside(sheet: View, @IdRes discId: Int, @IdRes labelId: Int, where: String) {
        val disc = sheet.findViewById<View>(discId)
        val label = sheet.findViewById<View>(labelId)
        assertTrue(
            "${sheet.name(labelId)} left ${sheet.name(discId)} $where",
            label.topOnSheet() < disc.bottomOnSheet() && disc.topOnSheet() < label.bottomOnSheet(),
        )
    }

    private fun forEverySize(check: (View, String) -> Unit) {
        for (locale in LANGUAGES) {
            for (scale in TEXT_SIZE_STEPS) {
                for (width in SCREEN_WIDTHS_IN_DP) {
                    check(inflate(scale, locale, width), "at ×$scale in $locale on ${width}dp")
                }
            }
        }
    }

    /**
     * Builds the sheet at [scale] in [locale] and lays it out at [widthInDp].
     *
     * Everything the sheet can show is shown, including the two lines the code
     * hides when it has nothing to put in them: a test of what is written must
     * write in all of it.
     */
    private fun inflate(
        scale: Float,
        locale: Locale,
        widthInDp: Int,
        andThen: (View) -> Unit = {},
    ): View {
        val context = contextFor(scale, locale)
        lateinit var sheet: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            sheet = LayoutInflater.from(context)
                .inflate(R.layout.sheet_station_detail, null, false)
            fill(sheet)
            andThen(sheet)
            val metrics = context.resources.displayMetrics
            sheet.measure(
                View.MeasureSpec.makeMeasureSpec(
                    (widthInDp * metrics.density).toInt(),
                    View.MeasureSpec.EXACTLY,
                ),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            sheet.layout(0, 0, sheet.measuredWidth, sheet.measuredHeight)
        }
        return sheet
    }

    private fun fill(sheet: View) {
        val context = sheet.context
        sheet.setText(R.id.name, LONGEST_STATION_NAME)
        sheet.show(R.id.address)
        sheet.setText(
            R.id.address,
            context.getString(
                R.string.address_detail,
                context.getString(
                    R.string.address_detail,
                    LONGEST_ADDRESS,
                    context.getString(R.string.address_locality, POSTCODE, CITY),
                ),
                DISTANCE,
            ),
        )
        sheet.show(R.id.service_state)
        sheet.setText(R.id.service_state, context.getString(R.string.station_out_of_service))
        // The labels come from the code rather than from the layout, being
        // agreed with the count beside them: the plural form is the longer of
        // the two, and the one the defect was measured on.
        sheet.setText(
            R.id.bikes_label,
            context.resources.getQuantityString(R.plurals.counterpart_bikes, BIKES),
        )
        sheet.setText(
            R.id.docks_label,
            context.resources.getQuantityString(R.plurals.counterpart_docks, DOCKS),
        )
        sheet.show(R.id.bikes_split)
        sheet.setText(
            R.id.bikes_split,
            context.getString(
                R.string.station_bikes_split,
                context.resources.getQuantityString(R.plurals.bikes_mechanical, BIKES, BIKES),
                context.resources.getQuantityString(R.plurals.bikes_electric, BIKES, BIKES),
            ),
        )
        sheet.setText(
            R.id.capacity,
            context.getString(
                R.string.station_capacity_and_age,
                context.resources.getQuantityString(R.plurals.docks_total, DOCKS, DOCKS),
                context.getString(
                    R.string.freshness_fresh,
                    context.resources.getQuantityString(
                        R.plurals.freshness_seconds,
                        SECONDS,
                        SECONDS,
                    ),
                ),
            ),
        )
    }

    /**
     * The context the sheet is really inflated in.
     *
     * Not the application's theme but the bottom sheet dialog's, resolved from
     * it exactly as `BottomSheetDialog` resolves it. **That is not a detail of
     * the harness: it is where the defect lives.** Material's bottom sheet
     * theme inherits the dialog overlay, which points `materialButtonStyle` at
     * the style of an alert dialog's buttons — `android:lines="1"`,
     * `android:singleLine`, `android:ellipsize="end"`. A test run under
     * `Theme.RoueLibre` alone sees three buttons wrapping their labels happily
     * and reports a screen that is broken on the device.
     */
    private fun contextFor(scale: Float, locale: Locale): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(target.resources.configuration).apply {
            fontScale = scale
            setLocale(locale)
        }
        val application = ContextThemeWrapper(
            target.createConfigurationContext(configuration),
            R.style.Theme_RoueLibre,
        )
        val sheetTheme = TypedValue()
        application.theme.resolveAttribute(
            com.google.android.material.R.attr.bottomSheetDialogTheme,
            sheetTheme,
            true,
        )
        return ContextThemeWrapper(application, sheetTheme.resourceId)
    }

    private fun View.setText(@IdRes id: Int, text: String) {
        findViewById<TextView>(id).text = text
    }

    private fun View.show(@IdRes id: Int) {
        findViewById<View>(id).visibility = View.VISIBLE
    }

    private fun View.top(@IdRes id: Int) = findViewById<View>(id).topOnSheet()

    private fun View.name(@IdRes id: Int): String = resources.getResourceEntryName(id)

    /**
     * A view's top and bottom in the sheet's own coordinates.
     *
     * The views compared here no longer share one parent — a disc and its label
     * sit in a block of their own — so their `top` alone would say nothing.
     */
    private fun View.topOnSheet(): Int {
        var total = 0
        var walk: View? = this
        while (walk != null) {
            total += walk.top
            walk = walk.parent as? View
        }
        return total
    }

    private fun View.bottomOnSheet(): Int = topOnSheet() + height

    private companion object {
        /** The steps of Android's text size slider on a Fairphone 3. */
        val TEXT_SIZE_STEPS = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f)

        /** The interface's own language, and its one translation (SPEC §9). */
        val LANGUAGES = listOf(Locale.ENGLISH, Locale.FRENCH)

        /** The three widths [ListTextSizeLayoutTest] measures, and why. */
        val SCREEN_WIDTHS_IN_DP = listOf(320, 360, 411)

        val WIDEST_SCREEN_IN_DP = SCREEN_WIDTHS_IN_DP.last()

        const val NORMAL_TEXT_SIZE = 1.0f

        /**
         * The lines that fit on one at the normal text size, and must go on
         * doing so. The address and the capacity line are left out: they are
         * long enough to wrap on a narrow screen already, and this is measured
         * on the widest.
         */
        val ONE_LINE_AT_THE_NORMAL_SIZE = listOf(
            R.id.name,
            R.id.bikes_label,
            R.id.docks_label,
            R.id.set_as_origin,
            R.id.set_as_destination,
            R.id.open_in_navigation,
        )

        /** See [ListTextSizeLayoutTest]: read from V'lille's feed, not invented. */
        const val LONGEST_STATION_NAME = "Metropole Europeenne de Lille (CB)"

        /** A name of the length most of the network's stations carry. */
        const val ORDINARY_STATION_NAME = "Gare Lille Flandres"

        const val LONGEST_ADDRESS = "Avenue du Colonel Jean-Baptiste Lebas"
        const val POSTCODE = "59650"
        const val CITY = "Villeneuve-d'Ascq"
        const val DISTANCE = "1,2 km"
        const val DOCKS = 40
        const val BIKES = 3
        const val SECONDS = 32
    }
}
