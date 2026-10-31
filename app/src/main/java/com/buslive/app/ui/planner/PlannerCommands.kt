package com.buslive.app.ui.planner

import com.buslive.app.domain.model.Place
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** "Plan a trip to X" from any screen. The Plan tab picks it up, so there is only ever one planner screen. */
@Singleton
class PlannerCommands @Inject constructor() {
    private val channel = Channel<Place>(Channel.CONFLATED)
    val destinations: Flow<Place> = channel.receiveAsFlow()
    fun planTo(place: Place) { channel.trySend(place) }
}
