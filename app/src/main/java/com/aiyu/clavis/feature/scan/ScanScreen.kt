package com.aiyu.clavis.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aiyu.clavis.core.card.CardTextParser
import com.aiyu.clavis.core.card.ScanStabilizer
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.components.CARD_RATIO
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/** Card numbers read by the camera, handed to the add form in memory only (never through nav args or saved state). */
object ScanHandoff {
    data class Result(val pan: String, val expiry: String?, val holder: String?)
    @Volatile var result: Result? = null
    fun take(): Result? = result.also { result = null }
}

/**
 * Scan (06-UX-DESIGN.md screen 6): camera with an ID-1 guide frame, torch, "Enter manually" always visible.
 * On-device OCR; a PAN is accepted after 3 of 5 frames agree. The user reviews everything in the add form.
 */
@Composable
fun ScanScreen(onBack: () -> Unit, onResult: () -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; denied = !it }

    if (granted) CameraScan(onBack, onResult, onManual)
    else PermissionRationale(denied, onAllow = { launcher.launch(Manifest.permission.CAMERA) }, onManual = onManual, onBack = onBack)
}

@Composable
private fun PermissionRationale(denied: Boolean, onAllow: () -> Unit, onManual: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(112.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (denied) R.string.scan_permission_denied else R.string.scan_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(min = ButtonDefaults.MediumContainerHeight)) {
            Text(stringResource(R.string.scan_permission_grant), style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onManual) { Text(stringResource(R.string.action_enter_manually)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScan(onBack: () -> Unit, onResult: () -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycle) {
        val executor = Executors.newSingleThreadExecutor()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val stabilizer = ScanStabilizer()
        var expiry: String? = null
        var holder: String? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || locked) { proxy.close(); return@setAnalyzer }
                recognizer.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { text ->
                        // shortcut: parses the whole frame, not only text inside the guide; the Luhn + 3-of-5 filter keeps it accurate enough.
                        val card = CardTextParser.parse(text.textBlocks.flatMap { b -> b.lines.map { it.text } })
                        card?.expiry?.let { expiry = it }
                        card?.holder?.let { holder = it }
                        if (!locked && stabilizer.offer(card?.pan)) {
                            locked = true
                            ScanHandoff.result = ScanHandoff.Result(card!!.pan, expiry, holder)
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            previewView.postDelayed(onResult, 450) // let the frame turn green first
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
            recognizer.close()
            executor.shutdown()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            GuideFrame(locked)
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(12.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (locked) R.string.scan_found else R.string.scan_hint),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (camera?.cameraInfo?.hasFlashUnit() == true) FilledTonalIconToggleButton(
                        checked = torch,
                        onCheckedChange = { torch = it; camera?.cameraControl?.enableTorch(it) },
                    ) { Icon(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, contentDescription = stringResource(R.string.scan_torch)) }
                    if (locked) LoadingIndicator(Modifier.size(40.dp))
                    TextButton(onClick = onManual) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_enter_manually))
                    }
                }
            }
        }
    }
}

/** Dims everything but an ID-1 shaped window; the outline turns tertiary (green) on lock-in. */
@Composable
private fun GuideFrame(locked: Boolean) {
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)
    val outline by animateColorAsState(if (locked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, label = "frame")
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width * 0.86f
        val h = w / CARD_RATIO
        val topLeft = Offset((size.width - w) / 2, (size.height - h) / 2)
        val radius = CornerRadius(20.dp.toPx())
        drawRect(scrim)
        drawRoundRect(scrim, topLeft, Size(w, h), radius, blendMode = BlendMode.Clear)
        drawRoundRect(outline, topLeft, Size(w, h), radius, style = Stroke(if (locked) 5.dp.toPx() else 3.dp.toPx()))
    }
}
