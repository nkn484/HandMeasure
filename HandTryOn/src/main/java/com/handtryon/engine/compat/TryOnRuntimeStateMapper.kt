package com.handtryon.engine.compat

import com.handtryon.coreengine.model.TryOnTrackingState as CoreTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction as CoreUpdateAction
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction

internal object TryOnRuntimeStateMapper {
    fun toCoreTrackingState(state: TryOnTrackingState): CoreTrackingState =
        when (state) {
            TryOnTrackingState.Searching -> CoreTrackingState.Searching
            TryOnTrackingState.Candidate -> CoreTrackingState.Candidate
            TryOnTrackingState.Locked -> CoreTrackingState.Locked
            TryOnTrackingState.Recovering -> CoreTrackingState.Recovering
        }

    fun toDomainTrackingState(state: CoreTrackingState): TryOnTrackingState =
        when (state) {
            CoreTrackingState.Searching -> TryOnTrackingState.Searching
            CoreTrackingState.Candidate -> TryOnTrackingState.Candidate
            CoreTrackingState.Locked -> TryOnTrackingState.Locked
            CoreTrackingState.Recovering -> TryOnTrackingState.Recovering
        }

    fun toCoreUpdateAction(action: TryOnUpdateAction): CoreUpdateAction =
        when (action) {
            TryOnUpdateAction.Update -> CoreUpdateAction.Update
            TryOnUpdateAction.FreezeScaleRotation -> CoreUpdateAction.FreezeScaleRotation
            TryOnUpdateAction.HoldLastPlacement -> CoreUpdateAction.HoldLastPlacement
            TryOnUpdateAction.Recover -> CoreUpdateAction.Recover
            TryOnUpdateAction.Hide -> CoreUpdateAction.Hide
        }

    fun toDomainUpdateAction(action: CoreUpdateAction): TryOnUpdateAction =
        when (action) {
            CoreUpdateAction.Update -> TryOnUpdateAction.Update
            CoreUpdateAction.FreezeScaleRotation -> TryOnUpdateAction.FreezeScaleRotation
            CoreUpdateAction.HoldLastPlacement -> TryOnUpdateAction.HoldLastPlacement
            CoreUpdateAction.Recover -> TryOnUpdateAction.Recover
            CoreUpdateAction.Hide -> TryOnUpdateAction.Hide
        }
}
