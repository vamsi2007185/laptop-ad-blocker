package com.example.androidadblocker.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class QueryLog(
    val domain: String,
    val isBlocked: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class StatsState(
    val total: Long = 0,
    val blocked: Long = 0,
    val allowed: Long = 0,
    val isRunning: Boolean = false,
    val recentQueries: List<QueryLog> = emptyList()
)

object BlockerStats {
    private val _total = AtomicLong(0)
    private val _blocked = AtomicLong(0)
    private val _allowed = AtomicLong(0)
    private val recentList = mutableListOf<QueryLog>()

    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    fun setRunning(running: Boolean) {
        synchronized(this) {
            _state.value = _state.value.copy(isRunning = running)
        }
    }

    fun record(domain: String, blocked: Boolean) {
        val total = _total.incrementAndGet()
        val blockedCount = if (blocked) _blocked.incrementAndGet() else _blocked.get()
        val allowedCount = if (!blocked) _allowed.incrementAndGet() else _allowed.get()

        synchronized(this) {
            recentList.add(0, QueryLog(domain, blocked))
            if (recentList.size > 50) {
                recentList.removeAt(recentList.lastIndex)
            }
            _state.value = _state.value.copy(
                total = total,
                blocked = blockedCount,
                allowed = allowedCount,
                recentQueries = recentList.toList()
            )
        }
    }

    fun reset() {
        _total.set(0)
        _blocked.set(0)
        _allowed.set(0)
        synchronized(this) {
            recentList.clear()
            _state.value = _state.value.copy(
                total = 0,
                blocked = 0,
                allowed = 0,
                recentQueries = emptyList()
            )
        }
    }
}
