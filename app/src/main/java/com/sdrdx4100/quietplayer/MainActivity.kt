package com.sdrdx4100.quietplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdrdx4100.quietplayer.ui.QuietPlayerApp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> if (granted) viewModel.scanLibrary() }

            LaunchedEffect(Unit) {
                if (checkSelfPermission(audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    viewModel.scanLibrary()
                }
            }

            QuietPlayerApp(
                state = state,
                hasAudioPermission = checkSelfPermission(audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                requestPermission = { permissionLauncher.launch(audioPermission) },
                viewModel = viewModel,
            )
        }
    }
}
