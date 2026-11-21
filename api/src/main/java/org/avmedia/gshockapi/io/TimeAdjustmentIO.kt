package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import timber.log.Timber

class TimeAdjustmentInfo(
    var isTimeAdjustmentSet: Boolean = false,
    var adjustmentTimeMinutes: Int = 0,
    /** Auto-sync anchor hour (low 7 bits of byte 12). 0 on watches that only expose on/off. */
    var adjustmentTimeHour: Int = 0,
)

/**
 * Pure codec for the CASIO_SETTING_FOR_BLE (0x11) packet, as far as automatic time
 * adjustment is concerned. Indices include the leading 0x11 command byte.
 *
 * byte 12: bit 7 (0x80) = automatic sync DISABLED, bits 0-6 = sync hour (0-23)
 * byte 13: sync minute (0-59)
 *
 * Every other byte (alert modes, connection timeout, ...) must be written back as read.
 */
object TimeAdjustmentCodec {
    const val FLAGS_INDEX = 12
    const val MINUTE_INDEX = 13
    private const val DISABLED_BIT = 0x80
    private const val HOUR_MASK = 0x7F
    private const val DEFAULT_MINUTE = 30
    private const val COMMAND = 0x11
    private const val PACKET_SIZE = 14 // bytes 12-13 must exist; ABL-100WE sends 15

    /** A full 0x11 read; anything shorter must never be used as a write template. */
    fun isValid(packet: List<Int>): Boolean = packet.size >= PACKET_SIZE && packet[0] == COMMAND

    fun decode(packet: List<Int>): TimeAdjustmentInfo {
        require(isValid(packet)) { "not a full 0x11 packet (${packet.size} bytes)" }
        val flags = packet[FLAGS_INDEX]
        val minute = packet[MINUTE_INDEX]
        return TimeAdjustmentInfo(
            isTimeAdjustmentSet = flags and DISABLED_BIT == 0,
            adjustmentTimeMinutes = if (minute in 0..59) minute else DEFAULT_MINUTE,
            adjustmentTimeHour = flags and HOUR_MASK,
        )
    }

    /**
     * @param hour new sync hour, or `null`/out of range to keep the hour bits from [original].
     */
    fun encode(original: List<Int>, enabled: Boolean, minute: Int, hour: Int?): ByteArray {
        require(isValid(original)) { "not a full 0x11 packet (${original.size} bytes)" }
        val packet = original.toMutableList()
        val hourBits = hour?.takeIf { it in 0..23 } ?: (packet[FLAGS_INDEX] and HOUR_MASK)
        packet[FLAGS_INDEX] = hourBits or (if (enabled) 0 else DISABLED_BIT)
        packet[MINUTE_INDEX] = minute.coerceIn(0, 59)
        return ByteArray(packet.size) { i -> packet[i].toByte() }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
object TimeAdjustmentIO {
    private data class State(
        val deferredResult: CompletableDeferred<TimeAdjustmentInfo>? = null
    )

    private var state = State()

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun request(): TimeAdjustmentInfo =
        CachedIO.request("GET_TIME_ADJUSTMENT") { key -> getTimeAdjustment(key) }

    private suspend fun getTimeAdjustment(key: String): TimeAdjustmentInfo {
        state = state.copy(deferredResult = CompletableDeferred())
        Connection.sendMessage("{ action: '$key'}")
        return state.deferredResult?.await() ?: error("Deferred result not initialized")
    }

    fun set(settings: Settings) {
        settings.let {
            Gson().toJson(it)
        }.let { settingJson ->
            CachedIO.set("GET_TIME_ADJUSTMENT") {
                Connection.sendMessage("{action: \"SET_TIME_ADJUSTMENT\", value: $settingJson}")
            }
        }
    }

    fun onReceived(data: String) {
        val deferred = state.deferredResult
        state = State() // Reset state
        val packet = runCatching { Utils.toIntArray(data) }.getOrNull()
        if (packet == null || !TimeAdjustmentCodec.isValid(packet)) {
            // Never keep a malformed packet as the template for the next write.
            CasioIsAutoTimeOriginalValue.value = ""
            Timber.w("Ignoring malformed 0x11 packet: $data")
            deferred?.completeExceptionally(IllegalStateException("Malformed 0x11 packet"))
            return
        }
        CasioIsAutoTimeOriginalValue.value = data
        deferred?.complete(TimeAdjustmentCodec.decode(packet))
    }

    object CasioIsAutoTimeOriginalValue {
        var value = ""
    }

    /** Forget the packet read on a previous connection (possibly from another watch). */
    fun resetOriginalValue() {
        CasioIsAutoTimeOriginalValue.value = ""
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        IO.writeCmd(
            GetSetMode.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code.toByte())
        )
    }

    fun sendToWatchSet(message: String) {
        JSONObject(message).get("value").let { it as JSONObject }
            .apply {
                put("casioIsAutoTimeOriginalValue", CasioIsAutoTimeOriginalValue.value)
            }
            .let { settings -> encodeTimeAdjustment(settings) }
            .takeIf { it.isNotEmpty() }
            ?.let { encoded -> IO.writeCmd(GetSetMode.SET, encoded) }
    }

    private fun encodeTimeAdjustment(settings: JSONObject): ByteArray {
        val original = settings.getString("casioIsAutoTimeOriginalValue")
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { Utils.toIntArray(it) }.getOrNull() }
            ?.takeIf { TimeAdjustmentCodec.isValid(it) }
        if (original == null) {
            // Without a full packet read on this connection we would clobber unrelated bytes.
            Timber.w("SET_TIME_ADJUSTMENT skipped: no valid 0x11 read on this connection")
            return ByteArray(0)
        }
        return TimeAdjustmentCodec.encode(
            original = original,
            enabled = settings.optBoolean("timeAdjustment", true),
            minute = settings.getInt("adjustmentTimeMinutes"),
            hour = settings.optInt("adjustmentTimeHour", -1).takeIf { it >= 0 },
        )
    }
}
