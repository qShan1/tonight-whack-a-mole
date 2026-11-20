package com.example.windowbirds.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.windowbirds.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
