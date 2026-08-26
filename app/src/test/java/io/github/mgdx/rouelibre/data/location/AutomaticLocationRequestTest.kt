package io.github.mgdx.rouelibre.data.location

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule under test is SPEC §10's: the application asks for the location
 * permission unprompted at most once — once per session, and not at all once
 * a refusal has been recorded, however long ago. Two dialogs for one refusal
 * is what an F-Droid Permissions review reads as insistence.
 */
class AutomaticLocationRequestTest {

    /** What the settings file would hold, shared by the requests of a test. */
    private var refusalOnDisk = false

    /** A session: each instance forgets what was asked, not what was refused. */
    private fun newSession(): AutomaticLocationRequest = AutomaticLocationRequest(
        isRefusalRemembered = { refusalOnDisk },
        rememberRefusal = { refusalOnDisk = true },
    )

    @Test
    fun `a fresh installation is asked, once`() = runTest {
        val session = newSession()

        assertTrue(session.mayAskUnprompted())
    }

    @Test
    fun `the same session is never asked twice`() = runTest {
        val session = newSession()
        session.mayAskUnprompted()

        assertFalse(session.mayAskUnprompted())
    }

    @Test
    fun `a session that has refused is not asked again`() = runTest {
        val session = newSession()
        session.mayAskUnprompted()
        session.noteRefused()

        assertFalse(session.mayAskUnprompted())
    }

    @Test
    fun `a refusal outlives the session it was pronounced in`() = runTest {
        // The refusal on the city screen's button, then the map opening on a
        // later launch: the F-Droid scenario. Before the refusal was written
        // down, every new session asked again.
        newSession().noteRefused()

        assertFalse(newSession().mayAskUnprompted())
    }

    @Test
    fun `a refusal on a button is enough, without any unprompted ask`() = runTest {
        val buttonOnly = newSession()
        buttonOnly.noteRefused()

        assertFalse(newSession().mayAskUnprompted())
    }

    @Test
    fun `a session merely asked does not bind the next one`() = runTest {
        // Asking is not refusing: a dialog the process died under must not
        // silence every launch to come.
        newSession().mayAskUnprompted()

        assertTrue(newSession().mayAskUnprompted())
    }
}
