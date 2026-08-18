package io.github.mgdx.rouelibre.ui

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * A row of choice buttons — theme, units, the screen opened on, one's own bike,
 * the walking pace, the kind of bike wanted, bikes against free docks — that
 * keeps its labels whole at every text size the system offers (SPEC §7).
 *
 * Every such row in the application is one of these, and its buttons wear
 * `Widget.RoueLibre.Toggle.Choice`, which hands the label the width Material
 * would have spent on padding. The two work together: the style is what makes
 * a row of three still hold "Système" at ×2.0, and this view is what happens
 * when even that is not enough.
 *
 * A plain [MaterialButtonToggleGroup] gives each button an equal share of the
 * width whatever it has to write in it, and a share is a third or a quarter of
 * the screen. Past the normal font size the labels no longer fit that share and
 * Android cuts them off with an ellipsis: at ×2.0 "Mécanique" and "Électrique"
 * both came out as seven letters and a dot-dot-dot, telling apart at the third
 * letter two choices that decide the journey. The setting meant to make the
 * screen readable was making it less so, which SPEC §7 rules out.
 *
 * The row answers in two steps, in this order:
 *
 *  1. **The label wraps.** A button holds up to [MAXIMUM_LABEL_LINES] lines and
 *     grows taller; the row stays a row and nothing around it moves. This is
 *     what carries "Liste des stations" to ×2.0 on a 411 dp screen, on two
 *     lines, in a row of two.
 *  2. **The row stacks.** When even those lines cannot hold a label — a long
 *     single word such as "Électrique" has nowhere to break, so no number of
 *     lines saves it — the buttons go one under another, each on the full
 *     width. The precedent is SPEC §7.9, which drops the welcome drawing at the
 *     largest sizes rather than push a sentence off the screen.
 *
 * **The switch is measured, not a font scale threshold.** The row asks its own
 * buttons, at the width they would actually get, whether their text comes out
 * whole; it stacks only if one of them says no. A threshold in `sp` would have
 * to be guessed for the longest label of the longest translation on the
 * narrowest screen, and would then be wrong for the row of two buttons next to
 * the row of four. Measuring costs one extra pass when the size changes — a
 * font size change already rebuilds the activity — and is right for every
 * language, including the ones nobody has translated yet.
 *
 * The one figure here is [MAXIMUM_LABEL_LINES]. Two: on a 411 dp screen a
 * three-across row leaves about 118 dp per button, which at ×2.0 holds some six
 * characters a line, so a third line would be reading a label down a column
 * three letters at a time. Two lines is where wrapping still reads as a label
 * and past which stacking is the honest layout.
 *
 * What this view never does is make the text smaller. The reader asked for big
 * characters; `autoSizeTextType` would answer no to the only thing they asked.
 */
class ToggleRow @JvmOverloads constructor(context: Context, attributes: AttributeSet? = null) :
    MaterialButtonToggleGroup(context, attributes) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val room = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd
        if (room > 0) {
            stack(!labelsComeOutWholeAcross(room))
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * Whether every button writes its label in full in the share of [room] a
     * row would give it.
     *
     * The buttons are measured at that exact width, which is what the weights
     * hand them, and each is then asked what it managed to lay out. The
     * negative margins the group uses to overlap its strokes are ignored: they
     * only ever give a button a couple more pixels, so the answer errs towards
     * stacking rather than towards a label cut off.
     */
    private fun labelsComeOutWholeAcross(room: Int): Boolean {
        val buttons = shownButtons()
        if (buttons.isEmpty()) return true

        val shareSpec = MeasureSpec.makeMeasureSpec(room / buttons.size, MeasureSpec.EXACTLY)
        val freeHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        return buttons.all { button ->
            button.maxLines = MAXIMUM_LABEL_LINES
            button.measure(shareSpec, freeHeightSpec)
            button.writesItsLabelWhole()
        }
    }

    /**
     * Whether the last measurement laid the label out in a form one can read.
     *
     * Three things can go wrong, and all three have been seen on the settings
     * screen at ×2.0:
     *
     *  - the label is ellipsized, which `getEllipsisCount` reports;
     *  - it runs out of lines and the rest is dropped in silence, which only
     *    the end offset of the last line shows;
     *  - it is whole but a word has been cut in half to get there — Android
     *    breaks between two letters, with no hyphen, once a word is wider than
     *    the line, and "Mécanique" came out as "Mécaniq" over "ue". A label
     *    split that way is not a label, so the row is better off stacked.
     */
    private fun MaterialButton.writesItsLabelWhole(): Boolean {
        val written = layout ?: return true
        val lastLine = written.lineCount - 1
        if (written.getEllipsisCount(lastLine) != 0) return false
        if (written.getLineEnd(lastLine) < written.text.length) return false
        return (0 until lastLine).all { line -> written.breaksBetweenWords(line) }
    }

    /**
     * Whether the line ends where a line may end: after a space or after a
     * hyphen the label was written with. Anything else is a word cut in two.
     */
    private fun Layout.breaksBetweenWords(line: Int): Boolean {
        val lastCharacter = text[getLineEnd(line) - 1]
        return lastCharacter.isWhitespace() || lastCharacter == '-'
    }

    /**
     * Puts the buttons one under another, or back side by side.
     *
     * The layout parameters are changed in place rather than through the
     * setter: this runs inside a measure pass, which is about to read them, and
     * the setter would ask for a second one for nothing. Changing the
     * orientation asks for it by itself, and that is the pass in which Material
     * redraws the group's corners the right way round.
     */
    private fun stack(stacked: Boolean) {
        val wanted = if (stacked) VERTICAL else HORIZONTAL
        if (orientation == wanted) return

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val parameters = child.layoutParams as LinearLayout.LayoutParams
            if (stacked) {
                parameters.width = LayoutParams.MATCH_PARENT
                parameters.height = LayoutParams.WRAP_CONTENT
                parameters.weight = 0f
            } else {
                parameters.width = 0
                parameters.height = LayoutParams.MATCH_PARENT
                parameters.weight = 1f
            }
        }
        orientation = wanted
    }

    private fun shownButtons(): List<MaterialButton> = (0 until childCount)
        .map { getChildAt(it) }
        .filterIsInstance<MaterialButton>()
        .filter { it.visibility != View.GONE }

    private companion object {
        /** See the class comment: two, and why it is two. */
        const val MAXIMUM_LABEL_LINES = 2
    }
}
