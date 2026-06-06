package ru.course.roguelike.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.course.roguelike.shared.dto.InputSyncRequest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SyncInputDispatcher(
    private val scope: CoroutineScope,
    private val sessionGeneration: AtomicInteger,
    private val syncOutboundSeq: AtomicInteger,
    private val pendingInput: AtomicReference<InputSyncRequest?>,
    private val sessionIdProvider: () -> String?,
    private val mergeInput: (InputSyncRequest, InputSyncRequest) -> InputSyncRequest,
    private val onBatch: suspend (sessionId: String, seq: Int, input: InputSyncRequest, gen: Int) -> Unit,
) {
    private val syncInFlight = AtomicBoolean(false)

    fun enqueue(input: InputSyncRequest) {
        while (true) {
            val previous = pendingInput.get()
            val merged = if (previous == null) input else mergeInput(previous, input)
            if (pendingInput.compareAndSet(previous, merged)) return
        }
    }

    fun pump() {
        if (sessionIdProvider() == null) return
        if (!syncInFlight.compareAndSet(false, true)) return
        val gen = sessionGeneration.get()
        scope.launch {
            try {
                drain(gen)
            } finally {
                syncInFlight.set(false)
                if (pendingInput.get() != null && sessionIdProvider() != null) {
                    pump()
                }
            }
        }
    }

    private suspend fun drain(gen: Int) {
        while (sessionGeneration.get() == gen) {
            val batch = nextBatch() ?: return
            onBatch(batch.sessionId, batch.seq, batch.input, gen)
        }
    }

    private fun nextBatch(): SyncBatch? {
        val sessionId = sessionIdProvider() ?: return null
        val input = pendingInput.getAndSet(null) ?: return null
        val seq = syncOutboundSeq.incrementAndGet()
        return SyncBatch(sessionId, seq, input)
    }

    private data class SyncBatch(
        val sessionId: String,
        val seq: Int,
        val input: InputSyncRequest,
    )
}
