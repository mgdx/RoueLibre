package io.github.mgdx.rouelibre.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.mgdx.rouelibre.BuildConfig
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.databinding.FragmentWelcomeBinding
import io.github.mgdx.rouelibre.ui.city.CityFragment
import io.github.mgdx.rouelibre.ui.map.MapFragment
import kotlinx.coroutines.launch

/**
 * The welcome screen on the very first start (SPEC §7.9).
 *
 * A screen and not a dialog: the content is too dense for a modal window, and
 * it must be readable again from "about".
 *
 * Three pages at most, each skippable, and the last leads straight into
 * obtaining the data — a single sequence, not two successive walls of text.
 *
 * The tone is §7's: short sentences, active voice, no jargon. We explain how
 * something works, we are not selling anything.
 */
class WelcomeFragment : Fragment() {

    private var binding: FragmentWelcomeBinding? = null

    private var page = 0

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentWelcomeBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        page = savedInstanceState?.getInt(STATE_PAGE) ?: 0
        val views = checkNotNull(binding)

        views.next.setOnClickListener { onNext() }
        views.skip.setOnClickListener { onSkip() }
        showPage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, page)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun showPage() {
        val views = binding ?: return
        val current = PAGES[page]
        views.title.setText(current.title)
        views.body.setText(current.body)
        views.step.text = getString(R.string.welcome_step, page + 1, PAGES.size)
        views.next.setText(current.next)
        views.skip.setText(current.skip)
        // The very last page offers the download; the earlier ones have
        // nothing to postpone, only to be skipped.
        views.skip.isVisible = true
        views.bodyContainer.scrollTo(0, 0)
    }

    private fun onNext() {
        if (page < PAGES.lastIndex) {
            page++
            showPage()
            return
        }
        // Last page: the city is chosen first, since it determines which data
        // to fetch. The next screen follows on from it.
        finish(CityFragment())
    }

    private fun onSkip() {
        if (page < PAGES.lastIndex) {
            page = PAGES.lastIndex
            showPage()
            return
        }
        // "Later": the application stays usable in degraded mode, with the
        // station list and their availability (SPEC §4.4).
        finish(MapFragment())
    }

    /**
     * Closes the welcome screen and remembers it has been seen.
     *
     * The version is remembered here, not at launch: somebody who leaves the
     * application in the middle of the sequence must see it again.
     */
    private fun finish(next: Fragment) {
        viewLifecycleOwner.lifecycleScope.launch {
            container.preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, next)
                .commit()
        }
    }

    /** One page of the welcome screen. */
    private data class Page(val title: Int, val body: Int, val next: Int, val skip: Int)

    private companion object {
        const val STATE_PAGE = "page"

        /**
         * The three pages, in order: what the application is, what it does not
         * do with your data, and what it needs in order to work.
         */
        val PAGES = listOf(
            Page(
                title = R.string.welcome_hello_title,
                body = R.string.welcome_hello_body,
                next = R.string.welcome_continue,
                skip = R.string.welcome_skip,
            ),
            Page(
                title = R.string.welcome_privacy_title,
                body = R.string.welcome_privacy_body,
                next = R.string.welcome_continue,
                skip = R.string.welcome_skip,
            ),
            Page(
                title = R.string.welcome_data_title,
                body = R.string.welcome_data_body,
                next = R.string.welcome_choose_city,
                skip = R.string.welcome_later,
            ),
        )
    }
}
