package com.buslive.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.buslive.app.ui.map.MapCommand
import com.buslive.app.ui.map.MapCommands
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(private val mapCommands: MapCommands) : ViewModel() {
    fun sendMapCommand(c: MapCommand) = mapCommands.send(c)
}
