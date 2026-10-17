package com.trypod33.kitchlore.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.ScaleGestureDetector
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trypod33.kitchlore.data.OcrParser
import com.trypod33.kitchlore.data.RegionBlock
import com.trypod33.kitchlore.data.RegionKind
import com.trypod33.kitchlore.data.RegionLabeler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.trypod33.kitchlore.data.RecipeLlm
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scope = rememberCoroutineScope()
    var aiStatus by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<Pair<ParsedRecipe, String?>?>(null) }
    // Multi-photo recipes: each capture lands here, and the reviewer gets
    // one merged recipe with the overlapping lines deduped.
    var staged by remember { mutableStateOf<List<Pair<ParsedRecipe, String?>>>(emptyList()) }
    // A page can hold more than one recipe; each one gets its own review,
    // one after another.
    var pendingReviews by remember { mutableStateOf<List<Pair<ParsedRecipe, String?>>>(emptyList()) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    // Learned measurement corrections, applied by the parser before the AI pass.
    var rules by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        rules = withContext(Dispatchers.IO) {
            db.correctionRuleDao().getAllOnce().associate { it.garbled to it.replacement }
        }
    }

    fun stage(recipe: ParsedRecipe, path: String?) {
        staged = staged + (recipe to path)
        aiStatus = null
    }

    fun handleParsed(recipes: List<ParsedRecipe>, path: String?) {
        if (recipes.size <= 1) {
            val only = recipes.firstOrNull() ?: return
            stage(only, path)
            return
        }
        // One page, several recipes: review them one at a time, starting now.
        parsed = recipes.first() to path
        pendingReviews = recipes.drop(1).map { it to path }
        aiStatus = null
    }

    fun enhance(recipe: ParsedRecipe, path: String?) {
        scope.launch {
            try {
                val fixed = RecipeLlm.enhance(context, recipe) { st -> aiStatus = st.ifBlank { null } }
                parsed = fixed to path
                aiStatus = null
            } catch (t: Throwable) {
                // The AI pass is optional; the deterministic parse still stands.
                parsed = recipe to path
                aiStatus = null
            }
        }
    }

    fun reviewStaged() {
        if (staged.isEmpty()) return
        val merged = mergeRecipes(staged.map { it.first })
        enhance(merged, staged.first().second)
    }


    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            runOcr(context, uri, rules) { recipes, path ->
                busy = false
                handleParsed(recipes, path)
            }
        }
    }

    // Play-services document scanner: finds the card edges, straightens and
    // cleans the page, then hands back the image this screen already knows
    // how to read. This is the path that reads recipe cards well.
    val docScan = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val scan = result.data?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }
        val uris = scan?.pages?.mapNotNull { it.imageUri }.orEmpty()
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        fun next(i: Int) {
            if (i >= uris.size) {
                busy = false
                return
            }
            runOcr(context, uris[i], rules) { recipes, path ->
                recipes.forEach { stage(it, path) }
                next(i + 1)
            }
        }
        next(0)
    }
    fun launchDocScan() {
        val activity = context as? Activity
        if (activity == null) {
            error = "Document scanning isn't available here"
            return
        }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(3)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        busy = true
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                busy = false
                docScan.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener {
                busy = false
                error = "Document scanning isn't available on this phone; use the photo buttons instead"
            }
    }

    val p = parsed
    if (p != null) {
        ReviewImportScreen(
            db = db,
            parsed = p.first,
            photoPath = p.second,
            sourceNote = if (mode == "photo") "Imported from photo" else "Scanned from camera",
            onSaved = {
                val next = pendingReviews
                if (next.isEmpty()) {
                    onDone()
                } else {
                    pendingReviews = next.drop(1)
                    parsed = next.first()
                }
            },
            onCancel = {
                val next = pendingReviews
                if (next.isEmpty()) {
                    onBack()
                } else {
                    pendingReviews = next.drop(1)
                    parsed = next.first()
                }
            },
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

        aiStatus?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )
        }

        when {
            mode == "photo" -> {
                LaunchedEffect(Unit) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (staged.isEmpty() && parsed == null && !busy) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Button(
                            onClick = { launchDocScan() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text("Scan document (auto-crop)") }
                        OutlinedButton(
                            onClick = {
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(56.dp),
                        ) { Text("Pick a photo") }
                    }
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
                        error = null
                        launchDocScan()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).height(56.dp),
                ) { Text("Scan document (auto-crop)") }
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
                                    runOcr(context, out, rules) { recipes, path ->
                                        busy = false
                                        handleParsed(recipes, path)
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

        if (staged.isNotEmpty() && parsed == null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    if (staged.size == 1) "1 page ready" else "${staged.size} pages ready",
                    fontSize = 15.sp,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { reviewStaged() }) { Text("Review recipe") }
                    if (mode == "photo") {
                        OutlinedButton(onClick = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("Add another page") }
                    }
                }
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


private fun runOcr(
    context: android.content.Context,
    image: Any,
    rules: Map<String, String>,
    onDone: (List<ParsedRecipe>, String?) -> Unit,
) {
    val input = try {
        when (image) {
            is File -> loadPreppedBitmap(context, Uri.fromFile(image))?.let { InputImage.fromBitmap(it, 0) }
            is Uri -> loadPreppedBitmap(context, image)?.let { InputImage.fromBitmap(it, 0) }
            else -> null
        }
    } catch (t: Throwable) {
        null
    }
    if (input == null) {
        onDone(listOf(OcrParser.parse("", emptyList(), rules)), null)
        return
    }
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(input)
        .addOnSuccessListener { visionText ->
            val blocks = visionText.textBlocks.flatMap { block ->
                block.lines.map { line -> line.text to (line.boundingBox?.height() ?: 0) }
            }
            val boxes = visionText.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val b = line.boundingBox ?: return@mapNotNull null
                    OcrParser.OcrLineBox(line.text, b.left, b.top, b.right, b.bottom)
                }
            }
            val regionBlocks = visionText.textBlocks.mapNotNull { block ->
                val box = block.boundingBox ?: return@mapNotNull null
                RegionBlock(block.text, box.left, box.top, box.right, box.bottom)
            }.sortedWith(compareBy({ it.top }, { it.left }))
            CoroutineScope(Dispatchers.Main.immediate).launch {
                val labels = try {
                    RegionLabeler.label(context, regionBlocks) { }
                } catch (t: Throwable) {
                    null
                }
                val path = when (image) {
                    is File -> image.absolutePath
                    is Uri -> copyUriToPhotos(context, image)
                    else -> null
                }
                val recipes: List<ParsedRecipe> = labels?.let { ls ->
                    RegionLabeler.splitRecipes(regionBlocks, ls).map { group ->
                        val title = StringBuilder()
                        val ing = StringBuilder()
                        val steps = StringBuilder()
                        for (b in group) when (ls[regionBlocks.indexOf(b)]) {
                            RegionKind.TITLE -> title.append(b.text).append('\n')
                            RegionKind.INGREDIENTS -> ing.append(b.text).append('\n')
                            RegionKind.STEPS -> steps.append(b.text).append('\n')
                            RegionKind.OTHER -> {}
                        }
                        OcrParser.parse(
                            visionText.text, blocks, rules, boxes,
                            regions = OcrParser.Regions(title.toString(), ing.toString(), steps.toString()),
                        )
                    }
                } ?: listOf(OcrParser.parse(visionText.text, blocks, rules, boxes))
                onDone(recipes, path)
            }
        }
        .addOnFailureListener {
            onDone(listOf(OcrParser.parse("", emptyList(), rules)), null)
        }
}

