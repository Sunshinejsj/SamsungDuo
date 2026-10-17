package com.jsj.samsungduo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The process the module hooks, and therefore the one that has to come back for
 * a change to be visible at all. Killed by package name rather than by pid: the
 * shell that hosts it is named differently across versions, and `killall`
 * matches the process's own name.
 */
private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

/**
 * The root shell's name, as shipped by Magisk, KernelSU and APatch alike.
 *
 * <p>Not resolved from the PATH ourselves: if `su` is genuinely absent the
 * [ProcessBuilder] fails with the same message the shell would print, and that
 * message is what the user gets to see.
 */
private const val ROOT_SHELL = "su"

/** Everything the restart sheet can be showing. */
internal sealed interface RestartState {
    /** Nothing asked yet, or the sheet was closed after a finished attempt. */
    data object Idle : RestartState

    /** A root prompt may be on screen; the command has not answered yet. */
    data object Running : RestartState

    /** The kill was accepted, which is as much as the shell can report. */
    data object Done : RestartState

    /**
     * @param detail the shell's own words — a refusal code, or the `IOException`
     *   raised when there is no `su` at all. Kept raw because it is diagnostic
     *   text that would lose its meaning if it were translated.
     */
    data class Failed(val detail: String?) : RestartState
}

/**
 * Owns the one-shot "restart System UI" action.
 *
 * <p>The write happens off the main thread and the outcome lands back on it, so
 * the sheet can read [state] as if it were a plain value.
 *
 * <p>Held at screen level rather than inside the sheet: the sheet animates out
 * after the confirm is tapped, and a kill that straddled that boundary would
 * keep running with nothing left to report to.
 */
internal class RestartController {

    var state: RestartState by mutableStateOf(RestartState.Idle)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun dispose() = scope.cancel()

    /**
     * Clears a finished attempt so the next opening starts from a blank sheet.
     * Ignored while running, which is the only state whose answer still matters.
     */
    fun reset() {
        if (state is RestartState.Running) return
        state = RestartState.Idle
    }

    /**
     * Kills System UI through the root shell. Re-entrant calls are ignored: the
     * button is disabled while running, but the kill is not something to issue
     * twice if it ever were reachable.
     */
    fun restart() {
        if (state is RestartState.Running) return
        state = RestartState.Running
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { killSystemUi() } }
            state = result.fold(
                onSuccess = { RestartState.Done },
                onFailure = { RestartState.Failed(it.message?.takeIf(String::isNotBlank)) },
            )
        }
    }

    /**
     * Runs `su -c "killall com.android.systemui"` and insists on a zero status.
     *
     * <p>`su -c` rather than feeding commands into a bare `su`: the exit status
     * then belongs to the command that matters, so a refusal at the
     * authorisation prompt and a failure of the kill itself are both visible as
     * a non-zero code, and neither has to be guessed at from the output.
     *
     * <p>stdout and stderr are merged and drained *before* waiting, because a
     * shell that fills its unread pipe blocks on write forever and `waitFor`
     * would never return.
     */
    private fun killSystemUi() {
        val process = ProcessBuilder(ROOT_SHELL, "-c", "killall $SYSTEM_UI_PACKAGE")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exit = process.waitFor()
        check(exit == 0) { output.ifBlank { "$ROOT_SHELL exited with code $exit" } }
    }
}

/**
 * Whether a `su` binary is reachable on the device — i.e. a root solution
 * (Magisk / KernelSU / APatch) is installed. A present binary does not mean the
 * user has granted this app root; it only means the restart can go on to ask for
 * it. The actual `su -c` in [RestartController.killSystemUi] is what surfaces
 * the grant prompt, and a refusal there lands as [RestartState.Failed].
 */
internal fun isRootAvailable(): Boolean {
    return runCatching {
        val process = ProcessBuilder("sh", "-c", "command -v su || which su")
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        out.isNotEmpty()
    }.getOrDefault(false)
}
