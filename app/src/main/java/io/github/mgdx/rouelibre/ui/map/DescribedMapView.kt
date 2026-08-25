package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import android.util.AttributeSet
import io.github.mgdx.rouelibre.R
import org.maplibre.android.maps.MapView

/**
 * The map view, told to describe itself in the application's own words
 * (SPEC §9, §11).
 *
 * MapLibre gives its `MapView` a content description of its own — the sentence
 * a screen reader announces — and it is translated into the library's
 * languages, not into ours. Under an Arabic interface the map therefore spoke
 * English, because MapLibre ships no Arabic, while every other view on the
 * screen spoke Arabic. The languages the application declares are its own
 * business (SPEC §9), so the sentence has to be one of ours.
 *
 * A `android:contentDescription` in the layout would not do it: `MapView` sets
 * its description inside its constructor, which runs *after* `View` has read
 * the attributes the layout gave it, so the library's sentence would win.
 * Setting it here, once the superclass is built, is what puts ours last — and
 * putting it in the view rather than in each fragment means the map screen and
 * the journey result cannot drift apart.
 */
class DescribedMapView : MapView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attributes: AttributeSet?) : super(context, attributes)

    constructor(context: Context, attributes: AttributeSet?, style: Int) :
        super(context, attributes, style)

    init {
        contentDescription = context.getString(R.string.map_description)
    }
}
