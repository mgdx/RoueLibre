package io.github.mgdx.rouelibre.ui.stations

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import io.github.mgdx.rouelibre.R

/**
 * One station row — the station's name and where it is on the left, the
 * counterpart count on the right — laid out so that the name stays whole at
 * every text size the system offers (SPEC §7).
 *
 * The row is read for its **name**: it is what tells one station from the next,
 * and "Alfre…" tells nothing from "Anato…". The count beside it is a supporting
 * fact, the one the indicator does not already show. That order of importance
 * is what this view enforces when the width runs short.
 *
 * Left alone, the row shares its width first-come-first-served: the indicator
 * and the count block ask for what they need and the name takes what is left.
 * That works while the text is small and stops working long before ×2.0, where
 * the indicator is 104 dp — it is sized in `sp`, so it grows with the figure it
 * holds — and "FREE DOCKS" set in large capitals is another 195, leaving the
 * name some fifty dp of a 379 dp row. Five letters.
 *
 * So the row answers in two steps, the same two the settings' rows of choices
 * answer in (SPEC §7):
 *
 *  1. **The name wraps.** It is a plain text view with no line limit: it takes
 *     the lines it needs and the row grows taller. Unlike a button in a row of
 *     buttons it has no equal share to stay inside, so there is no line count to
 *     cap here — see the class comment of `ToggleRow` for the case where there
 *     is one.
 *  2. **The count steps below.** It gives up its place at the right and goes
 *     under the row's text, which then spans the full width. At ×2.0 that turns
 *     fifty dp of name into three hundred.
 *
 * **The switch is measured, and carries deliberately no threshold**, exactly as
 * the settings' rows do. Two questions are put, and the count steps below if
 * either is answered no:
 *
 *  - **the sharing**: the row asks its own two blocks how wide they want to be,
 *    at the text size in force, and the supporting fact may not take more of the
 *    row than the subject;
 *  - **what the share writes**: the name is laid out at the width it would
 *    actually get and asked whether any line of it ends in the middle of a word,
 *    which is the third of the questions `ToggleRow` puts to its buttons.
 *    Android breaks a word wider than its line between two letters and with no
 *    hyphen, so a column narrower than one word of a name reports no ellipsis
 *    while being unreadable.
 *
 * Neither question involves a font scale, a fraction of the screen or the
 * longest label of the longest translation, and both are right on a screen
 * nobody has tested and in a language nobody has translated yet.
 *
 * **The first question is what holds the ordinary screen still**, and it is
 * there for that. A stricter sharing was tried — the name to be left more of the
 * row than everything else on it put together — and it reads better in the
 * middle of the range, putting the count below from ×1.5 in English on a 411 dp
 * screen where this one waits for ×2.0. It was dropped because it also stacks at
 * the **normal** text size on a 360 dp screen in French and on a 320 dp screen
 * in both languages, rebuilding the row for readers who never asked for large
 * characters and have no defect to fix. What is left in the middle of the range
 * is a name on three or four lines beside its count — taller than it might be,
 * and whole, which is what §7 asks for.
 *
 * **The second question is what the first cannot see**, and it is the one that
 * makes two rows of the same list differ: at a size where a long name breaks a
 * word and a short one does not, the first stacks and the second does not. That
 * is accepted rather than overlooked. The alternative is a row that keeps its
 * shape and cuts a word in half, and SPEC §7 does not treat a cut word as a
 * compromise.
 *
 * What this view never does is make the text smaller. The reader asked for large
 * characters.
 */
