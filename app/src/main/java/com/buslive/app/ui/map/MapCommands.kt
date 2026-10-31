package com.buslive.app.ui.map

import com.buslive.app.domain.model.GeoPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MapCommand {
    data class FocusStop(val code: Int) : MapCommand
    data class ShowLine(val lineRef: Long) : MapCommand
    data class FocusPoint(val point: GeoPoint) : MapCommand
}

/** Other screens ask the map to focus something, then switch to the map tab. */
@Singleton
class MapCommands @Inject constructor() {
    private val channel = Channel<MapCommand>(Channel.BUFFERED)
    val commands: Flow<MapCommand> = channel.receiveAsFlow()
    fun send(c: MapCommand) { channel.trySend(c) }
}
