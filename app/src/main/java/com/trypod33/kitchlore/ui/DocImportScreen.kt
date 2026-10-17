package com.trypod33.kitchlore.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trypod33.kitchlore.data.DocTextExtractor
import com.trypod33.kitchlore.data.OcrParser
import com.trypod33.kitchlore.data.ParsedRecipe
import com.trypod33.kitchlore.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

// Document import (PDF / DOCX / TXT), mirroring the Termux /api/import-doc
// route: pick a file, pull out the text, parse, review, save. Scanned PDFs
// with no text layer fall back to rendering each page and OCRing it.

@Composable
fun DocImportRoute(
    db: AppDatabase,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<ParsedRecipe?>(null) }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            error = null
            scope.launch {
                try {
                    val text = with2(context, uri) { s -> status = s }
                    val rules = withContext(Dispatchers.IO) {
                        db.correctionRuleDao().getAllOnce().associate { it.garbled to it.replacement }
                    }
                    parsed = OcrParser.parse(text, emptyList(), rules)
                } catch (t: Throwable) {
                    error = "Couldn't read that document"
                }
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        openDoc.launch(
            arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword",
                "text/plain",
                "text/markdown",
            )
        )
    }

    val p = parsed
    if (p != null) {
        ReviewImportScreen(
            db = db,
            parsed = p,
            photoPath = null,
            sourceNote = "Imported from document",
            onSaved = onDone,
            onCancel = onBack,
        )
        return
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onBack) { Text("< Back") }
        if (busy) {
            CircularProgressIndicator()
            if (status.isNotBlank()) {
                Text(status, modifier = androidx.compose.ui.Modifier.padding(top = 12.dp))
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

private suspend fun with2(context: Context, uri: Uri, onStatus: (String) -> Unit): String =
    kotlinx.coroutines.withContext(Dispatchers.IO) {
        extractFromUri(context, uri, onStatus)
    }

private fun extractFromUri(context: Context, uri: Uri, onStatus: (String) -> Unit): String {
    val name = queryName(context, uri) ?: "document"
    val safeName = name.takeLast(80)
    val tmp = File(context.cacheDir, "import-$safeName")
    context.contentResolver.openInputStream(uri)?.use { ins ->
        tmp.outputStream().use { ins.copyTo(it) }
    } ?: return ""
    val ext = tmp.extension.lowercase()
    if (ext == "pdf" || name.endsWith(".pdf", ignoreCase = true)) {
        val text = DocTextExtractor.extractPdfText(tmp)
        if (text.length < 40) {
            onStatus("Scanning the PDF pages")
            val ocrText = ocrPdfPages(tmp)
            return ocrText.ifBlank { text }
        }
        return text
    }
    return DocTextExtractor.extract(tmp)
}

private fun ocrPdfPages(file: File): String {
    val sb = StringBuilder()
    try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                for (i in 0 until minOf(renderer.pageCount, 6)) {
                    renderer.openPage(i).use { page ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888,
                        )
                        bmp.eraseColor(Color.WHITE)
                        val m = Matrix()
                        m.setScale(scale.toFloat(), scale.toFloat())
                        page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val visionText = Tasks.await(
                            recognizer.process(InputImage.fromBitmap(bmp, 0)),
                        )
                        sb.append(visionText.text).append('\n')
                        bmp.recycle()
                    }
                }
            }
        }
    } catch (t: Throwable) {
        // Fall back to whatever text was extracted directly.
    }
    return sb.toString().trim()
}

private fun queryName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
