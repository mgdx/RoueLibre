package io.github.mgdx.rouelibre.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of which redirections may be followed (SPEC §4.1).
 *
 * The hosts are the real ones where there is one: the publication path below
 * is the very chain a catalogue, a manifest or a data file goes through on its
 * way down.
 */
class RedirectPolicyTest {

    private fun allows(
        scope: RedirectScope?,
        origin: String,
        originDomain: String?,
        target: String,
        targetDomain: String?,
    ) = RedirectPolicy.allows(scope, origin, originDomain, target, targetDomain)

    @Test
    fun `the same host is always followed, whatever the request is for`() {
        for (scope in listOf(RedirectScope.GBFS, RedirectScope.PUBLICATION, null)) {
            assertTrue(
                allows(scope, "gbfs.example.com", "example.com", "gbfs.example.com", "example.com"),
            )
        }
        // An address without a registrable domain is still its own host.
        assertTrue(allows(null, "127.0.0.1", null, "127.0.0.1", null))
    }

    @Test
    fun `a feed may move within its operator's domain`() {
        assertTrue(
            allows(
                RedirectScope.GBFS,
                "gbfs.example.com",
                "example.com",
                "api.example.com",
                "example.com",
            ),
        )
    }

    @Test
    fun `a feed may not leave its operator's domain`() {
        assertFalse(
            allows(
                RedirectScope.GBFS,
                "gbfs.example.com",
                "example.com",
                "tracker.example.org",
                "example.org",
            ),
        )
        // Two hosts with no registrable domain are not one domain for that.
        assertFalse(allows(RedirectScope.GBFS, "localhost", null, "127.0.0.1", null))
    }

    @Test
    fun `an unlabelled request stays on its host, even within its domain`() {
        assertFalse(
            allows(null, "gbfs.example.com", "example.com", "api.example.com", "example.com"),
        )
    }

    @Test
    fun `a release on GitHub may go down to its asset storage`() {
        assertTrue(
            allows(
                RedirectScope.PUBLICATION,
                "github.com",
                "github.com",
                "release-assets.githubusercontent.com",
                "githubusercontent.com",
            ),
        )
        assertTrue(
            allows(
                RedirectScope.PUBLICATION,
                "github.com",
                "github.com",
                "objects.githubusercontent.com",
                "githubusercontent.com",
            ),
        )
    }

    @Test
    fun `a release on GitHub may not go anywhere else`() {
        assertFalse(
            allows(
                RedirectScope.PUBLICATION,
                "github.com",
                "github.com",
                "evil.example",
                "evil.example",
            ),
        )
        // A suffix match on the bare text would have let this one through.
        assertFalse(
            allows(
                RedirectScope.PUBLICATION,
                "github.com",
                "github.com",
                "evilgithubusercontent.com",
                "evilgithubusercontent.com",
            ),
        )
    }

    @Test
    fun `the asset storage is GitHub's exception, not anybody's`() {
        assertFalse(
            allows(
                RedirectScope.PUBLICATION,
                "example.org",
                "example.org",
                "x.githubusercontent.com",
                "githubusercontent.com",
            ),
        )
    }

    @Test
    fun `a feed on GitHub does not inherit the publication exception`() {
        assertFalse(
            allows(
                RedirectScope.GBFS,
                "github.com",
                "github.com",
                "x.githubusercontent.com",
                "githubusercontent.com",
            ),
        )
    }
}
