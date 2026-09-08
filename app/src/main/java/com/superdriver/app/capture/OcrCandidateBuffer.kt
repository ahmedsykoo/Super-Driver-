package com.superdriver.app.capture

import com.superdriver.app.calculator.TripDecisionResult
import com.superdriver.app.calculator.TripOfferInput

internal data class OcrCandidateSnapshot(
    val traceId: String,
    val createdAtMillis: Long,
    val input: TripOfferInput,
    val result: TripDecisionResult,
    val completeFieldCount: Int = input.completeFieldCount()
) {
    val hasCompleteOverlayData: Boolean
        get() = result.missingOverlayFields().isEmpty()
}

internal data class OcrCandidateBufferUpdate(
    val buffered: OcrCandidateSnapshot,
    val selected: OcrCandidateSnapshot?,
    val selectedReason: String?,
    val replacedTraceId: String?,
    val expiredTraceIds: List<String>
)

internal data class OcrCandidateBufferSelection(
    val selected: OcrCandidateSnapshot?,
    val selectedReason: String?,
    val expiredTraceIds: List<String>
)

internal class OcrCandidateBuffer(
    private val maxCandidates: Int = DEFAULT_MAX_CANDIDATES,
    private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS
) {
    private val candidates = mutableListOf<OcrCandidateSnapshot>()

    fun add(
        snapshot: OcrCandidateSnapshot,
        nowMillis: Long
    ): OcrCandidateBufferUpdate {
        val expiredTraceIds = pruneExpired(nowMillis).toMutableList()
        val previousBest = bestCandidate()

        candidates += snapshot
        while (candidates.size > maxCandidates) {
            expiredTraceIds += candidates.removeAt(0).traceId
        }

        val currentBest = bestCandidate()
        return OcrCandidateBufferUpdate(
            buffered = snapshot,
            selected = snapshot.takeIf { it.hasCompleteOverlayData },
            selectedReason = if (snapshot.hasCompleteOverlayData) "complete immediate" else null,
            replacedTraceId = previousBest
                ?.takeIf { currentBest != null && it.traceId != currentBest.traceId }
                ?.traceId,
            expiredTraceIds = expiredTraceIds
        )
    }

    fun selectReady(nowMillis: Long): OcrCandidateBufferSelection {
        if (candidates.isEmpty()) {
            return OcrCandidateBufferSelection(
                selected = null,
                selectedReason = null,
                expiredTraceIds = emptyList()
            )
        }

        if (candidates.none { snapshot -> nowMillis - snapshot.createdAtMillis >= retentionMillis }) {
            return OcrCandidateBufferSelection(
                selected = null,
                selectedReason = null,
                expiredTraceIds = pruneExpired(nowMillis)
            )
        }

        val selected = requireNotNull(bestCandidate())
        val expiredTraceIds = candidates
            .asSequence()
            .map { it.traceId }
            .filterNot { it == selected.traceId }
            .toList()
        candidates.clear()
        return OcrCandidateBufferSelection(
            selected = selected,
            selectedReason = "buffer window elapsed",
            expiredTraceIds = expiredTraceIds
        )
    }

    fun nextSelectionDelayMillis(nowMillis: Long): Long? {
        return candidates
            .minOfOrNull { snapshot -> snapshot.createdAtMillis + retentionMillis }
            ?.let { readyAtMillis -> (readyAtMillis - nowMillis).coerceAtLeast(0L) }
    }

    fun clear() {
        candidates.clear()
    }

    private fun pruneExpired(nowMillis: Long): List<String> {
        val expired = candidates
            .filter { snapshot -> nowMillis - snapshot.createdAtMillis > retentionMillis }
            .map { it.traceId }
        if (expired.isNotEmpty()) {
            candidates.removeAll { snapshot -> snapshot.traceId in expired }
        }
        return expired
    }

    private fun bestCandidate(): OcrCandidateSnapshot? {
        return candidates.maxWithOrNull(
            compareBy<OcrCandidateSnapshot> { if (it.hasCompleteOverlayData) 1 else 0 }
                .thenBy { it.completeFieldCount }
                .thenBy { it.createdAtMillis }
        )
    }

    private companion object {
        const val DEFAULT_MAX_CANDIDATES = 3
        const val DEFAULT_RETENTION_MILLIS = 700L
    }
}

internal fun TripOfferInput.completeFieldCount(): Int {
    return listOf(
        fareAmount,
        pickupKm,
        tripKm,
        pickupMinutes,
        tripMinutes,
        platform
    ).count { it != null }
}

internal fun TripDecisionResult.missingOverlayFields(): List<String> {
    return buildList {
        if (fareAmount == null) add("fare")
        if (totalKm == null) add("totalKm")
        if (totalMinutes == null) add("totalMinutes")
        if (egpPerKm == null) add("egpPerKm")
        if (egpPerHour == null) add("egpPerHour")
    }
}
