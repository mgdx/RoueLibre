package io.github.mgdx.rouelibre.ui.stations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.databinding.ItemStationSectionBinding

/**
 * One heading of the station list, or nothing (SPEC §7.6).
 *
 * An adapter of its own, holding at most one row, so that the list can be put
 * together from parts — the favourites' heading, the favourites, the heading
 * of the whole list, the whole list — and each part come and go without the
 * station rows, which are the list's own (`StationAdapter`), learning that
 * headings exist.
 *
 * @param title what the heading names.
 * @param onReorder opens the favourites' own screen, or `null` for a heading
 *   that offers nothing — the one over the whole list.
 * @param onFold folds the part under the heading away, or unfolds it; `null`
 *   for a heading whose part cannot be folded.
 */
class StationSectionAdapter(
    private val title: Int,
    private val onReorder: (() -> Unit)? = null,
    private val onFold: (() -> Unit)? = null,
) : RecyclerView.Adapter<StationSectionAdapter.HeadingHolder>() {

    /** Whether the heading is on the list at all. */
    var shown: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) notifyItemInserted(0) else notifyItemRemoved(0)
        }

    /** Whether the part under the heading is folded away. */
    var folded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (shown) notifyItemChanged(0)
        }

    override fun getItemCount(): Int = if (shown) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeadingHolder =
        HeadingHolder(
            ItemStationSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(holder: HeadingHolder, position: Int) {
        holder.bind(title, onReorder, onFold, folded)
    }

    /** The heading's one row. */
    class HeadingHolder(private val views: ItemStationSectionBinding) :
        RecyclerView.ViewHolder(views.root) {

        /** Writes the heading and wires what it offers. */
        fun bind(title: Int, onReorder: (() -> Unit)?, onFold: (() -> Unit)?, folded: Boolean) {
            views.sectionTitle.setText(title)
            ViewCompat.setAccessibilityHeading(views.sectionTitle, true)
            views.reorder.isVisible = onReorder != null
            views.reorder.setOnClickListener { onReorder?.invoke() }
            views.fold.isVisible = onFold != null
            views.fold.setOnClickListener { onFold?.invoke() }
            // The icon shows what a press does, as the station sheet's own
            // fold button does, and the label says it in words.
            views.fold.setIconResource(if (folded) R.drawable.ic_unfold else R.drawable.ic_fold)
            views.fold.contentDescription = views.root.context.getString(
                if (folded) {
                    R.string.stations_favourites_unfold
                } else {
                    R.string.stations_favourites_fold
                },
            )
        }
    }
}
