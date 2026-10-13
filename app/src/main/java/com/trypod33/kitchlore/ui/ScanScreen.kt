package com.trypod33.kitchlore.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.ScaleGestureDetector
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trypod33.kitchlore.data.OcrParser
import com.trypod33.kitchlore.data.ParsedRecipe
import com.trypod33.kitchlore.data.local.AppDatabase
import java.io.File
import java.io.FileOutputStream

// Camera scan and gallery photo import, both ending in the same OCR ->
// parse -> review -> save flow as the Termux /scan route.

@Composable
fun ScanRoute(
    db: AppDatabase,
    mode: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<Pair<ParsedRecipe, String?>?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            runOcr(context, uri) { p, path ->
                busy = false
                parsed = p to path
            }
        }
    }

    val p = parsed
    if (p != null) {
        ReviewImportScreen(
            db = db,
            parsed = p.first,
            photoPath = p.second,
            sourceNote = if (mode == "photo") "Imported from photo" else "Scanned from camera",
            onSaved = onDone,
            onCancel = onBack,
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text(
                if (mode == "photo") "Import photo" else "Scan recipe",
                fontSize = 24.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        when {
            mode == "photo" -> {
                LaunchedEffect(Unit) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            !granted -> {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Kitchlore needs the camera to photograph recipes.")
                    Button(
                        onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Allow camera") }
                }
            }
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { view ->
                                providerFuture.addListener(
                                    {
                                        val provider = providerFuture.get()
                                        val cameraPreview =
                                            androidx.camera.core.Preview.Builder().build()
                                        cameraPreview.setSurfaceProvider(view.surfaceProvider)
                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                            .build()
                                        imageCapture = capture
                                        provider.unbindAll()
                                        val camera = provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            cameraPreview,
                                            capture,
                                        )
                                        cameraRef = camera
                                        // Pinch to zoom: scale the current zoom ratio within the lens limits.
                                        val zoomer = ScaleGestureDetector(
                                            ctx,
                                            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                                override fun onScale(detector: ScaleGestureDetector): Boolean {
                                                    val state = camera.cameraInfo.zoomState.value
                                                    val next = ((state?.zoomRatio ?: 1f) * detector.scaleFactor)
                                                        .coerceIn(
                                                            state?.minZoomRatio ?: 1f,
                                                            state?.maxZoomRatio ?: 1f,
                                                        )
                                                    camera.cameraControl.setZoomRatio(next)
                                                    return true
                                                }
                                            },
                                        )
                                        view.setOnTouchListener { v, event ->
                                            zoomer.onTouchEvent(event)
                                            v.performClick()
                                            true
                                        }
                                    },
                                    ContextCompat.getMainExecutor(ctx),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Button(
                    onClick = {
                        busy = true
                        error = null
                        val out = File(context.cacheDir, "scan-${System.currentTimeMillis()}.jpg")
                        imageCapture?.takePicture(
                            ImageCapture.OutputFileOptions.Builder(out).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                    runOcr(context, out) { parsedRecipe, path ->
                                        busy = false
                                        parsed = parsedRecipe to path
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    busy = false
                                    error = "Couldn't take the picture"
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp),
                ) { Text("Photograph recipe page") }
            }
        }

        if (busy) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text("Reading the page...", modifier = Modifier.padding(top = 8.dp))
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
        }
    }
}


private fun runOcr(context: android.content.Context, image: Any, onDone: (ParsedRecipe, String?) -> Unit) {
    val input = try {
        when (image) {
            is File -> InputImage.fromFilePath(context, Uri.fromFile(image))
            is Uri -> InputImage.fromFilePath(context, image)
            else -> null
        }
    } catch (t: Throwable) {
        null
    }
    if (input == null) {
        onDone(OcrParser.parse(""), null)
        return
    }
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(input)
        .addOnSuccessListener { visionText ->
            val blocks = visionText.textBlocks.flatMap { block ->
                block.lines.map { line -> line.text to (line.boundingBox?.height() ?: 0) }
            }
            val parsedRecipe = OcrParser.parse(visionText.text, blocks)
            val path = when (image) {
                is File -> image.absolutePath
                is Uri -> copyUriToPhotos(context, image)
                else -> null
            }
            onDone(parsedRecipe, path)
        }
        .addOnFailureListener {
            onDone(OcrParser.parse(""), null)
        }
}

private fun copyUriToPhotos(context: android.content.Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "photos")
        dir.mkdirs()
        val out = File(dir, "imported-${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { ins ->
            FileOutputStream(out).use { ins.copyTo(it) }
        }
        if (out.length() > 0L) out.absolutePath else null
    } catch (t: Throwable) {
        null
    }
}
