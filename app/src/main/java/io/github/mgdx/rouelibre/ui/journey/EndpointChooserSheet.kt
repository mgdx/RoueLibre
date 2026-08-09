package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.databinding.SheetEndpointChooserBinding

/**
 * Comment désigner un départ ou une arrivée (SPEC §7.3).
 *
 * Quatre façons, et le SPEC les veut toutes : sa position, un favori, un point
 * choisi sur la carte, une adresse. Elles ne se valent pas selon le moment —
 * on connaît son adresse d'arrivée mais rarement celle de son point de départ,
 * où l'on est déjà.
 *
 * La feuille ne fait que rendre le choix ; c'est l'écran de recherche qui sait
 * quoi en faire.
 */
class EndpointChooserSheet : BottomSheetDialogFragment() {

    private var binding: SheetEndpointChooserBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = SheetEndpointChooserBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)
        views.title.setText(
            if (isForOrigin()) {
                R.string.journey_choose_origin
            } else {
                R.string.journey_choose_destination
            },
        )
        views.myPosition.setOnClickListener { choose(SOURCE_MY_POSITION) }
        views.favourite.setOnClickListener { choose(SOURCE_FAVOURITE) }
        views.onMap.setOnClickListener { choose(SOURCE_ON_MAP) }
        views.address.setOnClickListener { choose(SOURCE_ADDRESS) }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun isForOrigin(): Boolean = requireArguments().getBoolean(ARGUMENT_IS_ORIGIN)

    private fun choose(source: String) {
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(RESULT_SOURCE, source)
                putBoolean(RESULT_IS_ORIGIN, isForOrigin())
            },
        )
        dismiss()
    }

    companion object {
        /** Clé sous laquelle la façon choisie est rendue. */
        const val REQUEST_KEY: String = "source-point"

        /** La façon retenue, l'une des quatre constantes `SOURCE_`. */
        const val RESULT_SOURCE: String = "source"

        /** Vrai si le choix portait sur le départ. */
        const val RESULT_IS_ORIGIN: String = "est-depart"

        /** Se placer là où l'on est. */
        const val SOURCE_MY_POSITION: String = "ma-position"

        /** Choisir une station mise en favori. */
        const val SOURCE_FAVOURITE: String = "favori"

        /** Désigner un point sur la carte. */
        const val SOURCE_ON_MAP: String = "carte"

        /** Chercher une adresse dans l'index hors ligne. */
        const val SOURCE_ADDRESS: String = "adresse"

        /** Étiquette sous laquelle la feuille est ajoutée au gestionnaire. */
        const val TAG: String = "choix-point"

        private const val ARGUMENT_IS_ORIGIN = "est-depart"

        /** Ouvre la feuille pour le départ ou pour l'arrivée. */
        fun newInstance(isOrigin: Boolean): EndpointChooserSheet = EndpointChooserSheet().apply {
            arguments = Bundle().apply { putBoolean(ARGUMENT_IS_ORIGIN, isOrigin) }
        }
    }
}
