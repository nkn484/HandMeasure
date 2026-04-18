package com.handmeasure.core.capture

class RollingCandidateWindow<KeyT, CandidateT>(
    private val maxPerKey: Int = 8,
    private val scoreSelector: (CandidateT) -> Float,
) {
    private val candidatesByKey = mutableMapOf<KeyT, ArrayDeque<CandidateT>>()

    fun add(
        key: KeyT,
        candidate: CandidateT,
    ) {
        val queue = candidatesByKey.getOrPut(key) { ArrayDeque() }
        queue.addLast(candidate)
        while (queue.size > maxPerKey) {
            queue.removeFirst()
        }
    }

    fun best(key: KeyT): CandidateT? = candidatesByKey[key]?.maxByOrNull(scoreSelector)

    fun clear(key: KeyT) {
        candidatesByKey.remove(key)
    }

    fun snapshot(key: KeyT): List<CandidateT> = candidatesByKey[key]?.toList().orEmpty()
}
