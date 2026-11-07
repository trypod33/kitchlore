package com.trypod33.kitchlore.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

// Edit the photo after a recipe is parsed: swap in a better shot, rotate a
// sideways card, or drop the picture entirely. New edits always write a new
// file so the original scan stays on disk for sync and undo.

private const val MAX_DIM = 1600

@Composable
fun PhotoEditSection(path: String?, onPathChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun editImage(source: () -> Bitmap?, degrees: Float, onError: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                val src = source()
                val newPath = if (src == null) null else saveEdited(context, src, degrees)
                if (newPath != null) onPathChange(newPath) else onError()
            } catch (t: Throwable) {
                onError()
            } finally {
                busy = false
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            editImage({ decodeUriSampled(context, uri) }, 0f) {}
        }
    }

    // Camera capture writes straight into the app's private photos folder,
    // nothing goes to the gallery or MediaStore.
    var pendingCapturePath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = pendingCapturePath
        pendingCapturePath = null
        if (ok && target != null) {
            editImage({ decodeFileSampled(target) }, 0f) {}
        }
    }
    fun startCapture() {
        if (busy) return
        val dir = File(context.filesDir, "photos")
        dir.mkdirs()
        val capture = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        pendingCapturePath = capture.absolutePath
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", capture)
        cameraLauncher.launch(uri)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCapture()
    }

    Column(modifier) {
        RecipeThumbnail(path, Modifier.fillMaxWidth().height(170.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = { pickImage.launch("image/*") }, enabled = !busy) { Text("Replace") }
            TextButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCapture()
                    else permLauncher.launch(Manifest.permission.CAMERA)
                },
                enabled = !busy,
            ) { Text("Camera") }
            TextButton(
                onClick = { editImage({ decodeFileSampled(path) }, -90f) {} },
                enabled = !busy && !path.isNullOrBlank(),
            ) { Text("Rotate left") }
            TextButton(
                onClick = { editImage({ decodeFileSampled(path) }, 90f) {} },
                enabled = !busy && !path.isNullOrBlank(),
            ) { Text("Rotate right") }
            TextButton(onClick = { onPathChange(null) }, enabled = !busy && !path.isNullOrBlank()) { Text("Remove") }
        }
        if (busy) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(20.dp))
                Text("Saving photo...", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun decodeFileSampled(path: String?): Bitmap? {
    if (path.isNullOrBlank()) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIM) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = try {
            when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (t: Throwable) { 0f }
        if (rotation != 0f) bmp.rotate(rotation) else bmp
    } catch (t: Throwable) {
        null
    }
}

private fun decodeUriSampled(context: Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIM) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        val rotation = context.contentResolver.openInputStream(uri)?.use { ins ->
            when (ExifInterface(ins).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (bmp != null && rotation != 0f) bmp.rotate(rotation) else bmp
    } catch (t: Throwable) {
        null
    }
}

private fun Bitmap.rotate(degrees: Float): Bitmap {
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

private fun saveEdited(context: Context, src: Bitmap, degrees: Float): String? {
    return try {
        val dir = File(context.filesDir, "photos")
        dir.mkdirs()
        val bmp = if (degrees != 0f) src.rotate(degrees) else src
        val out = File(dir, "edited-${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (out.length() > 0L) out.absolutePath else null
    } catch (t: Throwable) {
        null
    }
}
