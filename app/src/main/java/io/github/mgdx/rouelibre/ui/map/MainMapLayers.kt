package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

/**
 * The main map's own layers, laid over a style that has just loaded, and the
 * sources the screen draws into (SPEC §7.1).
 *
 * The layers are the style's, so they go with it: a style built again over a
 * new base map needs them all added again, and the sources handed back are
 * that style's and no other's. They are laid in one place because their order
 * is a decision — what is drawn over what — and is read best whole.
 *
 * The sources are handed back empty: filling them is the screen's business,
 * from what it holds.
 *
 * @property stations the stations, clustered by MapLibre.
 * @property streetBikes the bikes outside stations, clustered alike.
 * @property savedPlaces the places the user has named.
 * @property userPosition draws the user's point and its circle.
 */
internal class MainMapLayers private constructor(
    val stations: GeoJsonSource,
    val streetBikes: GeoJsonSource,
    val savedPlaces: GeoJsonSource,
    val userPosition: UserPositionDisplay,
) {

    companion object {
        /**
         * Lays every layer of the main map over [style], in drawing order.
         *
         * @param pickedPlace lays the searched point's own layer, in its place
         *   in that order.
         */
        fun addTo(
            style: Style,
            context: Context,
            pickedPlace: PickedPlaceController,
        ): MainMapLayers {
            val stations = clusteredSource(StationMarkers.SOURCE_ID)
            style.addSource(stations)
            // The bikes outside stations go down FIRST, so the stations are
            // drawn over them: a rack of bikes is what this map is for, and a
            // marker standing for one bike must never hide one standing for
            // twenty.
            val streetBikes = addStreetBikeLayers(style, context)
            addStationLayers(style, context)

            // The named places go over the stations, and under the point
            // searched for: that one is what the user has just asked for.
            val saved = GeoJsonSource(
                SavedPlaceMarkers.SOURCE_ID,
                SavedPlaceMarkers.featuresFor(emptyMap()),
            )
            style.addSource(saved)
            SavedPlaceMarkers.registerImages(context, style)
            style.addLayer(SavedPlaceMarkers.layer())

            // The searched point is laid down AFTER the stations: it is what
            // the user has just asked for, so it goes in front.
            pickedPlace.addTo(style, context)

            return MainMapLayers(
                stations = stations,
                streetBikes = streetBikes,
                savedPlaces = saved,
                userPosition = addUserPositionLayers(style, context),
            )
        }

        /**
         * The station layers, over a source already installed.
         *
         * Clustering is left to MapLibre (SPEC §7.1): past the zoom given,
         * nearby stations merge into a cluster bearing their count.
         */
        private fun addStationLayers(style: Style, context: Context) {
            style.addLayer(StationMarkers.circleLayer(context))
            style.addLayer(StationMarkers.countLayer(context))
            style.addLayer(StationMarkers.clusterLayer(context))
            style.addLayer(StationMarkers.clusterCountLayer(context))
        }

        /**
         * Installs the source and the layers of the bikes outside stations.
         *
         * They are installed whatever the setting says, and the source is
         * simply left empty while the bikes are not shown: that is one state of
         * one object, where layers coming and going with a switch set on
         * another screen would be a style rebuilt from under the map.
         *
         * Clustering is MapLibre's, with the stations' own radius and zoom
         * (SPEC §7.1): the two kinds of marker collapse at the same moment, so
         * the map does not read as two maps laid on each other.
         */
        private fun addStreetBikeLayers(style: Style, context: Context): GeoJsonSource {
            val source = clusteredSource(StreetBikeMarkers.SOURCE_ID)
            style.addSource(source)
            style.addLayer(StreetBikeMarkers.circleLayer(context))
            StreetBikeMarkers.registerImage(context, style)
            style.addLayer(StreetBikeMarkers.boltLayer())
            style.addLayer(StreetBikeMarkers.clusterLayer(context))
            style.addLayer(StreetBikeMarkers.clusterCountLayer(context))
            return source
        }

        /** The user's point, on top of everything else. */
        private fun addUserPositionLayers(style: Style, context: Context): UserPositionDisplay {
            // The circle of uncertainty goes down before the disc: it is the
            // ground the point may be standing on, and the point stands on top
            // of it.
            val userAccuracy = GeoJsonSource(
                UserPositionMarker.ACCURACY_SOURCE_ID,
                UserPositionMarker.accuracyFeatureFor(null),
            )
            style.addSource(userAccuracy)
            style.addLayer(UserPositionMarker.accuracyLayer(context))

            val userPoint = GeoJsonSource(
                UserPositionMarker.SOURCE_ID,
                UserPositionMarker.featureFor(null),
            )
            style.addSource(userPoint)
            // The direction cone goes under the disc: its tip peeks out, its
            // base is the disc's business.
            UserPositionMarker.registerImage(context, style)
            style.addLayer(UserPositionMarker.bearingLayer())
            style.addLayer(UserPositionMarker.layer(context))
            return UserPositionDisplay(userPoint, userAccuracy)
        }

        private fun clusteredSource(id: String) = GeoJsonSource(
            id,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions()
                .withCluster(true)
                .withClusterRadius(CLUSTER_RADIUS)
                .withClusterMaxZoom(CLUSTER_MAX_ZOOM),
        )

        /**
         * The clustering radius, in pixels. Fifty keeps the stations of central
         * Lille distinct as soon as one comes near, without making the overview
         * swarm.
         */
        private const val CLUSTER_RADIUS = 50

        /** Past this, every station takes back its own marker. */
        private const val CLUSTER_MAX_ZOOM = 13
    }
}
