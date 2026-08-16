package io.github.mgdx.rouelibre.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a dataset transfer may run, and when it must wait (SPEC §4.4). */
class MeteredTransferGateTest {

    @Test
    fun `the setting on and a billed connection hold the transfer back`() {
        assertFalse(MeteredTransferGate().mayRun(unmeteredOnly = true, metered = true))
    }

    @Test
    fun `the setting on and an unbilled connection let it run`() {
        assertTrue(MeteredTransferGate().mayRun(unmeteredOnly = true, metered = false))
    }

    @Test
    fun `the setting off lets it run whatever the connection bills`() {
        val gate = MeteredTransferGate()

        assertTrue(gate.mayRun(unmeteredOnly = false, metered = true))
        assertTrue(gate.mayRun(unmeteredOnly = false, metered = false))
    }

    @Test
    fun `an exempted transfer runs on a billed connection`() {
        val gate = MeteredTransferGate()

        gate.exemptOneTransfer()

        assertTrue(gate.mayRun(unmeteredOnly = true, metered = true))
    }

    @Test
    fun `the exemption holds for the whole transfer`() {
        // A set is several files, and the connection is asked about again at
        // every one of them: an exemption spent on the first would stop the
        // transfer in the middle of what was agreed to.
        val gate = MeteredTransferGate()

        gate.exemptOneTransfer()

        assertTrue(gate.mayRun(unmeteredOnly = true, metered = true))
        assertTrue(gate.mayRun(unmeteredOnly = true, metered = true))
    }

    @Test
    fun `the next transfer asks again`() {
        // The agreement covers one download, not the session: this is what
        // keeps the setting a setting.
        val gate = MeteredTransferGate()
        gate.exemptOneTransfer()
        gate.mayRun(unmeteredOnly = true, metered = true)

        gate.transferEnded()

        assertFalse(gate.isExempted)
        assertFalse(gate.mayRun(unmeteredOnly = true, metered = true))
    }
}
