package com.beyondpad.app.adb

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.beyondpad.app.Prefs
import com.beyondpad.app.display.DisplayRepository
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class AdbLink { NOT_PAIRED, PAIRING, CONNECTING, CONNECTED, UNREACHABLE }

data class AdbState(val link: AdbLink = AdbLink.NOT_PAIRED, val message: String = "")

/**
 * Talks to the tablet's own Wireless debugging service, the same way `adb` on a computer does.
 *
 * Why: Android 16 on the Pad 3 keeps a newly connected monitor DISABLED until it is confirmed
 * ("enable_waiting_confirmation_before_mirroring"), and OxygenOS never shows that confirmation.
 * Only the shell user may enable it (`cmd display enable-display`), so BeyondPad asks the user to
 * pair once, then runs that one command whenever a monitor is plugged in.
 */
object SelfAdb {
    private lateinit var ctx: Context
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(AdbState())
    val state: StateFlow<AdbState> = _state.asStateFlow()

    /** Physical displays the system created but keeps disabled: "Display N [id=local:…,stack=-1]". */
    private const val ENABLE_SCRIPT =
        "ids=\$(dumpsys display | sed -n 's/^ *Display \\([0-9][0-9]*\\) \\[id=local:[0-9]*,stack=-1\\].*/\\1/p' | sort -u); " +
            "if [ -z \"\$ids\" ]; then echo NONE; fi; " +
            "for id in \$ids; do cmd display enable-display \$id && echo ENABLED \$id; done"

    fun init(context: Context) {
        ctx = context.applicationContext
        _state.value = AdbState(if (Prefs.settings.value.adbPaired) AdbLink.UNREACHABLE else AdbLink.NOT_PAIRED,
            if (Prefs.settings.value.adbPaired) "Paired. Connects when needed." else "")
    }

    val isPaired: Boolean get() = Prefs.settings.value.adbPaired

    /** Pairs with the code from Settings › Wireless debugging › Pair device with pairing code. */
    fun pair(code: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        val clean = code.filter { it.isDigit() }
        if (clean.length != 6) { onDone(false, "The pairing code has 6 digits."); return }
        _state.value = AdbState(AdbLink.PAIRING, "Looking for the pairing service…")
        worker.execute {
            val (ok, msg) = try {
                val found = discover(AdbMdns.SERVICE_TYPE_TLS_PAIRING, 8_000)
                    ?: throw IllegalStateException("Pairing dialog not found. Keep 'Pair device with pairing code' open on screen and try again.")
                SelfAdbManager.get(ctx).pair(found.first, found.second, clean)
                Prefs.update { it.copy(adbPaired = true) }
                DisplayRepository.log("Paired with this tablet's Wireless debugging")
                true to "Paired."
            } catch (e: Exception) {
                false to "Pairing failed: ${e.message ?: e.javaClass.simpleName}"
            }
            _state.value = AdbState(if (ok) AdbLink.CONNECTING else AdbLink.NOT_PAIRED, msg)
            main.post { onDone(ok, msg) }
            if (ok) {
                connectLocked()
                runLocked("pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS")
                enableLocked()
            }
        }
    }

    /** Turns on any monitor the system is holding disabled. Safe to call often. */
    fun enableMonitors(reason: String, onDone: (String) -> Unit = {}) {
        if (!isPaired) { onDone("Set up the one-time pairing first."); return }
        worker.execute {
            val out = if (connectLocked()) enableLocked() else _state.value.message
            DisplayRepository.log("Turn on monitor ($reason): $out")
            main.postDelayed({ DisplayRepository.refresh(); onDone(out) }, 800)
        }
    }

    /** Sets an AOSP global setting through the shell (no extra permission needed once paired). */
    fun putGlobal(key: String, value: Int, onDone: (Boolean) -> Unit = {}) {
        worker.execute {
            val ok = connectLocked() && runLocked("settings put global $key $value; settings get global $key").trim().endsWith("$value")
            main.post { onDone(ok) }
        }
    }

    fun forget() {
        worker.execute { runCatching { SelfAdbManager.get(ctx).disconnect() } }
        Prefs.update { it.copy(adbPaired = false) }
        _state.value = AdbState(AdbLink.NOT_PAIRED, "")
    }

    // ── worker-thread helpers ──

    private fun connectLocked(): Boolean {
        val m = SelfAdbManager.get(ctx)
        if (m.isConnected) { _state.value = AdbState(AdbLink.CONNECTED, "Connected to Wireless debugging."); return true }
        _state.value = AdbState(AdbLink.CONNECTING, "Connecting to Wireless debugging…")
        return try {
            val found = discover(AdbMdns.SERVICE_TYPE_TLS_CONNECT, 6_000)
            val ok = if (found != null) m.connect(found.first, found.second) || m.isConnected else m.autoConnect(ctx, 6_000)
            _state.value = if (ok) AdbState(AdbLink.CONNECTED, "Connected to Wireless debugging.")
            else AdbState(AdbLink.UNREACHABLE, "Wireless debugging is off or Wi-Fi is disconnected.")
            ok
        } catch (_: AdbPairingRequiredException) {
            Prefs.update { it.copy(adbPaired = false) }
            _state.value = AdbState(AdbLink.NOT_PAIRED, "The tablet forgot the pairing. Pair again.")
            false
        } catch (e: Exception) {
            _state.value = AdbState(AdbLink.UNREACHABLE, "Wireless debugging is off or Wi-Fi is disconnected (${e.javaClass.simpleName}).")
            false
        }
    }

    private fun enableLocked(): String {
        val raw = runLocked(ENABLE_SCRIPT).trim()
        return when {
            raw.contains("ENABLED") -> "Monitor turned on (${raw.lines().filter { it.startsWith("ENABLED") }.joinToString { it.removePrefix("ENABLED ").let { id -> "display $id" } }})."
            raw == "NONE" -> "No switched-off monitor found."
            raw.isEmpty() -> "No answer from the tablet."
            else -> raw.take(200)
        }
    }

    private fun runLocked(cmd: String): String = try {
        SelfAdbManager.get(ctx).openStream("shell:$cmd").use { s ->
            s.openInputStream().bufferedReader().readText()
        }
    } catch (e: Exception) {
        runCatching { SelfAdbManager.get(ctx).disconnect() }
        "Error: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun discover(type: String, timeoutMs: Long): Pair<String, Int>? {
        val host = AtomicReference<String?>(null)
        val port = AtomicInteger(-1)
        val latch = CountDownLatch(1)
        val mdns = AdbMdns(ctx, type) { addr, p ->
            if (addr != null && p > 0) { host.set(addr.hostAddress); port.set(p); latch.countDown() }
        }
        mdns.start()
        try { latch.await(timeoutMs, TimeUnit.MILLISECONDS) } finally { mdns.stop() }
        val h = host.get() ?: return null
        return h to port.get()
    }
}
