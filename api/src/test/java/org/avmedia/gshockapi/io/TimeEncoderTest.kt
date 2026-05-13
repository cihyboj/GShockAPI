package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.io.TimeIO.TimeEncoder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class TimeEncoderTest {

    @Test
    fun `encodes year as little-endian two bytes`() {
        val arr = TimeEncoder.prepareCurrentTime(LocalDateTime.of(2026, 5, 12, 10, 30, 0))
        assertEquals(0xEA.toByte(), arr[0]) // 2026 & 0xFF = 0xEA
        assertEquals(0x07.toByte(), arr[1]) // 2026 >> 8 = 0x07
    }

    @Test
    fun `encodes month day hour minute second and weekday`() {
        val arr = TimeEncoder.prepareCurrentTime(LocalDateTime.of(2026, 5, 12, 10, 30, 45))
        assertEquals(5.toByte(), arr[2])
        assertEquals(12.toByte(), arr[3])
        assertEquals(10.toByte(), arr[4])
        assertEquals(30.toByte(), arr[5])
        assertEquals(45.toByte(), arr[6])
        // 2026-05-12 is a Tuesday → DayOfWeek.value == 2
        assertEquals(2.toByte(), arr[7])
    }

    @Test
    fun `sub-second byte is hundredths of a second within 0 to 99`() {
        val arr = TimeEncoder.prepareCurrentTime(
            LocalDateTime.of(2026, 1, 1, 0, 0, 0).withNano(990_000_000)
        )
        assertEquals(99.toByte(), arr[8])
    }

    @Test
    fun `sub-second byte is zero when nano is zero`() {
        val arr = TimeEncoder.prepareCurrentTime(LocalDateTime.of(2026, 1, 1, 0, 0, 0))
        assertEquals(0.toByte(), arr[8])
    }

    @Test
    fun `sub-second byte never exceeds 99 for valid LocalDateTime`() {
        val arr = TimeEncoder.prepareCurrentTime(
            LocalDateTime.of(2026, 1, 1, 0, 0, 0).withNano(999_999_999)
        )
        assertEquals(99.toByte(), arr[8])
    }

    @Test
    fun `trailing byte is 0 (experimental, was 1 upstream)`() {
        val arr = TimeEncoder.prepareCurrentTime(LocalDateTime.of(2026, 1, 1, 0, 0, 0))
        assertEquals(0.toByte(), arr[9])
    }
}
