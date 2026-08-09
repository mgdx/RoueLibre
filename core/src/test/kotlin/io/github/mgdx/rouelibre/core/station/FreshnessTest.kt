package io.github.mgdx.rouelibre.core.station

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Tests de l'âge affiché de la donnée (SPEC §4.1). */
class FreshnessTest {

    private val now: Instant = Instant.parse("2026-08-09T12:00:00Z")

    private fun ago(seconds: Long) = freshnessOf(now.minusSeconds(seconds), now)

    @Test
    fun `les toutes premieres secondes ne meritent pas de decompte`() {
        assertEquals(Freshness.JustNow, ago(0))
        assertEquals(Freshness.JustNow, ago(4))
    }

    @Test
    fun `le decompte passe des secondes aux minutes puis aux heures`() {
        assertEquals(Freshness.Seconds(12), ago(12))
        assertEquals(Freshness.Seconds(59), ago(59))
        assertEquals(Freshness.Minutes(1), ago(60))
        assertEquals(Freshness.Minutes(59), ago(3_599))
        assertEquals(Freshness.Hours(1), ago(3_600))
        assertEquals(Freshness.Hours(23), ago(86_399))
        assertEquals(Freshness.LongAgo, ago(86_400))
    }

    @Test
    fun `l'absence de donnee se distingue d'une donnee ancienne`() {
        assertEquals(Freshness.Never, freshnessOf(null, now))
    }

    @Test
    fun `une horloge qui recule ne produit pas un age negatif`() {
        // Correction NTP ou changement d'heure : mieux vaut « à l'instant »
        // que « il y a -3 secondes ».
        assertEquals(Freshness.JustNow, freshnessOf(now.plusSeconds(30), now))
    }

    @Test
    fun `l'etat n'est dit fige qu'au-dela de cinq minutes`() {
        assertTrue(!ago(0).isStale)
        assertTrue(!ago(59).isStale)
        assertTrue(!ago(4 * 60).isStale)
        assertTrue(ago(5 * 60).isStale)
        assertTrue(ago(3_600).isStale)
        assertTrue(Freshness.LongAgo.isStale)
    }

    @Test
    fun `sans aucune donnee l'etat est fige par definition`() {
        assertTrue(Freshness.Never.isStale)
    }
}
