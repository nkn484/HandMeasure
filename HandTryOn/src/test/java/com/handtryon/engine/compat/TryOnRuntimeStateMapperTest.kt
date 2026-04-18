package com.handtryon.engine.compat

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import org.junit.Test

class TryOnRuntimeStateMapperTest {
    @Test
    fun tracking_state_round_trip_preserves_all_values() {
        val states = TryOnTrackingState.entries

        states.forEach { state ->
            val core = TryOnRuntimeStateMapper.toCoreTrackingState(state)
            val domain = TryOnRuntimeStateMapper.toDomainTrackingState(core)
            assertThat(domain).isEqualTo(state)
        }
    }

    @Test
    fun update_action_round_trip_preserves_all_values() {
        val actions = TryOnUpdateAction.entries

        actions.forEach { action ->
            val core = TryOnRuntimeStateMapper.toCoreUpdateAction(action)
            val domain = TryOnRuntimeStateMapper.toDomainUpdateAction(core)
            assertThat(domain).isEqualTo(action)
        }
    }
}
