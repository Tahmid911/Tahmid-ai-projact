package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.agent.configureAppCheck
import com.example.service.ScreenCaptureService
import com.example.ui.AgentViewModel
import com.example.ui.AutoPilotScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val agentViewModel: AgentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        // Register host Activity for fallback screen capture when MediaProjection is on standby
        ScreenCaptureService.registerHostActivity(this)

        // STEP 2: Enable Edge-to-Edge and initialize Compose UI
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AutoPilotScreen(viewModel = agentViewModel)
                }
            }
        }
    }
}
