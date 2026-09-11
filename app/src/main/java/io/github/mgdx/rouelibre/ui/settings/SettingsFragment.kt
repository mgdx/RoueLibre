package io.github.mgdx.rouelibre.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.WalkingPace
import io.github.mgdx.rouelibre.core.measure.UnitChoice
import io.github.mgdx.rouelibre.data.AppTheme
import io.github.mgdx.rouelibre.data.OpeningScreen
import io.github.mgdx.rouelibre.data.OwnBikeKind
import io.github.mgdx.rouelibre.data.SavedPlace
import io.github.mgdx.rouelibre.data.SavedPlaceKind
import io.github.mgdx.rouelibre.databinding.FragmentSettingsBinding
import io.github.mgdx.rouelibre.ui.ChoiceDialogFragment
import io.github.mgdx.rouelibre.ui.about.AboutFragment
import io.github.mgdx.rouelibre.ui.address.AddressSearchFragment
import io.github.mgdx.rouelibre.ui.chosenLanguage
import io.github.mgdx.rouelibre.ui.city.CityFragment
import io.github.mgdx.rouelibre.ui.cityLabel
import io.github.mgdx.rouelibre.ui.endonym
import io.github.mgdx.rouelibre.ui.offeredLanguages
import io.github.mgdx.rouelibre.ui.speakLanguage
import io.github.mgdx.rouelibre.ui.stations.StreetBikeIntroDialogFragment
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import kotlinx.coroutines.flow.combine
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

    /**
     * Which of the two places the address search was opened for, if it was.
     *
     * Kept across both ways this screen goes away while one searches — the
     * unstacking, which leaves this very field standing, and the rebuild, which
     * does not and is what [onSaveInstanceState] is for. [awaitedPlace] weighs
     * the two, and must: an answer that comes back to no row writes nothing.
     */
    private var placeBeingNamed: SavedPlaceKind? = null

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

        // The bundle answers where it has an answer, and never erases what the
        // instance is still holding — [awaitedPlace] carries the why, and it is
        // not a simplification waiting to happen: reading the bundle straight
        // into the field is the version that shipped and that made this screen
        // unable to save a place at all. Going to the address search only
        // unstacks this screen, so the instance survives with its field set and
        // comes back with no bundle whatsoever.
        placeBeingNamed = awaitedPlace(
            held = placeBeingNamed,
            savedName = savedInstanceState?.getString(STATE_PLACE_BEING_NAMED),
        )

        setUpToolbar(views)
        // In the order the screen reads them (SPEC §7.6): city, display,
        // journey, offline data, then the way to "about".
        setUpCity(views)
        setUpLanguage(views)
        setUpTheme(views)
        setUpUnits(views)
        setUpOpeningScreen(views)
        setUpStationFilters(views)
        setUpStreetBikes(views)
        setUpOwnBikeKind(views)
        setUpWalkingPace(views)
        setUpSavedPlaces(views)
        setUpOfflineData(views)
        setUpDownloadPolicy(views)
        setUpAbout(views)
        listenForTheLanguageChosen()
        listenForTheAddressChosen()
    }

    /**
     * Collects the language picked from the list.
     *
     * Registered as the screen is built, and not where the list is put up: a
     * list the phone turned over on is already back by then, and its answer
     * would arrive with nobody listening for it.
     *
     * The index is read against [offeredLanguages] rather than against a copy
     * carried in the answer: that list is derived from the translations that
     * exist and ordered by each language's own name — a name that does not
     * depend on the language being spoken — so it comes back identical however
     * many times the screen is rebuilt.
     */
    private fun listenForTheLanguageChosen() {
        ChoiceDialogFragment.onAnswer(
            childFragmentManager,
            viewLifecycleOwner,
            LANGUAGE_ANSWER,
        ) { chosen, _ ->
            // Index 0 is "follow the system", which is the absence of a
            // language rather than one of them.
            speakLanguage(offeredLanguages().getOrNull(chosen - 1))
        }
    }

    /**
     * Collects the address picked for a saved place (SPEC §7.6).
     *
     * Registered as the screen is built, and not where the search is put up,
     * for the reason [io.github.mgdx.rouelibre.ui.journey.JourneyHandover]
     * gives of its own listener: the screen is destroyed while one searches —
     * a rotation is enough — so an answer collected at the press would arrive
     * with nobody listening for it. The mistake has been made in this
     * repository before.
     *
     * Nothing is written unless a row was waiting: the map and the journey
     * screens return their address under this same key, and a result meant for
     * one of them must not be taken for a home.
     */
    private fun listenForTheAddressChosen() {
        parentFragmentManager.setFragmentResultListener(
            AddressSearchFragment.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            val kind = placeBeingNamed ?: return@setFragmentResultListener
            placeBeingNamed = null
            val label = result.getString(AddressSearchFragment.RESULT_LABEL).orEmpty()
            // A place with nothing to show is a place no screen can offer, and
            // AppPreferences reads it back as no place at all: better never
            // written than written and silently lost.
            if (label.isBlank()) return@setFragmentResultListener
            val place = SavedPlace(
                label = label,
                position = Coordinates(
                    result.getDouble(AddressSearchFragment.RESULT_LATITUDE),
                    result.getDouble(AddressSearchFragment.RESULT_LONGITUDE),
                ),
            )
            viewLifecycleOwner.lifecycleScope.launch { preferences.setSavedPlace(kind, place) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        placeBeingNamed?.let { outState.putString(STATE_PLACE_BEING_NAMED, it.name) }
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
        viewLifecycleOwner.lifecycleScope.launch { showCity(container.activeCity()) }
    }

    /**
     * Writes the network in service on the row that opens the city list.
     *
     * The row said "change city", which is what pressing it does and not what
     * the reader came to this section to know: the section is titled "City"
     * and answered nothing. It now names the network, as the language row
     * names the language, so the screen answers without being pressed.
     *
     * The read is asynchronous — the configuration comes off the disk — and
     * the row is written when it returns rather than blocking the first draw
     * for it.
     *
     * @param city the conurbation served, or `null` if none is chosen yet.
     */
    private fun showCity(city: CityConfiguration?) {
        val views = binding ?: return
        if (city == null) {
            // Nothing to name. The row invites the choice instead, in the
            // words the welcome sequence uses to ask for it.
            views.openCity.setText(R.string.city_choose)
            // The invitation says what pressing it does, so the eye and the
            // ear are told the same thing and nothing is to be added.
            views.openCity.contentDescription = null
            return
        }
        val name = requireContext().cityLabel(city.network.displayName, city.network.city)
        views.openCity.text = name
        // The row reads as a bare network name otherwise — "V'lille — Lille"
        // alone, with nothing saying what it settles.
        views.openCity.contentDescription =
            getString(R.string.settings_city_description, name)
    }

    /**
     * The walking pace, in the journey section (SPEC §6, §7.6).
     *
     * Written the moment it is pressed, on the theme's pattern and with no
     * "apply" button. Nothing is applied here, though, and that is the whole
     * difference with the theme and the units: this one changes no screen
     * already drawn. It is read again when the next journey is worked out — the
     * model collects it from the preferences — so a pace changed here reaches
     * the next journey and leaves the one on show as it was computed.
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
     * **A declared pedal-assist bike changes the ride itself**, and since
     * 17 August 2026 the minutes with it: the two ends of a journey on one's own
     * bike take the bolt, the summary names that bike, and the ride is traced
     * with a profile of its own — a profile the engine computes with, never a
     * speed applied afterwards (SPEC §6). The mechanical bike is the ride of
     * before, to the track and to the minute, and it is what the row is set to
     * until the rider says otherwise.
     *
     * Written the moment it is pressed, like the pace under it, and read when
     * the next journey is asked for: a journey already on the screen keeps the
     * stations and the minutes it was worked out with.
     */
    private fun setUpOwnBikeKind(views: FragmentSettingsBinding) {
        views.ownBikeKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
            // Every change fires twice — the button left, then the one taken —
            // and only the second says what was chosen.
            if (!isChecked || isFilling) return@addOnButtonCheckedListener
            val kind = when (checkedId) {
                R.id.own_bike_kind_electric -> OwnBikeKind.Electric
                else -> OwnBikeKind.Mechanical
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
                            OwnBikeKind.Mechanical -> R.id.own_bike_kind_mechanical
                            OwnBikeKind.Electric -> R.id.own_bike_kind_electric
                        },
                    )
                    isFilling = false
                }
            }
        }
    }

    /**
     * The places the user names for themselves (SPEC §7.6).
     *
     * **Declared, never observed.** Nothing here is written from a journey,
     * from the map or from a search history: the row is pressed, an address is
     * chosen, and that is the only way either place is ever filled — which is
     * what keeps constraint C3 whole (SPEC §2, §8) and what
     * [io.github.mgdx.rouelibre.data.SavedPlace] says at greater length.
     *
     * The served area is read once, from the city in service and the way the
     * station sheet reads it. It cannot go stale under this screen: changing
     * city means leaving for the city list, which rebuilds this view on the way
     * back and reads the box again.
     */
    private fun setUpSavedPlaces(views: FragmentSettingsBinding) {
        views.placeHome.setOnClickListener { nameThePlace(SavedPlaceKind.Home) }
        views.placeWork.setOnClickListener { nameThePlace(SavedPlaceKind.Work) }
        views.forgetPlaceHome.setOnClickListener { forgetThePlace(SavedPlaceKind.Home) }
        views.forgetPlaceWork.setOnClickListener { forgetThePlace(SavedPlaceKind.Work) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val area = container.activeCity()?.boundingBox
                // The two are collected together so that both rows are written
                // from one reading of the area, and neither can lag the other.
                combine(preferences.homePlace, preferences.workPlace, ::Pair)
                    .collect { (home, work) ->
                        showPlace(SavedPlaceKind.Home, home, area)
                        showPlace(SavedPlaceKind.Work, work, area)
                    }
            }
        }
    }

    /**
     * Writes one row of "my places".
     *
     * **A place the city in service does not cover stays on the screen, is said
     * to be outside it, and stays erasable.** It is the rule SPEC §7.3 lays down
     * for the kind of bike asked of a network that lends one kind — "ignored,
     * never applied in silence — and never erased either" — and it holds for the
     * same reason: somebody who named their home in Lille and is looking at Lyon
     * today has not moved house, and coming back to Lille must find it again.
     *
     * @param kind which of the two rows is being written.
     * @param place what the user named, or `null` if they have named nothing.
     * @param area the reference box of the city in service, `null` when no city
     *   is chosen — and nothing is then outside anything.
     */
    private fun showPlace(kind: SavedPlaceKind, place: SavedPlace?, area: BoundingBox?) {
        val views = binding ?: return
        val setting = getString(kind.settingName)
        val row = settingsPlaceRow(
            setting = setting,
            place = place,
            coveredArea = area,
            none = getString(R.string.settings_place_none),
            invite = { getString(R.string.settings_place_set, it) },
            describe = { named, value ->
                getString(R.string.settings_place_description, named, value)
            },
        )
        val isHome = kind == SavedPlaceKind.Home
        val button = if (isHome) views.placeHome else views.placeWork
        val forget = if (isHome) views.forgetPlaceHome else views.forgetPlaceWork
        val outside = if (isHome) views.placeHomeOutside else views.placeWorkOutside

        button.text = row.label
        button.contentDescription = row.spokenLabel
        // The row names the place and not the press, so what the press does is
        // said as the label of the click action instead: a screen reader would
        // otherwise offer an address with nothing about where activating it
        // leads. Where no place is named the label is the invitation itself, so
        // the eye and the ear are told the same thing.
        ViewCompat.replaceAccessibilityAction(
            button,
            AccessibilityActionCompat.ACTION_CLICK,
            getString(
                if (place == null) R.string.settings_place_set else R.string.settings_place_change,
                setting,
            ),
            null,
        )
        forget.isVisible = row.canBeForgotten
        forget.contentDescription = getString(R.string.settings_place_clear, setting)
        outside.isVisible = row.isOutsideCityServed
    }

    /**
     * Opens the address search for one of the two places.
     *
     * No origin travels with it: that argument ranks the results by proximity to
     * a point one is composing a journey from, and there is no such point here.
     */
    private fun nameThePlace(kind: SavedPlaceKind) {
        placeBeingNamed = kind
        show(AddressSearchFragment.newInstance(origin = null))
    }

    /**
     * Forgets one of the two places.
     *
     * **Nothing is confirmed first**, as nothing is when a favourite station is
     * taken off the list: the press is undone by naming the place again, and a
     * dialog guarding a value one retypes in two presses costs more than it
     * protects. What it did is said afterwards instead, in the snackbar the
     * screens around this one answer a gesture with.
     */
    private fun forgetThePlace(kind: SavedPlaceKind) {
        viewLifecycleOwner.lifecycleScope.launch {
            preferences.clearSavedPlace(kind)
            val views = binding ?: return@launch
            Snackbar.make(
                views.root,
                getString(R.string.settings_place_cleared, getString(kind.settingName)),
                Snackbar.LENGTH_LONG,
            ).show()
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
     * The language the interface speaks, at the head of the display section
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
        val names =
            listOf(getString(R.string.settings_language_system)) + offered.map { it.endonym() }
        val chosen = chosenLanguage()
        val ticked =
            if (chosen == null) 0 else offered.indexOfFirst { it.language == chosen.language } + 1
        ChoiceDialogFragment.ask(
            manager = childFragmentManager,
            requestKey = LANGUAGE_ANSWER,
            title = R.string.settings_language_title,
            labels = names,
            // The language in service travels in the arguments, so the list
            // that comes back after a rotation still shows it ticked.
            ticked = ticked,
        )
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

    /**
     * Whether the map draws the bikes outside stations (SPEC §4.1, §7.6).
     *
     * The last setting of the display section, and the one that reaches a feed
     * rather than a drawing: switched on, the map reads a file twenty times
     * the weight of the station feed, every five minutes. Written the moment
     * it is pressed and kept from one session to the next, like the two
     * filters above it, and the map follows the stored value itself.
     *
     * **The explanation comes after the writing, and only the first time.** It
     * informs and does not confirm (SPEC §7.6): the switch is already on when
     * the window opens, so nothing waits on it and closing it changes nothing.
     * What it says is the one thing no feed carries — whether such a bike may
     * be taken where it stands — and it stays readable afterwards from the
     * sheet of any of those bikes.
     */
    private fun setUpStreetBikes(views: FragmentSettingsBinding) {
        views.showStreetBikes.setOnCheckedChangeListener { _, isChecked ->
            if (isFilling) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setShowStreetBikes(isChecked)
                if (isChecked && !preferences.streetBikesExplained()) {
                    StreetBikeIntroDialogFragment.explain(parentFragmentManager)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.showStreetBikes.collect { show ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.showStreetBikes.isChecked = show
                    current.vehicleFeedRefresh.isEnabled = show
                    current.vehicleFeedRefreshLabel.isEnabled = show
                    isFilling = false
                }
            }
        }
        setUpVehicleFeedRefresh(views)
    }

    /**
     * The cadence the vehicle feed is read at, in minutes (SPEC §4.1, §7.6).
     *
     * Written on release rather than on every step: a thumb dragged across
     * the slider passes twenty values, and the repository reads the setting
     * on its next tick, so only the value the thumb is left on is one that
     * was asked for. The line above the slider follows every step all the
     * same, since that is what one is looking at while dragging. Nothing
     * else waits on it: a change applies to the next read, wherever it comes
     * from.
     */
    private fun setUpVehicleFeedRefresh(views: FragmentSettingsBinding) {
        views.vehicleFeedRefresh.setLabelFormatter { value -> refreshLine(value.toInt()) }
        views.vehicleFeedRefresh.addOnChangeListener { _, value, _ ->
            binding?.vehicleFeedRefreshLabel?.text = refreshLine(value.toInt())
        }
        views.vehicleFeedRefresh.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit

                override fun onStopTrackingTouch(slider: Slider) {
                    if (isFilling) return
                    viewLifecycleOwner.lifecycleScope.launch {
                        preferences.setVehicleFeedRefreshMinutes(slider.value.toInt())
                    }
                }
            },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.vehicleFeedRefreshMinutes.collect { minutes ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.vehicleFeedRefresh.value = minutes.toFloat()
                    current.vehicleFeedRefreshLabel.text = refreshLine(minutes)
                    isFilling = false
                }
            }
        }
    }

    /** "5 minutes between two refreshes", agreed in number. */
    private fun refreshLine(minutes: Int): String =
        resources.getQuantityString(R.plurals.settings_vehicle_feed_refresh, minutes, minutes)

    private fun show(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    private companion object {
        /** The key the language picked from the list is answered under. */
        const val LANGUAGE_ANSWER = "settings-language"

        /** The key the row awaiting an address is kept under across a rebuild. */
        const val STATE_PLACE_BEING_NAMED = "settings-place-being-named"
    }
}

/** How a saved place's row names its setting: "Home", "Work" (SPEC §7.6). */
@get:StringRes
private val SavedPlaceKind.settingName: Int
    get() = when (this) {
        SavedPlaceKind.Home -> R.string.settings_place_home
        SavedPlaceKind.Work -> R.string.settings_place_work
    }
