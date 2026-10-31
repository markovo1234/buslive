package com.buslive.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.buslive.app.domain.model.Place
import com.buslive.app.ui.map.MapCommand
import com.buslive.app.ui.planner.PlannerCommands
import com.buslive.app.ui.map.MapCommands
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val mapCommands: MapCommands,
    private val plannerCommands: PlannerCommands,
) : ViewModel() {
    fun sendMapCommand(c: MapCommand) = mapCommands.send(c)
    fun planTo(place: Place) = plannerCommands.planTo(place)
}
