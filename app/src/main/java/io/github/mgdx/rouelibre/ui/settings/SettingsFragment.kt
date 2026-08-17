package io.github.mgdx.rouelibre.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.journey.WalkingPace
import io.github.mgdx.rouelibre.core.measure.UnitChoice
import io.github.mgdx.rouelibre.data.AppTheme
import io.github.mgdx.rouelibre.data.OpeningScreen
import io.github.mgdx.rouelibre.data.OwnBikeKind
import io.github.mgdx.rouelibre.databinding.FragmentSettingsBinding
import io.github.mgdx.rouelibre.ui.about.AboutFragment
import io.github.mgdx.rouelibre.ui.chosenLanguage
import io.github.mgdx.rouelibre.ui.city.CityFragment
import io.github.mgdx.rouelibre.ui.endonym
import io.github.mgdx.rouelibre.ui.offeredLanguages
import io.github.mgdx.rouelibre.ui.speakLanguage
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Settings (SPEC §7.6).
 *
 * Written by hand rather than with `androidx.preference`: that library brings
 * its own visual grammar, which the project's design tokens would then have to
 * fight, for a dozen settings that fit on one screen.
 *
 * Every change is saved immediately. There is no "apply" button: a setting one
 * has changed is a setting one wants.
 *
 * The screen is laid out in sections (SPEC §7.6) and this class follows that
 * order, one short function per setting. Wiring nine settings inside
 * [onViewCreated] would make a wall out of the one place that says what the
 * screen holds.
 */
