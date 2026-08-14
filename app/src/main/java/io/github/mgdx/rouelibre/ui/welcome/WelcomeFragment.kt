package io.github.mgdx.rouelibre.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
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
 * Four pages at most, each skippable, and the last leads straight into
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
        views.illustration.setImageResource(current.illustration)
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
        // to fetch. The map goes underneath rather than being replaced by it —
        // going back from the city then lands on the application, not outside
        // it.
        finish(MapFragment(), then = CityFragment())
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
     *
     * @param next the screen that becomes the root.
     * @param then a screen opened on top of it, which Back closes.
     */
    private fun finish(next: Fragment, then: Fragment? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            container.preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            // The screens visited before are dropped. Replayed from the about
            // screen, the sequence used to leave that screen on the stack: the
            // first Back brought it up again over the screen just opened, two
            // screens drawn on top of one another. Coming out of the
            // presentation is a fresh start, not a step in a path.
            parentFragmentManager.popBackStack(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE,
            )
            // Reordering allowed, and it matters: without it the screen placed
            // underneath is built and resumed for the instant between the two
            // transactions, long enough to ask the network for stations no city
            // has been chosen for yet — a request from a screen nobody is
            // looking at, which SPEC §4.1 rules out.
            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.content, next)
                .commit()
            if (then != null) {
                parentFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.content, then)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    /**
     * One page of the welcome screen.
     *
     * The drawing is part of the page and not of the layout: what a page says
     * is a shape before it is a paragraph, and the reader who skips the text
     * still leaves with it.
     */
    private data class Page(
        val title: Int,
        val body: Int,
        val illustration: Int,
        val next: Int,
        val skip: Int,
    )

    private companion object {
        const val STATE_PAGE = "page"

        /**
         * The four pages, in order: what the application is, what it does not
         * do with your data, how to read the bike it draws, and what it needs
         * in order to work.
         */
        val PAGES = listOf(
            Page(
                title = R.string.welcome_hello_title,
                body = R.string.welcome_hello_body,
                illustration = R.drawable.illustration_welcome_hello,
                next = R.string.welcome_continue,
                skip = R.string.welcome_skip,
            ),
            Page(
                title = R.string.welcome_privacy_title,
                body = R.string.welcome_privacy_body,
                illustration = R.drawable.illustration_welcome_privacy,
                next = R.string.welcome_continue,
                skip = R.string.welcome_skip,
            ),
            Page(
                title = R.string.welcome_fleet_title,
                body = R.string.welcome_fleet_body,
                illustration = R.drawable.illustration_welcome_fleet,
                next = R.string.welcome_continue,
                skip = R.string.welcome_skip,
            ),
            Page(
                title = R.string.welcome_data_title,
                body = R.string.welcome_data_body,
                illustration = R.drawable.illustration_welcome_data,
                next = R.string.welcome_choose_city,
                skip = R.string.welcome_later,
            ),
        )
    }
}
