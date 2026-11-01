package com.vwillcox.mytv.system

import android.content.Context
import android.util.Log
import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.ConnectException

/**
 * Puts the TV on this box's HDMI input with HDMI-CEC One Touch Play, as Google TV does when its own buttons are
 * pressed. Android lets only system apps send CEC, so this goes the way a computer would: it connects to the box's own
 * network debugging (adbd on port 5555) and runs `cmd hdmi_control onetouchplay`. The first time, the TV asks whether
 * to allow myTV's key.
 *
 * Each switch makes its own connection and closes it, so no debugging session is left open.
 */
class InputSwitch(context: Context, scope: CoroutineScope) {
    enum class State(val text: String) {
        UNTRIED("Not used yet"),
        SWITCHING("Switching. If the TV asks, allow it, with Always allow ticked"),
        WORKING("Working"),
        NO_DEBUGGING("Network debugging is off on this TV"),
        NOT_ALLOWED("The TV didn't allow it. Press Home to be asked again"),
        FAILED("The TV didn't take the command"),
    }

    private val _state = MutableStateFlow(State.UNTRIED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val privateKey = File(context.noBackupFilesDir, "adbkey")
    private val publicKey = File(context.noBackupFilesDir, "adbkey.pub")
    private var keys: AdbKeyPair? = null

    // A press made while a switch is on its way doesn't make another.
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) { for (r in requests) _state.value = oneTouchPlay() }
    }

    fun switchHere() {
        requests.trySend(Unit)
    }

    private fun oneTouchPlay(): State {
        if (_state.value != State.WORKING) _state.value = State.SWITCHING
        return try {
            Dadb.create(HOST, PORT, keys(), CONNECT_TIMEOUT_MS, ANSWER_TIMEOUT_MS).use { adb ->
                val r = adb.shell(COMMAND)
                if (r.exitCode == 0) State.WORKING else State.FAILED.also { warn("exit ${r.exitCode}: ${r.allOutput.trim()}") }
            }
        } catch (e: AdbConnectException) {
            warn(e)
            // Refused outright: nothing is listening. Anything later is the handshake, which the TV turned down.
            if (e.cause is ConnectException) State.NO_DEBUGGING else State.NOT_ALLOWED
        } catch (e: AdbAuthException) {
            warn(e)
            State.NOT_ALLOWED
        } catch (e: Exception) {
            warn(e)
            State.FAILED
        }
    }

    private fun keys(): AdbKeyPair = keys ?: run {
        if (!privateKey.exists() || !publicKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey)
            // The name after the key is what the TV lists it as; dadb writes "unknown@unknown".
            publicKey.writeText(publicKey.readText().substringBefore(' ') + " myTV")
        }
        AdbKeyPair.read(privateKey, publicKey).also { keys = it }
    }

    private fun warn(what: Any) = Log.w("myTvInput", "One Touch Play: $what")

    private companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val COMMAND = "cmd hdmi_control onetouchplay"
        const val CONNECT_TIMEOUT_MS = 3_000
        // Long enough to answer the TV's "Allow debugging?" the first time; the switch itself takes a second or so.
        const val ANSWER_TIMEOUT_MS = 60_000
    }
}
