package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.model.ExerciseProfile
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber

/**
 * Exercise setting, class 0x2D: step goal (4-byte little-endian BCD), height in cm
 * and weight in kg (2-byte little-endian BCD, each byte inverted).
 *
 * The encoding is upstream's (izivkov/GShockAPI ExerciseIO). This fork reads it
 * fresh every time (no CachedIO) and returns null instead of hanging when the
 * watch does not answer, so callers can refuse to write defaults over real values.
 */
@RequiresApi(Build.VERSION_CODES.O)
object ExerciseIO {
    const val CLASS_EXERCISE = 0x2D
    private const val REQUEST_TIMEOUT_MS = 5_000L

    private val requestMutex = Mutex()

    @Volatile
    private var pending: CompletableDeferred<ExerciseProfile>? = null

    suspend fun request(): ExerciseProfile? {
        if (!WatchInfo.hasStepCounter) return null
        return requestMutex.withLock {
            val deferred = CompletableDeferred<ExerciseProfile>()
            pending = deferred
            try {
                IO.writeCmd(GetSetMode.GET, byteArrayOf(CLASS_EXERCISE.toByte()))
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) { deferred.await() }
                    .also { if (it == null) Timber.w("ExerciseIO: no 0x2D response") }
            } finally {
                pending = null
            }
        }
    }

    fun set(profile: ExerciseProfile) {
        IO.writeCmd(GetSetMode.SET, encode(profile))
    }

    fun onReceived(data: String) {
        val payload = Utils.toIntArray(data).map { it.toByte() }.toByteArray()
        val profile = runCatching { decode(payload) }
            .onFailure { Timber.w(it, "ExerciseIO: cannot decode $data") }
            .getOrNull() ?: return
        pending?.complete(profile)
    }

    fun encode(profile: ExerciseProfile): ByteArray {
        val stepGoalBytes = toBcdLe(profile.stepGoal, 4)
        val heightBytes = invert(toBcdLe(profile.height, 2))
        val weightBytes = invert(toBcdLe(profile.weight, 2))
        val payload = ByteArray(2 + stepGoalBytes.size + heightBytes.size + weightBytes.size)
        payload[0] = CLASS_EXERCISE.toByte()
        payload[1] = 0
        stepGoalBytes.copyInto(payload, 2)
        heightBytes.copyInto(payload, 2 + stepGoalBytes.size)
        weightBytes.copyInto(payload, 2 + stepGoalBytes.size + heightBytes.size)
        return payload
    }

    fun decode(payload: ByteArray): ExerciseProfile {
        require(payload.size >= 10 && (payload[0].toInt() and 0xFF) == CLASS_EXERCISE) { "not an exercise payload" }
        return ExerciseProfile(
            stepGoal = bcdLe(payload.copyOfRange(2, 6)),
            height = bcdLe(invert(payload.copyOfRange(6, 8))),
            weight = bcdLe(invert(payload.copyOfRange(8, 10))),
        )
    }

    private fun toBcdLe(value: Int, length: Int): ByteArray {
        var remaining = value.coerceAtLeast(0)
        return ByteArray(length) {
            val current = remaining % 100
            remaining /= 100
            (((current / 10) shl 4) or (current % 10)).toByte()
        }
    }

    private fun bcdLe(bytes: ByteArray): Int {
        var total = 0
        var multiplier = 1
        for (byte in bytes) {
            val v = byte.toInt() and 0xFF
            total += ((v shr 4) * 10 + (v and 0x0F)) * multiplier
            multiplier *= 100
        }
        return total
    }

    private fun invert(bytes: ByteArray): ByteArray =
        ByteArray(bytes.size) { (255 - (bytes[it].toInt() and 0xFF)).toByte() }
}
