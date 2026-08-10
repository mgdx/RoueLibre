package io.github.mgdx.rouelibre.ui.address

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.databinding.ItemSearchShortcutBinding

/**
 * A way of designating a point without typing an address (SPEC §7.3).
 *
 * The specification wants four ways of naming a journey's end, and typing is
 * only one of them. The other three head the result list, where they stay
 * whatever is typed: one's position above all, which is what somebody setting
 * off usually means by "from here".
 *
 * @property label what the row reads.
 * @property icon what it shows, so the three tell themselves apart at a glance.
 */
enum class SearchShortcut(@StringRes val label: Int, @DrawableRes val icon: Int) {

    /** Where the device says one is. */
    MyPosition(R.string.journey_source_my_position, R.drawable.ic_my_location),

    /** A station marked as a favourite (SPEC §7.5). */
    Favourite(R.string.journey_source_favourite, R.drawable.ic_favourite),

    /** A point aimed at on the map. */
    OnMap(R.string.journey_source_on_map, R.drawable.ic_place),
}

/**
 * Shows the shortcuts at the head of the address results.
 *
 * A list adapter rather than fixed views above the list: the rows then scroll
 * with the results instead of eating the height the keyboard already takes.
 *
 * @property onPick called when one of them is chosen.
 */
class SearchShortcutAdapter(private val onPick: (SearchShortcut) -> Unit) :
    ListAdapter<SearchShortcut, SearchShortcutAdapter.ShortcutViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder =
        ShortcutViewHolder(
            ItemSearchShortcutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            onPick,
        )

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** One shortcut row. */
    class ShortcutViewHolder(
        private val binding: ItemSearchShortcutBinding,
        private val onPick: (SearchShortcut) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Fills the row from a way of designating a point. */
        fun bind(shortcut: SearchShortcut) {
            binding.shortcutLabel.setText(shortcut.label)
            binding.shortcutIcon.setImageResource(shortcut.icon)
            binding.root.contentDescription = binding.root.context.getString(shortcut.label)
            binding.root.setOnClickListener { onPick(shortcut) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SearchShortcut>() {
            override fun areItemsTheSame(oldItem: SearchShortcut, newItem: SearchShortcut) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: SearchShortcut, newItem: SearchShortcut) =
                oldItem == newItem
        }
    }
}
