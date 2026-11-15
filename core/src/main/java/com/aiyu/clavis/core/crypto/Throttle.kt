package com.aiyu.clavis.core.crypto

/**
 * PIN attempt throttling: 5 free attempts, then 30 s, 1 min, 5 min, 15 min, 1 h (repeats the last step).
 * Times are SystemClock.elapsedRealtime, which ignores wall-clock changes but restarts at boot.
 */
object Throttle {
    const val FREE_ATTEMPTS = 5
    private val schedule = longArrayOf(30_000, 60_000, 300_000, 900_000, 3_600_000)

    /** Lockout that starts after the [failures]-th consecutive wrong PIN; 0 while attempts are free. */
    fun lockoutAfter(failures: Int): Long =
        if (failures < FREE_ATTEMPTS) 0 else schedule[minOf(failures - FREE_ATTEMPTS, schedule.lastIndex)]

    fun attemptsLeft(failures: Int): Int = (FREE_ATTEMPTS - failures).coerceAtLeast(0)

    /**
     * The stored lockout end, corrected for a reboot: if the lockout seems to have started in the future, the
     * clock restarted, so the full lockout runs again from [now] instead of expiring early.
     */
    fun normalizedLockUntil(lockUntil: Long, duration: Long, now: Long): Long =
        if (lockUntil - duration > now) now + duration else lockUntil
}
