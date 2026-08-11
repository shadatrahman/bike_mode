package com.shadatrahman.bikemode.notifications

/** In-memory [InterruptionSettings]. [filter] stands in for the phone's Do Not Disturb state. */
class FakeInterruptionSettings(
    override val canControl: Boolean = true,
    var filter: Int = FILTER_ALL,
) : InterruptionSettings {

    var failWrites = false

    override fun current(): Int = filter

    override fun apply(): Result<Unit> = write { filter = FILTER_PRIORITY }

    override fun restore(previous: Int?): Result<Unit> = write {
        if (previous != null && previous != FILTER_UNKNOWN) filter = previous
    }

    private fun write(block: () -> Unit): Result<Unit> =
        if (failWrites || !canControl) Result.failure(IllegalStateException("No policy access"))
        else Result.success(block())

    companion object {
        /** Mirrors of the NotificationManager constants, which are unavailable in a unit test. */
        const val FILTER_UNKNOWN = 0
        const val FILTER_ALL = 1
        const val FILTER_PRIORITY = 2
        const val FILTER_NONE = 3
        const val FILTER_ALARMS = 4
    }
}
