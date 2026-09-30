package io.github.mgdx.rouelibre.ui.storage

import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.DatasetUpdate
import io.github.mgdx.rouelibre.core.data.InstalledDataset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * The badge a storage-screen row wears when an update is announced (SPEC §4.4).
 *
 * It used to be a replacement of the state line, in that line's own style, and
 * went unseen. What is held here is when the badge shows, and what the layout
 * gives it — its own view beside the name, a colour of alert, a label TalkBack
 * reads — read from the disk with no Android runtime involved (SPEC §14).
 */
class UpdateBadgeTest {

    private val installed = InstalledDataset(
        kind = DatasetKind.Tiles,
        sizeBytes = 12_000_000,
        sha256 = "0".repeat(64),
        installedAt = Instant.parse("2026-08-09T10:00:00Z"),
        formatVersion = null,
    )

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /** The attributes of the badge, from its identity to the end of its tag. */
    private val badge by lazy {
        val layout = File(resources, "layout/item_dataset.xml").readText()
        val declared = layout.indexOf("""android:id="@+id/dataset_update_badge"""")
        assertTrue("The row declares an update badge", declared > 0)
        layout.substring(declared, layout.indexOf("/>", declared))
    }

    @Test
    fun `only an installed set announced in another version wears the badge`() {
        val row = DatasetRow(DatasetKind.Tiles, installed)
        assertTrue(row.copy(update = DatasetUpdate.Outdated).announcesUpdate)
        assertFalse(row.copy(update = DatasetUpdate.UpToDate).announcesUpdate)
        assertFalse(row.announcesUpdate)
        assertFalse(
            DatasetRow(DatasetKind.Tiles, installed = null, update = DatasetUpdate.Outdated)
                .announcesUpdate,
        )
        assertFalse(
            DatasetRow(DatasetKind.Tiles, installed = null, update = DatasetUpdate.Missing)
                .announcesUpdate,
        )
    }

    @Test
    fun `the badge is hidden until the adapter shows it`() {
        assertTrue(badge.contains("""android:visibility="gone""""))
    }

    /** The label carries the meaning, the mark beside it is not announced. */
    @Test
    fun `the badge is a label TalkBack reads, with its mark as decoration`() {
        assertTrue(badge.contains("""android:text="@string/dataset_update_available""""))
        assertTrue(badge.contains("""app:drawableStartCompat="@drawable/ic_update_available""""))
        assertFalse(badge.contains("importantForAccessibility=\"no\""))
    }

    /** A theme token, redefined for the dark theme, and never a literal. */
    @Test
    fun `the badge and its mark take the alert colour of the theme`() {
        assertTrue(badge.contains("""android:textColor="@color/alert""""))
        val mark = File(resources, "drawable/ic_update_available.xml").readText()
        assertTrue(mark.contains("""android:tint="@color/alert""""))
        for (theme in listOf("values/colors.xml", "values-night/colors.xml")) {
            assertTrue(
                "$theme defines the alert colour",
                File(resources, theme).readText().contains("""<color name="alert">"""),
            )
        }
    }

    /** A long label wraps within its half of the row, beside a name it leaves be. */
    @Test
    fun `a long label cannot squeeze the name`() {
        assertTrue(badge.contains("""app:layout_constrainedWidth="true""""))
        val limit = """app:layout_constraintStart_toStartOf="@id/dataset_badge_limit""""
        assertTrue(badge.contains(limit))
        assertTrue(
            "The name ends where the badge starts",
            File(resources, "layout/item_dataset.xml").readText()
                .contains("""app:layout_constraintEnd_toStartOf="@id/dataset_update_badge""""),
        )
    }
}