// The scanner garbles fraction glyphs (1/4, 1/3, 1/2) on dim phone photos;
// loading the page full-size, boosting contrast and upscaling small captures
// gives the recognizer a much cleaner image to read.
private fun loadPreppedBitmap(context: android.content.Context, uri: Uri): Bitmap? { return try {
    val resolver = context.contentResolver
    val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: return null
    val rotation = resolver.openInputStream(uri)?.use { ins ->
        when (ExifInterface(ins).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val upright = if (rotation != 0f)
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
    else bmp
    preppedForOcr(upright)
} catch (t: Throwable) {
    null
} }

private fun preppedForOcr(src: Bitmap): Bitmap {
    // keep the OCR workload sane, then upscale small captures so glyphs are legible
    val cap = 2600
    val longest = maxOf(src.width, src.height)
    val capped = if (longest > cap) {
        val f = cap.toFloat() / longest
        Bitmap.createScaledBitmap(src, (src.width * f).toInt().coerceAtLeast(1), (src.height * f).toInt().coerceAtLeast(1), true)
    } else src
    val scale = if (longest < 1600) 2f else 1f
    val big = if (scale > 1f)
        Bitmap.createScaledBitmap(capped, (capped.width * scale).toInt(), (capped.height * scale).toInt(), true)
    else capped
    val w = big.width
    val h = big.height
    val px = IntArray(w * h)
    big.getPixels(px, 0, w, 0, 0, w, h)
    var lo = 255
    var hi = 0
    val lum = IntArray(w * h)
    for (i in px.indices) {
        val p = px[i]
        val l = (299 * ((p shr 16) and 0xFF) + 587 * ((p shr 8) and 0xFF) + 114 * (p and 0xFF)) / 1000
        lum[i] = l
        if (l < lo) lo = l
        if (l > hi) hi = l
    }
    val range = (hi - lo).coerceAtLeast(1)
    for (i in lum.indices) {
        val v = (((lum[i] - lo) * 255) / range).coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    // Adaptive local contrast: on a dim phone shot (a card on a dark table)
    // a global stretch is not enough, the shadow across one corner still
    // swamps the glyphs there. Each pixel is contrasted against its own
    // neighborhood's average instead, so shadowed areas read like bright
    // ones and the fraction glyphs come out crisp.
    val mapW = (w / 48).coerceAtLeast(1)
    val mapH = (h / 48).coerceAtLeast(1)
    val small = IntArray(mapW * mapH)
    for (my in 0 until mapH) {
        val sy = my * h / mapH
        val ey = (my + 1) * h / mapH
        for (mx in 0 until mapW) {
            val sx = mx * w / mapW
            val ex = (mx + 1) * w / mapW
            var sum = 0L
            var n = 0L
            var y = sy
            while (y < ey) {
                var x = sx
                val row = y * w
                while (x < ex) {
                    sum += (px[row + x] and 0xFF).toLong()
                    n++
                    x++
                }
                y++
            }
            small[my * mapW + mx] = (sum / n.coerceAtLeast(1L)).toInt()
        }
    }
    fun meanAt(x: Int, y: Int): Int {
        val fx = if (w > 1) (x * (mapW - 1)).toFloat() / (w - 1) else 0f
        val fy = if (h > 1) (y * (mapH - 1)).toFloat() / (h - 1) else 0f
        val x0 = fx.toInt().coerceIn(0, mapW - 1)
        val y0 = fy.toInt().coerceIn(0, mapH - 1)
        val x1 = (x0 + 1).coerceAtMost(mapW - 1)
        val y1 = (y0 + 1).coerceAtMost(mapH - 1)
        val ax = fx - x0
        val ay = fy - y0
        val a = small[y0 * mapW + x0] and 0xFF
        val b = small[y0 * mapW + x1] and 0xFF
        val c = small[y1 * mapW + x0] and 0xFF
        val d = small[y1 * mapW + x1] and 0xFF
        return (((a * (1f - ax) + b * ax) * (1f - ay) + (c * (1f - ax) + d * ax) * ay).toInt())
    }
    var lo2 = 255
    var hi2 = 0
    val adapted = IntArray(px.size)
    for (i in px.indices) {
        val v = px[i] and 0xFF
        val m = meanAt(i % w, i / w)
        val nv = (((v - m) * 18) / 10 + m).coerceIn(0, 255)
        adapted[i] = nv
        if (nv < lo2) lo2 = nv
        if (nv > hi2) hi2 = nv
    }
    val range2 = (hi2 - lo2).coerceAtLeast(1)
    for (i in px.indices) {
        val v = (((adapted[i] - lo2) * 255) / range2).coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
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



// Two photos of one recipe overlap, so the same line shows up on both. Merge
// the pages into one recipe and drop the repeats, matching on the cleaned
// text rather than the raw OCR so a re-read line still counts as the same.
private fun normLine(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

private fun seenBefore(key: String, seen: List<String>): Boolean =
    seen.any { it == key || (key.length > 12 && (it.contains(key) || key.contains(it))) }

private fun mergeRecipes(pages: List<ParsedRecipe>): ParsedRecipe {
    if (pages.size <= 1) return pages.firstOrNull() ?: ParsedRecipe("(untitled)")
    val base = pages.first()
    val title = pages.maxByOrNull { it.title.length }?.title?.takeIf { it.isNotBlank() } ?: base.title
    val ingredients = mutableListOf<com.trypod33.kitchlore.data.ParsedIngredient>()
    val ingKeys = mutableListOf<String>()
    pages.forEach { page ->
        page.ingredients.forEach { ing ->
            val key = normLine(ing.name.ifBlank { ing.rawText })
            if (key.isNotEmpty() && !seenBefore(key, ingKeys)) {
                ingKeys.add(key)
                ingredients.add(ing)
            }
        }
    }
    val steps = mutableListOf<com.trypod33.kitchlore.data.ParsedStep>()
    val stepKeys = mutableListOf<String>()
    pages.forEach { page ->
        page.steps.forEach { st ->
            val key = normLine(st.instructionText)
            if (key.isNotEmpty() && !seenBefore(key, stepKeys)) {
                stepKeys.add(key)
                steps.add(st)
            }
        }
    }
    return ParsedRecipe(
        title = title,
        baseServings = base.baseServings,
        prepTimeMinutes = base.prepTimeMinutes ?: pages.firstNotNullOfOrNull { it.prepTimeMinutes },
        cookTimeMinutes = base.cookTimeMinutes ?: pages.firstNotNullOfOrNull { it.cookTimeMinutes },
        ingredients = ingredients,
        steps = steps,
    )
}
