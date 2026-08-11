package com.shadatrahman.bikemode.util

import android.util.Log

/**
 * Runs [block], and on failure says what broke rather than returning [fallback] in silence.
 *
 * The system calls this wraps fail for reasons that look identical from the outside — a permission
 * refused, an adapter that has gone away, a profile proxy that never bound — and every one of them
 * reaches the rider as the same "helmet not connected". Swallowing that difference once already
 * cost a round of guessing over the companion-device switch, where a missing manifest entry looked
 * exactly like a cancelled dialog. This at least leaves a trail in logcat.
 *
 * [fallback] is the honest answer in each case: no devices, not connected, nothing done.
 */
internal inline fun <T> reportingFailure(
    tag: String,
    what: String,
    fallback: T,
    block: () -> T,
): T = try {
    block()
} catch (e: Exception) {
    Log.w(tag, "$what failed: ${e.javaClass.simpleName}: ${e.message}")
    fallback
}
