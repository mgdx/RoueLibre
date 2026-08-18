package io.github.mgdx.rouelibre.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * A row of blocks that go one under the other as soon as they no longer fit
 * side by side (SPEC §7).
 *
 * The third answer to one question, and it is the same question `ToggleRow` and
 * `StationRow` answer: what a row does when the reader turns the system's text
 * size up and there is no longer room across. The rule is the one written in
 * SPEC §7 — **it is the height that gives way, never the size of the
 * characters** — and the order is the one those two follow: what can grow
 * taller grows taller, and what cannot stand beside its neighbour steps below
 * it.
 *
 * Where this differs from those two is what a block is. A row of choices hands
 * every button an equal share of the width and asks whether the label survives
 * it; a station row shares between a name and a count. Here the blocks are
 * packed against the start and each asks for exactly what it needs — a disc and
 * the word naming it, which belong together and must be read as one thing. So
 * the question is the plainest of the three: **do they still fit?** The row
 * measures its own blocks at the size the system is giving them now and stacks
 * when their total, margins included, is more than the row has.
 *
 * That is a measurement and not a threshold, for the reason `ToggleRow` gives
 * at greater length: a size in `sp` at which to stack would have to be guessed
 * for the longest word of the longest translation on the narrowest screen, and
 * would be wrong for the next translation and the next screen. Nothing here is
 * a coefficient, and §14 has nothing to ask of this file.
 *
 * The gap travels with the arrangement. It is written into the layout as the
 * second block's start margin, which is what holds the two apart across; once
 * they are one under the other that margin would be an indent, so it becomes
 * the top margin and does the same work. `StationRow` moves its gap the same
 * way and for the same reason.
 *
 * A stacked block keeps `wrap_content` and therefore keeps the whole row to
 * itself, which is what lets a long label wrap rather than be cut off: the row
 * hands a stacked child the width it has, and a text view given a bounded width
 * takes the lines it needs.
 *
 * What this view never does is make the text smaller. The reader asked for
 * large characters.
 */
class StackingRow @JvmOverloads constructor(context: Context, attributes: AttributeSet? = null) :
    LinearLayout(context, attributes) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val room = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd
        if (room > 0) {
            stack(!blocksStandSideBySideIn(room))
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * Whether every block gets the width it asks for while they stand in a row.
     *
     * Each is measured with no bound at all, so what comes back is what it
     * wants rather than what a squeezed row would leave it. The margins count:
     * they are the gap that keeps the two blocks from touching, and a row that
     * only fits once the gap is gone does not fit.
     */
    private fun blocksStandSideBySideIn(room: Int): Boolean {
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var wanted = 0
        for (block in shownBlocks()) {
            block.measure(free, free)
            val parameters = block.layoutParams as LayoutParams
            wanted += block.measuredWidth + parameters.marginStart + parameters.marginEnd
        }
        return wanted <= room
    }

    /**
     * Puts the blocks one under another, or back side by side.
     *
     * The layout parameters are changed in place rather than through the
     * setter: this runs inside a measure pass which is about to read them, and
     * the setter would ask for a second one for nothing. Changing the
     * orientation asks for that pass by itself.
     */
    private fun stack(stacked: Boolean) {
        val wanted = if (stacked) VERTICAL else HORIZONTAL
        if (orientation == wanted) return

        for (index in 0 until childCount) {
            val parameters = getChildAt(index).layoutParams as LayoutParams
            if (stacked) {
                parameters.topMargin = parameters.marginStart
                parameters.marginStart = 0
            } else {
                parameters.marginStart = parameters.topMargin
                parameters.topMargin = 0
            }
        }
        orientation = wanted
    }

    private fun shownBlocks(): List<View> = (0 until childCount)
        .map { getChildAt(it) }
        .filter { it.visibility != View.GONE }
}
