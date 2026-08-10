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
import io.github.mgdx.rouelibre.ui.storage.formatBytes
import io.github.mgdx.rouelibre.ui.textLocale

/**
 * Une ville telle que l'écran la présente.
 *
 * @property entry la ville du catalogue.
 * @property isActive vrai si c'est celle que l'application sert.
 * @property installedBytes place que ses données occupent déjà, `0` si aucune.
 */
data class CityRow(val entry: CityEntry, val isActive: Boolean, val installedBytes: Long)

/** Affiche les villes du catalogue, celle en service en premier. */
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

    /** Une ligne de ville. */
    class CityViewHolder(
        private val binding: ItemCityBinding,
        private val onChoose: (CityEntry) -> Unit,
        private val onDelete: (CityEntry) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Remplit la ligne à partir de l'état d'une ville. */
        fun bind(row: CityRow) {
            val context = binding.root.context
            val locale = context.textLocale()
            val city = row.entry

            binding.cityName.text = city.displayName
            binding.cityActive.isVisible = row.isActive

            val stations = city.stationCount
            // Le poids annoncé avant tout téléchargement, comme l'exige le
            // SPEC §11.9 : c'est le seul endroit où on le voit avant de choisir.
            val size = city.dataSizeBytes
            binding.cityDetail.text = when {
                stations != null && size != null -> context.resources.getQuantityString(
                    R.plurals.city_detail,
                    stations,
                    stations,
                    formatBytes(size, locale),
                )

                size != null -> context.getString(
                    R.string.city_detail_size_only,
                    formatBytes(size, locale),
                )

                // Une ville listée dont les données ne sont pas publiées : le
                // dire, plutôt que de laisser un téléchargement échouer.
                else -> context.getString(R.string.city_data_unavailable)
            }

            binding.cityInstalled.isVisible = row.installedBytes > 0
            binding.cityInstalled.text = context.getString(
                R.string.city_installed,
                formatBytes(row.installedBytes, locale),
            )
            binding.cityDelete.isVisible = row.installedBytes > 0
            binding.cityDelete.contentDescription =
                context.getString(R.string.city_delete_description, city.displayName)
            binding.cityDelete.setOnClickListener { onDelete(city) }

            binding.root.setOnClickListener { onChoose(city) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CityRow>() {
            override fun areItemsTheSame(oldItem: CityRow, newItem: CityRow): Boolean =
                oldItem.entry.id == newItem.entry.id

            override fun areContentsTheSame(oldItem: CityRow, newItem: CityRow): Boolean =
                oldItem == newItem
        }
    }
}
