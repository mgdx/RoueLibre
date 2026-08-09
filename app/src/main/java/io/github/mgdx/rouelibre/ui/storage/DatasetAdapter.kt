package io.github.mgdx.rouelibre.ui.storage

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.DatasetRejection
import io.github.mgdx.rouelibre.databinding.ItemDatasetBinding
import io.github.mgdx.rouelibre.ui.textLocale
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Affiche les trois jeux de données et leurs actions. */
class DatasetAdapter(
    private val onImport: (DatasetKind) -> Unit,
    private val onDelete: (DatasetKind) -> Unit,
) : ListAdapter<DatasetRow, DatasetAdapter.DatasetViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DatasetViewHolder =
        DatasetViewHolder(
            ItemDatasetBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onImport,
            onDelete,
        )

    override fun onBindViewHolder(holder: DatasetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Une ligne de jeu de données. */
    class DatasetViewHolder(
        private val binding: ItemDatasetBinding,
        private val onImport: (DatasetKind) -> Unit,
        private val onDelete: (DatasetKind) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Remplit la ligne à partir de l'état d'un jeu. */
        fun bind(row: DatasetRow) {
            val context = binding.root.context
            val name = context.getString(row.kind.nameResource())

            binding.datasetName.text = name
            binding.datasetPurpose.setText(row.kind.purposeResource())

            val installed = row.installed
            binding.datasetState.text = if (installed == null) {
                context.getString(R.string.dataset_absent)
            } else {
                context.getString(
                    R.string.dataset_installed,
                    formatBytes(installed.sizeBytes, context.textLocale()),
                    dateFormatFor(context)
                        .format(installed.installedAt.atZone(ZoneId.systemDefault())),
                )
            }

            // Le libellé dit ce qui va se passer : installer là où il n'y a
            // rien, remplacer là où il y a déjà quelque chose.
            binding.datasetImport.setText(
                if (installed == null) R.string.dataset_import else R.string.dataset_replace,
            )
            binding.datasetImport.setOnClickListener { onImport(row.kind) }

            binding.datasetDelete.isVisible = installed != null
            binding.datasetDelete.contentDescription =
                context.getString(R.string.dataset_delete_description, name)
            binding.datasetDelete.setOnClickListener { onDelete(row.kind) }
        }

        /**
         * Formate la date dans la langue que l'application sert réellement.
         *
         * La langue du système ne convient pas : sur un appareil en anglais,
         * elle produisait « August 9, 2026 » au milieu d'une interface
         * française.
         */
        private fun dateFormatFor(context: Context): DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(context.textLocale())
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<DatasetRow>() {
            override fun areItemsTheSame(oldItem: DatasetRow, newItem: DatasetRow): Boolean =
                oldItem.kind == newItem.kind

            override fun areContentsTheSame(oldItem: DatasetRow, newItem: DatasetRow): Boolean =
                oldItem == newItem
        }
    }
}

/** Nom affiché d'un jeu de données. */
fun DatasetKind.nameResource(): Int = when (this) {
    DatasetKind.Tiles -> R.string.dataset_tiles
    DatasetKind.Routing -> R.string.dataset_routing
    DatasetKind.Addresses -> R.string.dataset_addresses
}

/** Ce à quoi sert un jeu de données, en une ligne. */
fun DatasetKind.purposeResource(): Int = when (this) {
    DatasetKind.Tiles -> R.string.dataset_tiles_purpose
    DatasetKind.Routing -> R.string.dataset_routing_purpose
    DatasetKind.Addresses -> R.string.dataset_addresses_purpose
}

/**
 * Formate une taille de fichier pour l'affichage.
 *
 * Base 1000 et non 1024 : c'est l'unité dans laquelle les tailles sont
 * annoncées à l'utilisateur partout ailleurs, y compris par le système.
 *
 * La langue décide du séparateur décimal — « 35,0 Mo » en français — et doit
 * donc être celle du texte affiché, pas celle du système.
 */
fun formatBytes(bytes: Long, locale: Locale): String = when {
    bytes < 1_000 -> "$bytes o"
    bytes < 1_000_000 -> String.format(locale, "%.0f ko", bytes / 1_000.0)
    bytes < 1_000_000_000 -> String.format(locale, "%.1f Mo", bytes / 1_000_000.0)
    else -> String.format(locale, "%.2f Go", bytes / 1_000_000_000.0)
}

/**
 * Met en mots l'issue d'une action de l'écran stockage.
 *
 * Chaque refus dit ce qui ne va pas et ce qu'il faut faire, jamais un code
 * technique (SPEC §14).
 */
fun StorageMessage.toText(context: Context): String = when (this) {
    is StorageMessage.Installed ->
        context.getString(R.string.dataset_imported, context.getString(kind.nameResource()))

    is StorageMessage.Deleted ->
        context.getString(R.string.dataset_deleted, context.getString(kind.nameResource()))

    is StorageMessage.Rejected -> when (val cause = reason) {
        DatasetRejection.Empty -> context.getString(R.string.dataset_rejected_empty)
        is DatasetRejection.WrongFormat ->
            context.getString(R.string.dataset_rejected_format, kind.fileName)
        is DatasetRejection.UnsupportedFormatVersion ->
            context.getString(
                R.string.dataset_rejected_version,
                cause.found,
                cause.supported,
            )
        is DatasetRejection.ChecksumMismatch ->
            context.getString(R.string.dataset_rejected_checksum)
        is DatasetRejection.TransferFailed ->
            context.getString(R.string.dataset_rejected_transfer)
    }
}
