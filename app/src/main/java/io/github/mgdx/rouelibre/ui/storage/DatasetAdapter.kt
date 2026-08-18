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
import io.github.mgdx.rouelibre.core.data.DatasetUpdate
import io.github.mgdx.rouelibre.databinding.ItemDatasetBinding
import io.github.mgdx.rouelibre.ui.textLocale
import io.github.mgdx.rouelibre.ui.toDownloadMessage
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Shows the three datasets and their actions. */
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

    /** One dataset row. */
    class DatasetViewHolder(
        private val binding: ItemDatasetBinding,
        private val onImport: (DatasetKind) -> Unit,
        private val onDelete: (DatasetKind) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Fills the row from a set's state. */
        fun bind(row: DatasetRow) {
            val context = binding.root.context
            val name = context.getString(row.kind.nameResource())

            binding.datasetName.text = name
            binding.datasetPurpose.setText(row.kind.purposeResource())

            val installed = row.installed
            binding.datasetState.text = when {
                installed == null -> context.getString(R.string.dataset_absent)

                // The manifest has been checked and announces something else:
                // say so on the row concerned, rather than in bulk (SPEC §4.4).
                row.update == DatasetUpdate.Outdated ->
                    context.getString(R.string.dataset_update_available)

                else -> context.getString(
                    R.string.dataset_installed,
                    formatBytes(context, installed.sizeBytes),
                    dateFormatFor(context)
                        .format(installed.installedAt.atZone(ZoneId.systemDefault())),
                )
            }

            // The label says what will happen: install where there is nothing,
            // replace where something is already there.
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
         * Formats the date in the language the application actually serves.
         *
         * The system language will not do: on a device set to English it
         * produced "August 9, 2026" in the middle of a French interface.
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

/** A dataset's displayed name. */
fun DatasetKind.nameResource(): Int = when (this) {
    DatasetKind.Tiles -> R.string.dataset_tiles
    DatasetKind.Routing -> R.string.dataset_routing
    DatasetKind.Addresses -> R.string.dataset_addresses
}

/** What a dataset is for, in one line. */
fun DatasetKind.purposeResource(): Int = when (this) {
    DatasetKind.Tiles -> R.string.dataset_tiles_purpose
    DatasetKind.Routing -> R.string.dataset_routing_purpose
    DatasetKind.Addresses -> R.string.dataset_addresses_purpose
}

/**
 * Formats a file size for display.
 *
 * Base 1000 and not 1024: that is the unit sizes are announced in to the user
 * everywhere else, the system included.
 *
 * Both the number and its unit come from the displayed language, not the
 * system's: the decimal separator differs — "35,0 Mo" in French — and so does
 * the unit itself, which is why it is a string resource rather than a literal.
 */
fun formatBytes(context: Context, bytes: Long): String {
    val locale = context.textLocale()
    val (unit, value) = when {
        bytes < 1_000 -> R.string.size_bytes to String.format(locale, "%d", bytes)
        bytes < 1_000_000 ->
            R.string.size_kilobytes to String.format(locale, "%.0f", bytes / 1_000.0)
        bytes < 1_000_000_000 ->
            R.string.size_megabytes to String.format(locale, "%.1f", bytes / 1_000_000.0)
        else -> R.string.size_gigabytes to String.format(locale, "%.2f", bytes / 1_000_000_000.0)
    }
    return context.getString(unit, value)
}

/**
 * Puts the outcome of a storage-screen action into words.
 *
 * Every refusal says what is wrong and what to do, never a technical code
 * (SPEC §14).
 */
/** What the generation scripts produce for the routing graph. */
private const val EXPECTED_ROUTING_FILE = "*.rd5"

fun StorageMessage.toText(context: Context): String = when (this) {
    is StorageMessage.Installed ->
        context.getString(R.string.dataset_imported, context.getString(kind.nameResource()))

    is StorageMessage.Deleted ->
        context.getString(R.string.dataset_deleted, context.getString(kind.nameResource()))

    is StorageMessage.CheckFailed -> error.toDownloadMessage(context)

    is StorageMessage.UnsupportedFormat -> context.getString(
        R.string.dataset_rejected_version,
        found,
        supported,
    )

    StorageMessage.AlreadyUpToDate -> context.getString(R.string.storage_up_to_date)

    is StorageMessage.HeldBackByMetering -> context.getString(
        if (wasUnderWay) R.string.download_stopped_body else R.string.download_held_back_body,
        formatBytes(context, pendingBytes),
    )

    StorageMessage.CanResumeOnUnmetered -> context.getString(R.string.download_can_resume)

    is StorageMessage.DownloadFailed -> context.getString(
        R.string.storage_download_failed,
        context.getString(kind.nameResource()),
        error.toDownloadMessage(context),
    )

    is StorageMessage.Rejected -> when (val cause = reason) {
        DatasetRejection.Empty -> context.getString(R.string.dataset_rejected_empty)
        is DatasetRejection.WrongFormat ->
            context.getString(
                R.string.dataset_rejected_format,
                kind.fileName ?: EXPECTED_ROUTING_FILE,
            )
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
