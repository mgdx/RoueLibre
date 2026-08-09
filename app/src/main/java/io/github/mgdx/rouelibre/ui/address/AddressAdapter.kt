package io.github.mgdx.rouelibre.ui.address

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.address.AddressEntryKind
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.databinding.ItemAddressBinding

/**
 * Affiche les adresses trouvées.
 *
 * @property onPick appelé quand une ligne est choisie.
 */
class AddressAdapter(private val onPick: (AddressResult) -> Unit) :
    ListAdapter<AddressResult, AddressAdapter.AddressViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        val binding = ItemAddressBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return AddressViewHolder(binding, onPick)
    }

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Une ligne de résultat. */
    class AddressViewHolder(
        private val binding: ItemAddressBinding,
        private val onPick: (AddressResult) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Remplit la ligne avec une adresse trouvée. */
        fun bind(result: AddressResult) {
            val context = binding.root.context
            binding.title.text = result.toTitle(context)
            binding.detail.text = result.toDetail(context)
            binding.detail.isGone = binding.detail.text.isNullOrBlank()
            binding.kindIcon.setImageResource(
                when (result.kind) {
                    AddressEntryKind.Street -> R.drawable.ic_search
                    AddressEntryKind.Landmark -> R.drawable.ic_pin
                },
            )
            // Un lecteur d'écran entend la même chose qu'un œil voit : l'adresse
            // puis son contexte, dans cet ordre.
            binding.root.contentDescription = context.getString(
                R.string.address_content_description,
                binding.title.text,
                binding.detail.text,
            )
            binding.root.setOnClickListener { onPick(result) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AddressResult>() {
            override fun areItemsTheSame(oldItem: AddressResult, newItem: AddressResult): Boolean =
                oldItem.streetId == newItem.streetId &&
                    oldItem.houseNumber == newItem.houseNumber

            override fun areContentsTheSame(
                oldItem: AddressResult,
                newItem: AddressResult,
            ): Boolean = oldItem == newItem
        }
    }
}
