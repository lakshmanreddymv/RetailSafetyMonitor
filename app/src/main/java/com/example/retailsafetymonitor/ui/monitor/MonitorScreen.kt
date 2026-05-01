package com.example.retailsafetymonitor.ui.monitor

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.retailsafetymonitor.ui.components.HazardCard
import com.example.retailsafetymonitor.ui.components.HazardOverlay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Full-screen live camera composable with real-time hazard overlay.
 *
 * Responsibilities:
 * - Requests and handles [Manifest.permission.CAMERA] permission via Accompanist.
 * - Renders the CameraX [PreviewView] as an [AndroidView].
 * - Draws [HazardOverlay] on top of the preview for every frame.
 * - Shows a [BottomSheetScaffold] that expands when [MonitorUiState.HazardDetected] is active.
 * - Provides a FAB for pause/resume and a live status indicator in the top-right corner.
 *
 * Camera start/stop is delegated to [MonitorViewModel]; this composable never touches
 * CameraX directly.
 *
 * @param viewModel Hilt-injected [MonitorViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MonitorScreen(viewModel: MonitorViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val previewView = remember { PreviewView(context) }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    // Request camera permission on first launch
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    // Start camera once permission granted
    LaunchedEffect(cameraPermission.status.isGranted) {
        if (cameraPermission.status.isGranted && uiState is MonitorUiState.Idle) {
            viewModel.startCamera(lifecycleOwner, previewView)
        }
    }

    // Expand bottom sheet on HazardDetected, hide otherwise
    LaunchedEffect(uiState) {
        if (uiState is MonitorUiState.HazardDetected) {
            scaffoldState.bottomSheetState.expand()
        } else if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
            scaffoldState.bottomSheetState.hide()
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose { /* CameraX lifecycle tied to lifecycleOwner */ }
    }

    when {
        !cameraPermission.status.isGranted -> PermissionRationaleView(
            showRationale = cameraPermission.status.shouldShowRationale,
            onRequest = { cameraPermission.launchPermissionRequest() }
        )
        uiState is MonitorUiState.Error -> ErrorView(
            message = (uiState as MonitorUiState.Error).message,
            onRetry = { viewModel.retryAfterError(lifecycleOwner, previewView) }
        )
        else -> BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 0.dp,
            sheetContent = {
                if (uiState is MonitorUiState.HazardDetected) {
                    val hazardState = uiState as MonitorUiState.HazardDetected
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        Text("Hazard Detected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("5 seconds — tap to dismiss", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                        HazardCard(
                            hazard = hazardState.hazard,
                            onResolve = { /* navigate to incidents */ }
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Live camera preview
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Bounding box overlay (updates every frame, view-referenced coordinates)
                val overlayHazards = when (val s = uiState) {
                    is MonitorUiState.Monitoring -> s.detectedHazards
                    is MonitorUiState.HazardDetected -> s.allActiveHazards
                    else -> emptyList()
                }
                HazardOverlay(detectedHazards = overlayHazards)

                // Model loading indicator
                if (uiState is MonitorUiState.Monitoring &&
                    !(uiState as MonitorUiState.Monitoring).isModelReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Status indicator (pulsing green dot)
                MonitoringStatusIndicator(
                    isActive = uiState is MonitorUiState.Monitoring || uiState is MonitorUiState.HazardDetected,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                )

                // Pause/Resume FAB
                FloatingActionButton(
                    onClick = { viewModel.togglePause(lifecycleOwner, previewView) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    Icon(
                        imageVector = if (uiState is MonitorUiState.Paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (uiState is MonitorUiState.Paused) "Resume" else "Pause"
                    )
                }
            }
        }
    }
}

/**
 * Top-right status pill showing "● LIVE" (green) or "● PAUSED" (grey).
 *
 * @param isActive True when the camera is actively processing frames.
 * @param modifier Modifier for position; caller aligns it to [Alignment.TopEnd].
 */
@Composable
private fun MonitoringStatusIndicator(isActive: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = if (isActive) Color(0xFF43A047) else Color(0xFFBDBDBD),
                shape = androidx.compose.foundation.shape.CircleShape
            )
            .padding(8.dp)
    ) {
        Text(
            text = if (isActive) "● LIVE" else "● PAUSED",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Full-screen permission prompt shown when camera access has not been granted.
 *
 * @param showRationale True if the OS wants us to explain why the permission is needed
 *   (user previously denied once). False on first-launch or after "Don't ask again".
 * @param onRequest Callback that re-launches the system permission dialog.
 */
@Composable
private fun PermissionRationaleView(showRationale: Boolean, onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(
                text = if (showRationale) "Camera access is required to detect safety hazards. Please grant access to continue."
                else "Camera permission required",
                style = MaterialTheme.typography.bodyLarge
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
            androidx.compose.material3.Button(onClick = onRequest) {
                Text("Grant Camera Permission")
            }
        }
    }
}

/**
 * Full-screen error state shown when camera binding fails or a fatal exception occurs.
 *
 * @param message Human-readable error description from [MonitorUiState.Error.message].
 * @param onRetry Callback that calls [MonitorViewModel.retryAfterError].
 */
@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(text = "Error: $message", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE53935))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
            androidx.compose.material3.Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
