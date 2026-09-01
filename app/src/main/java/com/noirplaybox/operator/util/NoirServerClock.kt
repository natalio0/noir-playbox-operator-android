package com.noirplaybox.operator.util

import java.util.concurrent.atomic.AtomicLong

object NoirServerClock {
    private val offsetMs = AtomicLong(0L)
    private val synced = AtomicLong(0L)

    fun update(serverEpochMs: Long, localReceivedAtMs: Long = System.currentTimeMillis()) {
        if (serverEpochMs <= 0L) return
        offsetMs.set(serverEpochMs - localReceivedAtMs)
        synced.set(1L)
    }

    fun nowEpochMs(): Long = System.currentTimeMillis() + offsetMs.get()

    fun isSynced(): Boolean = synced.get() == 1L

    fun offsetMillis(): Long = offsetMs.get()
}
