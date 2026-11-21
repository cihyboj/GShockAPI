package org.avmedia.gshockapi.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * Synthetic 400-byte ABL-100WE lifelog modelled on a verified capture
 * (57 steps today, 29 m, current-hour buckets 5 + 19 + 33, BCD total 57).
 */
class StepCounterParseTest {
    private fun record(yearBcd: Int = 0x26): ByteArray {
        val b = ByteArray(400)
        fun u16(at: Int, v: Int) { b[at] = (v and 0xFF).toByte(); b[at + 1] = (v shr 8 and 0xFF).toByte() }
        fun u32(at: Int, v: Int) { for (i in 0 until 4) b[at + i] = (v shr (8 * i) and 0xFF).toByte() }
        byteArrayOf(yearBcd.toByte(), 0x09, 0x29, 0x15, 0x01, 0x00).copyInto(b, 0)
        for (at in 6 until 318 step 2) u16(at, 0xFFFE)          // no hourly records / distances
        for (at in 318 until 374 step 4) u32(at, -2)            // no daily summaries
        u32(374, 57)
        u32(378, 29)
        u16(382, 5); u16(384, 19); u16(386, 33)
        u32(392, 29)
        byteArrayOf(0x57, 0, 0, 0).copyInto(b, 396)
        return b
    }

    @Test
    fun `parses today's totals and current hour`() {
        val data = StepCounterIOFunctional.parse(record())
        assertNotNull(data)
        data!!
        assertEquals(LocalDateTime.of(2026, 9, 29, 15, 1, 0), data.timestamp)
        assertEquals(57, data.currentDaySteps)
        assertEquals(29, data.distanceMeters)
        assertEquals(57, data.bcdTotalSteps)
        assertEquals(57, data.hourlyByHour[15])
        assertEquals(List(7) { null }, data.dailyHistory)
    }

    @Test
    fun `accepts records from 2027 on`() {
        val data = StepCounterIOFunctional.parse(record(yearBcd = 0x27))
        assertEquals(2027, data?.timestamp?.year)
    }

    @Test
    fun `rejects a non-BCD first byte`() {
        assertNull(StepCounterIOFunctional.parse(record(yearBcd = 0xFE)))
    }
}
