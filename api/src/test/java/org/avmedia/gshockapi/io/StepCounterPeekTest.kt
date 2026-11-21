package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.model.StepCounterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A peek must never send `04 11`: closing deletes the hourly and daily history on the watch. */
class StepCounterPeekTest {
    private val record = StepCounterData(hourlySteps = emptyList(), dailyHistory = emptyList(), currentDaySteps = 1234)

    @Test
    fun `first peek in a connection starts a transaction`() {
        assertEquals(StepCounterIO.PeekAction.START, StepCounterIO.peekAction(false, null))
    }

    @Test
    fun `second peek returns the record already read`() {
        assertEquals(StepCounterIO.PeekAction.RETURN_CACHED, StepCounterIO.peekAction(true, record))
    }

    @Test
    fun `peek after a failed, timed out or cancelled read does not touch the watch`() {
        assertEquals(StepCounterIO.PeekAction.RETURN_UNAVAILABLE, StepCounterIO.peekAction(true, null))
    }

    @Test
    fun `only an explicit close ends the transaction`() {
        assertFalse(StepCounterIO.closesTransaction(peek = true))
        assertTrue(StepCounterIO.closesTransaction(peek = false))
    }
}
