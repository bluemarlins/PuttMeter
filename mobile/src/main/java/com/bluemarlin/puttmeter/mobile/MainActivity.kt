package com.bluemarlin.puttmeter.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bluemarlin.puttmeter.mobile.presentation.PuttMeterMobileApp
import com.bluemarlin.puttmeter.mobile.presentation.PuttingViewModel
import com.bluemarlin.puttmeter.mobile.ui.theme.PuttMeterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            PuttMeterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: PuttingViewModel = viewModel()
                    PuttMeterMobileApp(viewModel = viewModel)
                }
            }
        }
    }
}
