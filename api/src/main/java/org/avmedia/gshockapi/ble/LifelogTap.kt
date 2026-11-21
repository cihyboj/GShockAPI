package org.avmedia.gshockapi.ble

/**
 * Debug hook: raw notifications from the lifelog channels (26eb0023 data request,
 * 26eb0024 convoy), delivered before StepCounterIO handles them. Null in normal use.
 */
object LifelogTap {
    @Volatile
    var listener: ((channel: String, data: ByteArray) -> Unit)? = null
}
