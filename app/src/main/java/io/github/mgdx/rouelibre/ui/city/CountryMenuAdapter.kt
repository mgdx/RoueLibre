package io.github.mgdx.rouelibre.ui.city

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.ui.flaggedText

/**
 * The entries of the country menu over the city list, flag first.
 *
 * @param labels what each entry shows, the flag included; it is read aloud
 *   without it, as every flagged label is.
 */
class CountryMenuAdapter(context: Context, private val labels: List<String>) :
    ArrayAdapter<String>(context, R.layout.item_country_option, labels) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        view.flaggedText = labels[position]
        return view
    }

    /**
     * Offers every country whatever the field shows.
     *
     * An auto-complete field filters its menu by its own text, and here that
     * text is the country already chosen: left to itself, the menu would
     * offer that one country and nothing else.
     */
    override fun getFilter(): Filter = everyEntry

    private val everyEntry = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults =
            FilterResults().apply {
                values = labels
                count = labels.size
            }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) =
            notifyDataSetChanged()
    }
}
