package io.github.mgdx.rouelibre.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the one rule a name from elsewhere must pass before it becomes a path
 * on the device: a manifest's file names, and an imported routing segment's,
 * which the document provider reports and may choose at will.
 */
class PlainFileNameTest {

    @Test
    fun `a name that would leave the directory is refused`() {
        for (name in listOf("", ".", "..", "a/b", "../graph.rd5", "a\\b", "graph\u0000.rd5")) {
            assertFalse("\"$name\" should have been refused", isPlainFileName(name))
        }
    }

    @Test
    fun `a plain name is accepted, spaces and accents included`() {
        for (name in listOf("E0_N50.rd5", "tiles.mbtiles", "réseau de Lille.rd5", "..rd5")) {
            assertTrue("\"$name\" should have been accepted", isPlainFileName(name))
        }
    }

    @Test
    fun `a name longer than one path component is refused, counted in bytes`() {
        assertTrue(isPlainFileName("a".repeat(251) + ".rd5"))
        assertFalse(isPlainFileName("a".repeat(252) + ".rd5"))
        // 128 characters, 256 bytes: the limit is the file system's, and it
        // counts bytes, not characters.
        assertFalse(isPlainFileName("é".repeat(128)))
    }

    @Test
    fun `an imported routing segment must be an rd5 file`() {
        assertTrue(DatasetKind.isRoutingSegmentName("graph.rd5"))
        assertTrue(DatasetKind.isRoutingSegmentName("E0_N50.rd5"))
        for (name in listOf("graph.txt", "graph.rd5.txt", ".rd5", "graph.RD5", "graph")) {
            assertFalse(
                "\"$name\" should have been refused",
                DatasetKind.isRoutingSegmentName(name),
            )
        }
    }

    @Test
    fun `an imported routing segment is held to the plain name rule`() {
        for (name in listOf("../graph.rd5", "a/graph.rd5", "a\\graph.rd5", "g\u0000.rd5")) {
            assertFalse(
                "\"$name\" should have been refused",
                DatasetKind.isRoutingSegmentName(name),
            )
        }
        assertFalse(DatasetKind.isRoutingSegmentName("a".repeat(252) + ".rd5"))
    }
}
