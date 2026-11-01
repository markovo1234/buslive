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
    data class Request(val place: Place, val fromHere: Boolean)

    private val channel = Channel<Request>(Channel.CONFLATED)
    val destinations: Flow<Request> = channel.receiveAsFlow()
    fun planTo(place: Place) { channel.trySend(Request(place, fromHere = false)) }
    /** Trip recovery: new route from my current location. */
    fun replanFromHere(place: Place) { channel.trySend(Request(place, fromHere = true)) }
}
