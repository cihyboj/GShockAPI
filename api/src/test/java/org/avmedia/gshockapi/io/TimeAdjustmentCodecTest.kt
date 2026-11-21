package org.avmedia.gshockapi.io

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeAdjustmentCodecTest {

    // ABL-100WE 0x11 read: auto sync ON, hour 0, minute 30, timeout 3 min.
    private val defaultPacket = hex("11 0F 0F 0F 06 00 50 00 04 00 01 00 00 1E 03")

    // Same watch with sync hour 13, minute 3, timeout 120 min.
    private val hour13Packet = hex("11 0F 0F 0F 06 00 50 00 04 00 01 00 0D 03 78")

    @Test
    fun `decodes default packet as enabled at hour 0`() {
        val info = TimeAdjustmentCodec.decode(defaultPacket)
        assertTrue(info.isTimeAdjustmentSet)
        assertEquals(0, info.adjustmentTimeHour)
        assertEquals(30, info.adjustmentTimeMinutes)
    }

    @Test
    fun `custom sync hour is still decoded as enabled`() {
        val info = TimeAdjustmentCodec.decode(hour13Packet)
        assertTrue(info.isTimeAdjustmentSet)
        assertEquals(13, info.adjustmentTimeHour)
        assertEquals(3, info.adjustmentTimeMinutes)
    }

    @Test
    fun `disabled bit is decoded independently of the hour`() {
        val packet = hour13Packet.toMutableList().apply { this[12] = 0x80 or 13 }
        val info = TimeAdjustmentCodec.decode(packet)
        assertFalse(info.isTimeAdjustmentSet)
        assertEquals(13, info.adjustmentTimeHour)
    }

    @Test
    fun `round trip without changes writes back the exact packet`() {
        val info = TimeAdjustmentCodec.decode(hour13Packet)
        val encoded = TimeAdjustmentCodec.encode(
            original = hour13Packet,
            enabled = info.isTimeAdjustmentSet,
            minute = info.adjustmentTimeMinutes,
            hour = null,
        )
        assertArrayEquals(bytes(hour13Packet), encoded)
    }

    @Test
    fun `changing minute keeps hour and every other byte`() {
        val encoded = TimeAdjustmentCodec.encode(hour13Packet, enabled = true, minute = 7, hour = null)
        val expected = hour13Packet.toMutableList().apply { this[13] = 7 }
        assertArrayEquals(bytes(expected), encoded)
    }

    @Test
    fun `disabling keeps the hour bits`() {
        val encoded = TimeAdjustmentCodec.encode(hour13Packet, enabled = false, minute = 3, hour = null)
        assertEquals((0x80 or 13).toByte(), encoded[12])
        assertEquals(0x78.toByte(), encoded[14])
    }

    @Test
    fun `explicit hour replaces hour bits`() {
        val encoded = TimeAdjustmentCodec.encode(defaultPacket, enabled = true, minute = 30, hour = 9)
        assertEquals(9.toByte(), encoded[12])
    }

    @Test
    fun `out of range hour keeps the watch value`() {
        val encoded = TimeAdjustmentCodec.encode(hour13Packet, enabled = true, minute = 3, hour = 42)
        assertEquals(13.toByte(), encoded[12])
    }

    @Test
    fun `short or foreign packets are not valid templates`() {
        assertFalse(TimeAdjustmentCodec.isValid(defaultPacket.take(13)))
        assertFalse(TimeAdjustmentCodec.isValid(listOf(0x13) + defaultPacket.drop(1)))
        assertTrue(TimeAdjustmentCodec.isValid(defaultPacket))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode refuses a short original`() {
        TimeAdjustmentCodec.encode(defaultPacket.take(13), enabled = true, minute = 30, hour = null)
    }

    private fun hex(s: String): List<Int> = s.split(' ').map { it.toInt(16) }

    private fun bytes(ints: List<Int>) = ByteArray(ints.size) { ints[it].toByte() }
}
