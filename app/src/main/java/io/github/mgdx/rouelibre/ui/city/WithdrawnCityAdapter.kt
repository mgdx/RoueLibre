package io.github.mgdx.rouelibre.ui.city

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.config.WithdrawnCity
import io.github.mgdx.rouelibre.databinding.ItemCityBinding
import io.github.mgdx.rouelibre.ui.cityLabel
import io.github.mgdx.rouelibre.ui.storage.formatBytes

/**
 * A network served once and withdrawn since, as the city list shows it.
 *
 * @property city the network, named as the catalogue last named it.
 * @property isActive true if it is still the one the settings name.
 * @property installedBytes the space its data still occupies, `0` if none.
 */
data class WithdrawnCityRow(
    val city: WithdrawnCity,
    val isActive: Boolean,
    val installedBytes: Long,
)

/**
 * Shows the withdrawn networks a device still holds something of (SPEC §15.1).
 *
 * They are listed for one purpose, which is to be deleted. Their data is of no
 * use to any other city and no release will serve it again, yet it is not
 * deleted unasked: the application destroys nothing the user has not chosen to
 * part with. Leaving it unlisted was the worse of the two, the space it takes
 * becoming invisible the moment another city was chosen.
 *
 * The row answers no tap and carries no touch feedback, there being nothing
 * behind it to open; only its delete button acts.
 */
class WithdrawnCityAdapter(private val onDelete: (WithdrawnCity) -> Unit) :
    ListAdapter<WithdrawnCityRow, WithdrawnCityAdapter.WithdrawnCityViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WithdrawnCityViewHolder =
        WithdrawnCityViewHolder(
            ItemCityBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onDelete,
        )

    override fun onBindViewHolder(holder: WithdrawnCityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** One withdrawn network's row. */
    class WithdrawnCityViewHolder(
        private val binding: ItemCityBinding,
        private val onDelete: (WithdrawnCity) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.isClickable = false
            binding.root.foreground = null
        }

        /** Fills the row from a withdrawn network's state. */
        fun bind(row: WithdrawnCityRow) {
            val context = binding.root.context
            val label = context.cityLabel(row.city.displayName, row.city.mainCity)
            val installed = row.installedBytes > 0

            binding.cityName.text = label
            binding.cityActive.isVisible = row.isActive
            binding.cityDetail.isVisible = true
            binding.cityDetail.setText(R.string.city_withdrawn)
            binding.cityInstalled.isVisible = installed
            binding.cityInstalled.text = context.getString(
                R.string.city_installed,
                formatBytes(context, row.installedBytes),
            )
            binding.cityDelete.isVisible = installed
            binding.cityDelete.contentDescription =
                context.getString(R.string.city_delete_description, label)
            binding.cityDelete.setOnClickListener { onDelete(row.city) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<WithdrawnCityRow>() {
            override fun areItemsTheSame(
                oldItem: WithdrawnCityRow,
                newItem: WithdrawnCityRow,
            ): Boolean = oldItem.city.id == newItem.city.id

            override fun areContentsTheSame(
                oldItem: WithdrawnCityRow,
                newItem: WithdrawnCityRow,
            ): Boolean = oldItem == newItem
        }
    }
}
