package io.github.mgdx.rouelibre.ui.city

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.databinding.ItemCityBinding
import io.github.mgdx.rouelibre.ui.cityLabel
import io.github.mgdx.rouelibre.ui.storage.formatBytes
import io.github.mgdx.rouelibre.ui.textLocale

/**
 * A city as the screen presents it.
 *
 * @property entry the city from the catalogue.
 * @property isActive true if it is the one the application serves.
 * @property installedBytes the space its data already occupies, `0` if none.
 * @property isSupported whether this build carries the city's configuration.
 *   A downloaded catalogue can name cities added after this release, and those
 *   cannot be served: the row says so rather than leading somewhere empty.
 */
data class CityRow(
    val entry: CityEntry,
    val isActive: Boolean,
    val installedBytes: Long,
    val isSupported: Boolean = true,
)

/** Shows the catalogue's cities, the one in service first. */
class CityAdapter(
    private val onChoose: (CityEntry) -> Unit,
    private val onDelete: (CityEntry) -> Unit,
) : ListAdapter<CityRow, CityAdapter.CityViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CityViewHolder =
        CityViewHolder(
            ItemCityBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onChoose,
            onDelete,
        )

    override fun onBindViewHolder(holder: CityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** One city row. */
    class CityViewHolder(
        private val binding: ItemCityBinding,
        private val onChoose: (CityEntry) -> Unit,
        private val onDelete: (CityEntry) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * The touch feedback the layout gave this row, kept to be put back.
         *
         * A refused row loses it, and rows are recycled: without this the
         * ripple would disappear for good from the first row that reused a
         * refused one's view.
         */
        private val selectableBackground = binding.root.foreground

        /** Fills the row from a city's state. */
        fun bind(row: CityRow) {
            val context = binding.root.context
            val locale = context.textLocale()
            val city = row.entry

            binding.cityName.text = context.cityLabel(city.displayName, city.mainCity)
            binding.cityActive.isVisible = row.isActive

            // A city this build does not know is shown and refused, never
            // hidden: somebody who has heard their city is served must find it
            // and read why it is not here yet, rather than conclude it is
            // missing from the catalogue (SPEC §15).
            if (!row.isSupported) {
                bindUnsupported()
                return
            }

            val stations = city.stationCount
            // The weight announced before any download, as SPEC §11.9
            // requires: this is the only place it is seen before choosing.
            val size = city.dataSizeBytes
            val installed = row.installedBytes > 0
            binding.cityDetail.text = when {
                // Once the data is installed, the line below announces its
                // weight; still offering it "to download" here would
                // contradict it.
                stations != null && installed -> context.resources.getQuantityString(
                    R.plurals.city_stations,
                    stations,
                    stations,
                )

                stations != null && size != null -> context.resources.getQuantityString(
                    R.plurals.city_detail,
                    stations,
                    stations,
                    formatBytes(context, size),
                )

                size != null && !installed -> context.getString(
                    R.string.city_detail_size_only,
                    formatBytes(context, size),
                )

                // Installed, but the catalogue says nothing about it: the line
                // below already says what it weighs here.
                installed -> ""

                // The catalogue carries no size — it is published apart from
                // the manifests and can lag behind them, so its silence proves
                // nothing about the data. Announce what is actually known,
                // rather than a "not published yet" the storage screen then
                // contradicts by offering the download (SPEC §4.4).
                stations != null -> context.resources.getQuantityString(
                    R.plurals.city_detail_size_unknown,
                    stations,
                    stations,
                )

                else -> context.getString(R.string.city_size_unknown)
            }
            binding.cityDetail.isVisible = binding.cityDetail.text.isNotEmpty()

            binding.cityInstalled.isVisible = installed
            binding.cityInstalled.text = context.getString(
                R.string.city_installed,
                formatBytes(context, row.installedBytes),
            )
            binding.cityDelete.isVisible = row.installedBytes > 0
            binding.cityDelete.contentDescription =
                context.getString(R.string.city_delete_description, city.displayName)
            binding.cityDelete.setOnClickListener { onDelete(city) }

            binding.root.setOnClickListener { onChoose(city) }
            binding.root.isEnabled = true
            binding.root.alpha = FULLY_LEGIBLE
            binding.root.foreground = selectableBackground
        }

        /**
         * Dresses the row of a city this version cannot serve.
         *
         * Everything the row would offer goes with it: the weight to download,
         * what is installed, the button that deletes it. What is left is the
         * name, dimmed, and the sentence that explains it — and a row that does
         * not answer a tap, since there is nothing behind it. The touch
         * feedback goes too: a ripple on a row that leads nowhere reads as a
         * screen that failed to open.
         */
        private fun bindUnsupported() {
            val context = binding.root.context
            binding.cityDetail.isVisible = true
            binding.cityDetail.text = context.getString(R.string.city_needs_newer_version)
            binding.cityInstalled.isVisible = false
            binding.cityDelete.isVisible = false
            binding.root.setOnClickListener(null)
            binding.root.isClickable = false
            binding.root.isEnabled = false
            binding.root.alpha = DIMMED
            binding.root.foreground = null
        }
    }

    private companion object {
        /**
         * How far a refused row is dimmed.
         *
         * Enough to read as unavailable beside its neighbours, not so far that
         * the name stops being legible — the name is the whole point of showing
         * the row at all.
         */
        const val DIMMED = 0.45f
        const val FULLY_LEGIBLE = 1f

        val DIFF = object : DiffUtil.ItemCallback<CityRow>() {
            override fun areItemsTheSame(oldItem: CityRow, newItem: CityRow): Boolean =
                oldItem.entry.id == newItem.entry.id

            override fun areContentsTheSame(oldItem: CityRow, newItem: CityRow): Boolean =
                oldItem == newItem
        }
    }
}
