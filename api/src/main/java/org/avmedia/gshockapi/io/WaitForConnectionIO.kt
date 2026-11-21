package org.avmedia.gshockapi.io

import CachedIO
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.utils.WatchDataListener

@RequiresApi(Build.VERSION_CODES.O)
object WaitForConnectionIO {
    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(
        context: Context,
        deviceId: String?,
    ): String = waitForConnection(context, deviceId)

    private suspend fun waitForConnection(
        context: Context,
        deviceId: String?,
    ): String {
        // Concurrent callers share one pending wait; each awaits its own reference, so the
        // listener clearing `state` cannot leave a caller with nothing to await.
        val deferred = state.deferredResult?.takeIf { it.isActive }
            ?: CompletableDeferred<String>().also { state = state.copy(deferredResult = it) }

        // MUST be before the isConnected check and startConnection: a link that becomes
        // ready in between would otherwise be missed and this wait would never end.
        setupConnectionListener()

        // CONNECTED is only set once the watch is ready (services discovered).
        if (Connection.isConnected()) {
            return "OK"
        }

        if (!Connection.isConnecting()) {
            WatchDataListener.init()
            Connection.startConnection(context, deviceId)
        }
        // If already connecting, just fall through and wait for the event

        return deferred.await()
    }

    private fun setupConnectionListener() {
        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {
                CachedIO.clearCache()
                TimeAdjustmentIO.resetOriginalValue()
                state.deferredResult?.complete("OK")
                state = State()
            }
        )

        ProgressEvents.subscriber.runEventActions(this.javaClass.name, eventActions)
    }
}
