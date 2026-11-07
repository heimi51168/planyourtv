package com.planyourtv.policy

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The one voice bus shared by both engines; the app owns the OGG player. */
object PolicyVoiceBus {
    private val _cues = MutableSharedFlow<PolicyVoiceCue>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val cues: SharedFlow<PolicyVoiceCue> = _cues.asSharedFlow()

    fun emit(cue: PolicyVoiceCue) {
        _cues.tryEmit(cue)
    }
}