class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null

    private val preferences
        get() = container.preferences

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    /** True while a field is being filled by the code, so as not to rewrite it. */
    private var isFilling = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentSettingsBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        setUpToolbar(views)
        // In the order the screen reads them (SPEC §7.6): city, journey,
        // display, offline data, then the way to "about".
        setUpCity(views)
        setUpWalkingPace(views)
        setUpOwnBikeKind(views)
        setUpTheme(views)
        setUpUnits(views)
        setUpLanguage(views)
        setUpOpeningScreen(views)
        setUpStationFilters(views)
        setUpOfflineData(views)
        setUpDownloadPolicy(views)
        setUpAbout(views)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun setUpToolbar(views: FragmentSettingsBinding) {
        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)
    }

    /** The city section: which network is served (SPEC §15.1). */
    private fun setUpCity(views: FragmentSettingsBinding) {
        views.openCity.setOnClickListener { show(CityFragment()) }
    }

    /**
     * The walking pace, in the journey section (SPEC §6, §7.6).
     *
     * Written the moment it is pressed, on the theme's pattern and with no
     * "apply" button. Nothing is applied here, though, and that is the whole
     * difference with the two settings below: this one changes no screen already
     * drawn. It is read again when the next journey is worked out — the model
     * collects it from the preferences — so a pace changed here reaches the next
     * journey and leaves the one on show as it was computed.
     */
    private fun setUpWalkingPace(views: FragmentSettingsBinding) {
        views.walkingPace.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isFilling) return@addOnButtonCheckedListener
            val pace = when (checkedId) {
                R.id.walking_pace_slow -> WalkingPace.Slow
                R.id.walking_pace_brisk -> WalkingPace.Brisk
                else -> WalkingPace.Normal
            }
            viewLifecycleOwner.lifecycleScope.launch { preferences.setWalkingPace(pace) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.walkingPace.collect { pace ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.walkingPace.check(
                        when (pace) {
                            WalkingPace.Slow -> R.id.walking_pace_slow
                            WalkingPace.Normal -> R.id.walking_pace_normal
                            WalkingPace.Brisk -> R.id.walking_pace_brisk
                        },
                    )
                    isFilling = false
                }
            }
        }
    }

    /**
     * What the rider's own bike is, in the journey section (SPEC §7.3, §7.6).
     *
     * **Not the kind of bike asked of the network**, which lives on the journey
     * screen: that one exists only where the network lends both kinds and it
     * narrows the stations §6 may choose. This one is a fact about the rider —
     * their bike belongs to no fleet and is the same in every city — so it is
     * offered everywhere and asks nothing of `FleetDescription.isMixed`.
     *
     * It is also the one setting of this section that reaches no computation:
     * written the moment it is pressed, like the pace above, but read only by
     * the drawings and the sentences of a journey on one's own bike. Not a
     * minute announced depends on it (SPEC §6).
     */
    private fun setUpOwnBikeKind(views: FragmentSettingsBinding) {
        views.ownBikeKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
            // Every change fires twice — the button left, then the one taken —
            // and only the second says what was chosen.
            if (!isChecked || isFilling) return@addOnButtonCheckedListener
            val kind = when (checkedId) {
                R.id.own_bike_kind_mechanical -> OwnBikeKind.Mechanical
                R.id.own_bike_kind_electric -> OwnBikeKind.Electric
                else -> null
            }
            viewLifecycleOwner.lifecycleScope.launch { preferences.setOwnBikeKind(kind) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.ownBikeKind.collect { kind ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.ownBikeKind.check(
                        when (kind) {
                            null -> R.id.own_bike_kind_unspecified
                            OwnBikeKind.Mechanical -> R.id.own_bike_kind_mechanical
                            OwnBikeKind.Electric -> R.id.own_bike_kind_electric
                        },
                    )
                    isFilling = false
                }
            }
        }
    }

    /** The offline data section: what is installed, and how to reclaim it (SPEC §4.4). */
    private fun setUpOfflineData(views: FragmentSettingsBinding) {
        views.openStorage.setOnClickListener { show(StorageFragment()) }
    }

    /**
     * What connection the datasets may travel on (SPEC §4.4, §7.6).
     *
     * Written the moment it is pressed, like every setting here, and it changes
     * no screen already drawn: what it settles is what the storage screen does
     * at the next press of its download button. On by default, so a gigabyte
     * never leaves on a mobile plan nobody meant to spend — and never a dead
     * end, since that screen offers the transfer anyway when it holds one back.
     */
    private fun setUpDownloadPolicy(views: FragmentSettingsBinding) {
        views.downloadUnmeteredOnly.setOnCheckedChangeListener { _, isChecked ->
            if (isFilling) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setDownloadOnUnmeteredOnly(isChecked)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.downloadOnUnmeteredOnly.collect { unmeteredOnly ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.downloadUnmeteredOnly.isChecked = unmeteredOnly
                    isFilling = false
                }
            }
        }
    }

    /** The way to "about" (SPEC §7.7), which belongs to no section. */
    private fun setUpAbout(views: FragmentSettingsBinding) {
        views.openAbout.setOnClickListener { show(AboutFragment()) }
    }

    /**
     * The theme, in the display section (SPEC §7.6).
     *
     * Written before being applied, and the order is what matters: see the
     * comment in the listener below, which documents a bug already fixed once.
     */
    private fun setUpTheme(views: FragmentSettingsBinding) {
        views.theme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isFilling) return@addOnButtonCheckedListener
            val theme = when (checkedId) {
                R.id.theme_light -> AppTheme.Light
                R.id.theme_dark -> AppTheme.Dark
                else -> AppTheme.System
            }
            viewLifecycleOwner.lifecycleScope.launch {
                // Written BEFORE being applied, and in that order. Applying a
                // theme has the activity rebuilt, which cancels this scope: the
                // write, started first and awaited nowhere, never reached the
                // disk, and coming back to this screen showed the old choice
                // ticked under the new theme.
                preferences.setTheme(theme)
                // Applied at once: a theme one chooses must show, not wait for
                // the next launch.
                applyTheme(theme)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.theme.collect { theme ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.theme.check(
                        when (theme) {
                            AppTheme.Light -> R.id.theme_light
                            AppTheme.Dark -> R.id.theme_dark
                            AppTheme.System -> R.id.theme_system
                        },
                    )
                    isFilling = false
                }
            }
        }
    }

    /**
     * The units distances are written in, beside the theme in the display
     * section (SPEC §7.6, §9).
     *
     * Written immediately, like the theme and for the same reason, and applied
     * without waiting for the next launch: the interface is rebuilt on the new
     * units by `MainActivity`, which watches them. Nothing else moves — the
     * journey, the stations chosen and the minutes announced are all worked out
     * in metres and are not asked again (SPEC §14).
     */
    private fun setUpUnits(views: FragmentSettingsBinding) {
        views.units.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isFilling) return@addOnButtonCheckedListener
            val choice = when (checkedId) {
                R.id.units_metric -> UnitChoice.Metric
                R.id.units_us -> UnitChoice.UnitedStates
                R.id.units_uk -> UnitChoice.UnitedKingdom
                else -> UnitChoice.FollowSystem
            }
            // The trap the theme above documents does not apply here, and it
            // is worth saying why: there the write and the rebuild were two
            // steps of one coroutine, and the second cancelled the first. Here
            // the rebuild is caused by the write having landed — the interface
            // follows the stored value — so nothing can rebuild ahead of it.
            viewLifecycleOwner.lifecycleScope.launch { preferences.setUnits(choice) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.units.collect { choice ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.units.check(
                        when (choice) {
                            UnitChoice.FollowSystem -> R.id.units_system
                            UnitChoice.Metric -> R.id.units_metric
                            UnitChoice.UnitedStates -> R.id.units_us
                            UnitChoice.UnitedKingdom -> R.id.units_uk
                        },
                    )
                    isFilling = false
                }
            }
        }
    }

    /**
     * The language the interface speaks, under the units in the display section
     * (SPEC §7.6, §9).
     *
     * Nothing is read from or written to the preferences here, alone among the
     * settings on this screen, and [chosenLanguage] says why: AppCompat stores
     * this one itself, and a second copy of it would diverge the first time the
     * language was changed from Android's own per-application settings.
     *
     * Nor is there anything to collect: applying a language rebuilds the
     * activity, so this fragment is created afresh on the new choice and reads
     * it once, here.
     */
    private fun setUpLanguage(views: FragmentSettingsBinding) {
        showLanguage(views, chosenLanguage())
        views.language.setOnClickListener { chooseLanguage() }
    }

    /** Writes the language in service on the row that opens the list. */
    private fun showLanguage(views: FragmentSettingsBinding, language: Locale?) {
        val name = language?.endonym() ?: getString(R.string.settings_language_system)
        views.language.text = name
        // The row reads as a bare language name otherwise — "Français" alone,
        // with nothing saying what it settles.
        views.language.contentDescription =
            getString(R.string.settings_language_description, name)
    }

    /**
     * Offers the languages the interface exists in.
     *
     * The list is [offeredLanguages] and is derived from the translations that
     * exist, never written out here: offering a language to answer in English
     * would be worse than not offering it. "Follow the system" heads it, as it
     * heads the theme and the units, and is what an unknown or absent choice
     * reads as.
     *
     * The choice applies on the press, with no "apply" button and nothing to
     * confirm, so the list closes on it.
     */
    private fun chooseLanguage() {
        val offered = offeredLanguages()
        val names = (
            listOf(getString(R.string.settings_language_system)) + offered.map { it.endonym() }
            ).toTypedArray()
        val chosen = chosenLanguage()
        val ticked =
            if (chosen == null) 0 else offered.indexOfFirst { it.language == chosen.language } + 1
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_language_title)
            .setSingleChoiceItems(names, ticked) { dialog, which ->
                dialog.dismiss()
                // Index 0 is "follow the system", which is the absence of a
                // language rather than one of them.
                speakLanguage(offered.getOrNull(which - 1))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /**
     * The screen the application opens on, in the display section
     * (SPEC §7.0, §7.6).
     *
     * Written the moment it is pressed, like the theme and the units, and like
     * the walking pace it applies to nothing already on screen: it is read once,
     * by the activity, at the launch after this one. Nothing is rebuilt here —
     * the screen one is standing on is not the screen one opens with.
     */
    private fun setUpOpeningScreen(views: FragmentSettingsBinding) {
        views.openingScreen.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isFilling) return@addOnButtonCheckedListener
            val screen = when (checkedId) {
                R.id.opening_screen_list -> OpeningScreen.StationList
                else -> OpeningScreen.Map
            }
            viewLifecycleOwner.lifecycleScope.launch { preferences.setOpeningScreen(screen) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.openingScreen.collect { screen ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.openingScreen.check(
                        when (screen) {
                            OpeningScreen.Map -> R.id.opening_screen_map
                            OpeningScreen.StationList -> R.id.opening_screen_list
                        },
                    )
                    isFilling = false
                }
            }
        }
    }

    /**
     * Which stations the map draws at all, in the display section (SPEC §7.1,
     * §7.6).
     *
     * Written the moment a switch is pressed, like everything else on this
     * screen, and kept from one session to the next like the theme and the
     * units. The map follows the stored value itself, so the markers are already
     * back — or already gone — on the map one returns to.
     *
     * **This screen is the only place either filter is visible at all.** The map
     * carries no control and no witness, which SPEC §7.1 settles and names as a
     * compromise rather than an oversight: a filter left on for weeks explains
     * itself here or nowhere. Hence the two lines under the switches, which are
     * part of the setting and not decoration.
     */
    private fun setUpStationFilters(views: FragmentSettingsBinding) {
        views.hideOutOfServiceStations.setOnCheckedChangeListener { _, isChecked ->
            if (isFilling) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setHideOutOfServiceStations(isChecked)
            }
        }
        views.hideEmptyStations.setOnCheckedChangeListener { _, isChecked ->
            if (isFilling) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setHideEmptyStations(isChecked)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.hideOutOfServiceStations.collect { hide ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.hideOutOfServiceStations.isChecked = hide
                    isFilling = false
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.hideEmptyStations.collect { hide ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.hideEmptyStations.isChecked = hide
                    isFilling = false
                }
            }
        }
    }

    private fun show(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }
}
