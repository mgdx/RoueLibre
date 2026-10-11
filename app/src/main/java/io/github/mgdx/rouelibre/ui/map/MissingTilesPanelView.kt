package io.github.mgdx.rouelibre.ui.map

import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.config.ActiveCity
import io.github.mgdx.rouelibre.databinding.FragmentMapBinding
import io.github.mgdx.rouelibre.ui.city.CityFragment
import io.github.mgdx.rouelibre.ui.noLongerServedMessage
import io.github.mgdx.rouelibre.ui.noLongerServedTitle
import io.github.mgdx.rouelibre.ui.storage.StorageFragment

/**
 * Raises the panel that covers a map without a base map, or takes it down, and
 * says in it what [missingTilesPanel] settled.
 *
 * @param hasTiles whether the base map is there — the panel then stands down
 *   and the attribution takes its place.
 * @param open shows the screen the panel's first button leads to.
 */
internal fun FragmentMapBinding.showMissingTiles(
    city: ActiveCity,
    panel: MissingTilesPanelContent,
    hasTiles: Boolean,
    open: (Fragment) -> Unit,
) {
    missingTiles.isVisible = !hasTiles
    attribution.isVisible = hasTiles
    when (panel.reason) {
        MissingTilesReason.CityGone -> {
            val gone = city as ActiveCity.NoLongerServed
            missingTilesTitle.setText(noLongerServedTitle(gone))
            missingTilesMessage.text = root.context.noLongerServedMessage(gone)
            missingTilesStorage.setText(R.string.city_choose_another)
            missingTilesStorage.setOnClickListener { open(CityFragment()) }
        }

        MissingTilesReason.NoCity -> {
            missingTilesTitle.setText(R.string.map_needs_city_title)
            missingTilesMessage.setText(R.string.map_needs_city_message)
            missingTilesStorage.setText(R.string.city_choose)
            missingTilesStorage.setOnClickListener { open(CityFragment()) }
        }

        MissingTilesReason.Downloading -> {
            missingTilesTitle.setText(R.string.map_downloading_title)
            missingTilesMessage.setText(R.string.map_downloading_message)
            missingTilesStorage.setText(R.string.storage_open)
            missingTilesStorage.setOnClickListener { open(StorageFragment()) }
        }

        MissingTilesReason.NoTiles -> {
            missingTilesTitle.setText(R.string.map_needs_tiles_title)
            missingTilesMessage.setText(R.string.map_needs_tiles_message)
            missingTilesStorage.setText(R.string.storage_open)
            missingTilesStorage.setOnClickListener { open(StorageFragment()) }
        }
    }
    missingTilesMessage.isVisible = panel.showsMessage
    missingTilesList.isVisible = panel.showsWayOut
}
