package dev.anonymous.yoloobjectdetector

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Crash-safe delegate probing journal.
 *
 * Before attempting a hardware delegate (GPU / NNAPI), the prober writes
 * a [STATE_PROBING] flag to disk via [SharedPreferences.Editor.commit] (synchronous).
 * If the delegate initializes and completes a warm-up inference successfully,
 * the flag is promoted to [STATE_SAFE]. If the process is killed by a native crash
 * (SIGSEGV / SIGABRT) during probing, the flag remains [STATE_PROBING]; on the next
 * cold start the prober detects this and promotes it to [STATE_BLOCKED], permanently
 * skipping that delegate for the given model.
 *
 * Tracking is per-model: a delegate may be safe for one .tflite file but crash with another.
 */
internal class DelegateProber(context: Context, private val modelPath: String) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class Delegate { GPU, NNAPI }

    /**
     * Returns true if the delegate is blocked (crashed previously) for this model.
     * Also converts a leftover [STATE_PROBING] (= crash happened) into [STATE_BLOCKED].
     */
    fun isBlocked(delegate: Delegate): Boolean {
        val key = key(delegate)
        return when (prefs.getInt(key, STATE_UNTESTED)) {
            STATE_BLOCKED -> true
            STATE_PROBING -> {
                prefs.edit(commit = true) { putInt(key, STATE_BLOCKED) }
                true
            }
            else -> false
        }
    }

    /** Returns true if the delegate has already been probed and marked safe. */
    fun isSafe(delegate: Delegate): Boolean =
        prefs.getInt(key(delegate), STATE_UNTESTED) == STATE_SAFE

    /** Marks the delegate as currently being probed (synchronous disk write). */
    fun markProbing(delegate: Delegate) {
        prefs.edit(commit = true) { putInt(key(delegate), STATE_PROBING) }
    }

    /** Marks the delegate as safe after a successful warm-up inference. */
    fun markSafe(delegate: Delegate) {
        prefs.edit(commit = true) { putInt(key(delegate), STATE_SAFE) }
    }

    /** Marks the delegate as blocked after a caught exception. */
    fun markBlocked(delegate: Delegate) {
        prefs.edit(commit = true) { putInt(key(delegate), STATE_BLOCKED) }
    }

    private fun key(delegate: Delegate): String = "${delegate.name}_$modelPath"

    companion object {
        private const val PREFS_NAME = "yolo_delegate_probing"
        private const val STATE_UNTESTED = 0
        private const val STATE_PROBING = 1
        private const val STATE_SAFE = 2
        private const val STATE_BLOCKED = 3
    }
}
