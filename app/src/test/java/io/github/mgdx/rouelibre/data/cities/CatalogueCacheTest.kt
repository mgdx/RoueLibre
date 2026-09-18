package io.github.mgdx.rouelibre.data.cities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests of the copy held on the device and of the validators that describe it.
 *
 * What is worth testing here is not that a file can be written but that the two
 * files never fall out of step: offering a validator for a document the device
 * no longer holds would have the host answer `304` about a catalogue that cannot
 * be shown.
 */
class CatalogueCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun cache(): CatalogueCache = CatalogueCache(folder.root)

    @Test
    fun `an empty directory holds nothing`() {
        assertNull(cache().document())
        assertNull(cache().validators())
    }

    @Test
    fun `what was kept is read back`() {
        assertTrue(cache().keep("{\"cities\":[]}", etag = "\"abc\"", lastModified = null))

        assertEquals("{\"cities\":[]}", cache().document())
        assertEquals("\"abc\"", cache().validators()?.etag)
        assertNull(cache().validators()?.lastModified)
    }

    @Test
    fun `both validators are kept when the host sends both`() {
        cache().keep("first", etag = "\"abc\"", lastModified = "Mon, 14 Sep 2026 20:50:55 GMT")

        val validators = cache().validators()
        assertEquals("\"abc\"", validators?.etag)
        assertEquals("Mon, 14 Sep 2026 20:50:55 GMT", validators?.lastModified)
        assertFalse(validators!!.isEmpty)
    }

    @Test
    fun `a document that comes with no validator forgets the previous one`() {
        cache().keep("first", etag = "\"abc\"", lastModified = null)

        cache().keep("second", etag = null, lastModified = null)

        // Kept, the entity tag of the first document would be offered for the
        // second, and the host would answer `304` for a copy long gone.
        assertEquals("second", cache().document())
        assertNull(cache().validators())
    }

    @Test
    fun `a blank validator is not written`() {
        cache().keep("first", etag = "", lastModified = " ")

        assertNull(cache().validators())
    }

    @Test
    fun `an unreadable validators file asks for the whole document`() {
        cache().keep("first", etag = "\"abc\"", lastModified = null)
        File(folder.root, "catalogue-validators.json").writeText("{ this is not JSON")

        // Null rather than a failure: the cost is one full download, against an
        // application that would otherwise not open its city list at all.
        assertNull(cache().validators())
        assertEquals("first", cache().document())
    }

    @Test
    fun `nothing is left staged behind a successful write`() {
        cache().keep("first", etag = null, lastModified = null)

        assertFalse(File(folder.root, "catalogue.json.partial").exists())
    }
}