class StationRow @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
) : ConstraintLayout(context, attributes) {

    private lateinit var indicator: View
    private lateinit var name: TextView
    private lateinit var detail: TextView
    private lateinit var count: View

    /**
     * The gap between the name and the count block.
     *
     * Read from the layout rather than from the dimension resource, so the
     * layout stays the one place it is decided. It is the name's end margin and
     * not the block's start margin, and that is not a detail: a margin on a side
     * carrying no constraint is ignored, which showed up in Arabic with the name
     * and the count flush against one another. When the count steps below, the
     * name's end no longer faces it and the margin is handed to the block's top,
     * where it does the same work.
     */
    private var gap = 0

    private var countIsBelow = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        indicator = findViewById(R.id.indicator)
        name = findViewById(R.id.name)
        detail = findViewById(R.id.detail)
        count = findViewById(R.id.counterpart_block)
        gap = (name.layoutParams as LayoutParams).marginEnd
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val room = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd
        if (room > 0) {
            placeCountBelow(!countCanStandBesideTheNameIn(room))
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * Whether the count can stand beside the name in [room].
     *
     * Two things are asked, and the count steps below if either says no. The
     * first is the sharing: the supporting fact may not take more of the row
     * than the subject. The second is what a share of that width actually
     * writes, which is the question `ToggleRow` puts to its buttons: a column
     * narrower than one word of the name is not a column at all, since Android
     * breaks a word too wide for its line between two letters and with no
     * hyphen. A 320 dp screen at ×1.3 leaves the name 105 dp and "Europeenne"
     * wants more, and the sharing alone called that fair.
     */
    private fun countCanStandBesideTheNameIn(room: Int): Boolean {
        val wanted = widthWanted(count)
        val leftToTheName = room - widthWanted(indicator) -
            (name.layoutParams as LayoutParams).marginStart - gap - wanted
        if (leftToTheName < wanted) return false
        return name.writesItsTextWholeAcross(leftToTheName) &&
            detail.writesItsTextWholeAcross(leftToTheName)
    }

    /**
     * Whether [text], given exactly [width], lays its text out in a form one
     * can read: no line ending in the middle of a word.
     *
     * Nothing is asked about ellipses or dropped lines here, and nothing needs
     * to be: these views carry no line limit, so they always write to their
     * last character. The only way this text can come out unreadable is a word
     * cut in half.
     */
    private fun TextView.writesItsTextWholeAcross(width: Int): Boolean {
        val text = this
        if (text.isGone || text.text.isNullOrEmpty()) return true
        text.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val written = text.layout ?: return true
        return (0 until written.lineCount - 1).all { line ->
            val lastCharacter = written.text[written.getLineEnd(line) - 1]
            lastCharacter.isWhitespace() || lastCharacter == '-'
        }
    }

    /** How wide [view] would draw itself given all the room in the world. */
    private fun widthWanted(view: View): Int {
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        view.measure(free, free)
        return view.measuredWidth
    }

    /**
     * Moves the count under the row's text, or back beside the name.
     *
     * The count keeps its bottom on the parent in both arrangements: it is what
     * makes a row of wrapped height end at the count rather than under it. Its
     * top anchor moves to the detail line, and a detail line that is hidden —
     * a network publishing neither postcode nor capacity leaves one — collapses
     * onto the name's bottom of its own accord, which is where the count then
     * lands.
     */
    private fun placeCountBelow(below: Boolean) {
        if (countIsBelow == below) return
        countIsBelow = below

        val nameParameters = name.layoutParams as LayoutParams
        val countParameters = count.layoutParams as LayoutParams
        if (below) {
            nameParameters.endToStart = LayoutParams.UNSET
            nameParameters.endToEnd = LayoutParams.PARENT_ID
            nameParameters.marginEnd = 0
            countParameters.topToTop = LayoutParams.UNSET
            countParameters.topToBottom = detail.id
            countParameters.topMargin = gap
        } else {
            nameParameters.endToEnd = LayoutParams.UNSET
            nameParameters.endToStart = count.id
            nameParameters.marginEnd = gap
            countParameters.topToBottom = LayoutParams.UNSET
            countParameters.topToTop = LayoutParams.PARENT_ID
            countParameters.topMargin = 0
        }
        // Through the setter and not in place: ConstraintLayout only reads its
        // children's constraints again once one of them has asked for a layout.
        name.layoutParams = nameParameters
        count.layoutParams = countParameters
    }
}
