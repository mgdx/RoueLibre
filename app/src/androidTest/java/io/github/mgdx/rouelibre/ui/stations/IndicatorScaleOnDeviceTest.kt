package io.github.mgdx.rouelibre.ui.stations

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mgdx.rouelibre.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The indicator against the system's font size **as a device applies it**.
 *
 * The companion of `IndicatorScaleTest`, which reads the tokens out of
 * `dimens.xml` on the JVM and can therefore only check what they declare. What
 * it cannot see is the one thing that broke here: from Android 14 a size in
 * `sp` grows along a curve rather than by the factor asked for, and the larger
 * the declared number the less of the factor it gets. Measured on a Fairphone 3
 * at ×2.0, the 16sp of body text came out at ×1.75, the 24sp figure at ×1.50
 * and the 52sp disc at ×1.11 — so the declared numbers satisfied the ratio on
 * paper while the drawing on the glass no longer held it, the disc taking a
 * tenth where the name beside it took three quarters, and the room for three
 * digits inside the ring falling from 2.17 to 1.60. Only a measurement on a
 * device running that curve catches it, which is what this is.
 *
 * The curve belongs to the system and not to this application, so these tests
 * assert **proportions between two sizes** rather than pixel counts: they stay
 * true on a phone whose curve differs from this one's, and on the Android that
 * changes it next.
 */
@RunWith(AndroidJUnit4::class)
class IndicatorScaleOnDeviceTest {

    /**
     * The disc grows exactly as much as the text it sits beside.
     *
     * The defect stated as the reader saw it: the text doubled and the disc
     * took ten per cent. What is compared is each size's growth from the normal
     * setting, so nothing here restates the arithmetic the view uses to get
     * there.
     */
    @Test
    fun theDiscGrowsInStepWithTheTextBesideIt() {
        val discAtNormalSize = discSize(NORMAL_TEXT_SIZE).toFloat()
        val bodyAtNormalSize = bodyTextSize(NORMAL_TEXT_SIZE)
        for (scale in TEXT_SIZE_STEPS) {
            val discGrowth = discSize(scale) / discAtNormalSize
            val textGrowth = bodyTextSize(scale) / bodyAtNormalSize
            assertEquals(
                "at ×$scale the text grew ×$textGrowth and the disc ×$discGrowth",
                0f,
                abs(discGrowth - textGrowth),
                A_PIXEL_OF_ROUNDING / discAtNormalSize,
            )
        }
    }

    /**
     * At the normal text size the disc is the size it has always been.
     *
     * Pinned against `indicator_size` resolved the way the view resolved it
     * before this change: at the default setting the font curve is the identity,
     * so the two must agree to the pixel. This is what says the disc was
     * repaired rather than redrawn.
     */
    @Test
    fun theDiscIsUnchangedAtTheNormalTextSize() {
        val declared = contextAt(NORMAL_TEXT_SIZE).resources
            .getDimensionPixelSize(R.dimen.indicator_size)
        assertEquals(
            "the disc moved at the normal text size",
            declared,
            discSize(NORMAL_TEXT_SIZE),
        )
    }

    private fun discSize(scale: Float): Int {
        val context = contextAt(scale)
        var side = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = AvailabilityIndicatorView(context)
            val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(free, free)
            side = view.measuredWidth
        }
        return side
    }

    private fun bodyTextSize(scale: Float) =
        contextAt(scale).resources.getDimension(R.dimen.text_body)

    private fun contextAt(scale: Float): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(target.resources.configuration).apply {
            fontScale = scale
        }
        return ContextThemeWrapper(
            target.createConfigurationContext(configuration),
            R.style.Theme_RoueLibre,
        )
    }

    private companion object {
        /** The steps of Android's text size slider on a Fairphone 3. */
        val TEXT_SIZE_STEPS = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f)

        const val NORMAL_TEXT_SIZE = 1.0f

        /**
         * What a whole pixel of rounding is worth as a proportion. The disc is
         * a whole number of pixels and a text size is not, so the two growths
         * cannot be asked to agree any closer than that.
         */
        const val A_PIXEL_OF_ROUNDING = 1f
    }
}
