package com.handmeasure.core.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RollingCandidateWindowTest {
    private data class Candidate(
        val score: Float,
        val id: String,
    )

    @Test
    fun best_returnsHighestScoreWithinWindow() {
        val window = RollingCandidateWindow<String, Candidate>(maxPerKey = 3, scoreSelector = { it.score })

        window.add("bucket", Candidate(score = 0.62f, id = "a"))
        window.add("bucket", Candidate(score = 0.74f, id = "b"))
        window.add("bucket", Candidate(score = 0.69f, id = "c"))

        assertThat(window.best("bucket")?.id).isEqualTo("b")
    }

    @Test
    fun best_ignoresExpiredFramesOutsideWindow() {
        val window = RollingCandidateWindow<String, Candidate>(maxPerKey = 2, scoreSelector = { it.score })

        window.add("bucket", Candidate(score = 0.95f, id = "old"))
        window.add("bucket", Candidate(score = 0.66f, id = "new-1"))
        window.add("bucket", Candidate(score = 0.68f, id = "new-2"))

        assertThat(window.best("bucket")?.id).isEqualTo("new-2")
    }
}
