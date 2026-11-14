package com.pocketsamp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pocketsamp.ui.PocketSampApp
import com.pocketsamp.ui.theme.PocketSampTheme
import com.pocketsamp.viewmodel.ServerViewModel

class MainActivity : ComponentActivity() {

    private val serverViewModel: ServerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> serverViewModel.bindServices()
                    Lifecycle.Event.ON_RESUME -> serverViewModel.refreshEnvironmentState()
                    Lifecycle.Event.ON_STOP -> serverViewModel.unbindServices()
                    else -> Unit
                }
            }
        )

        setContent {
            PocketSampTheme {
                PocketSampApp(serverViewModel = serverViewModel)
            }
        }
    }
}
