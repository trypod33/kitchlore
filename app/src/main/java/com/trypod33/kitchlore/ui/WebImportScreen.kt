package com.trypod33.kitchlore.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.trypod33.kitchlore.data.HtmlRecipeParser
import com.trypod33.kitchlore.data.fetchPage
import com.trypod33.kitchlore.data.insertWebRecipe
import com.trypod33.kitchlore.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener

/**
 * Import-first: paste (or share in) a recipe link, the page is read on the
 * device, the recipe is pulled from schema.org JSON-LD, then microdata, then
 * generic selectors, then saved into the library ready to edit. Pages that
 * block simple fetches load in the in-app WebView and import from there.
 */
@Composable
fun WebImportRoute(db: AppDatabase, initialUrl: String, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initialUrl) }
    var status by remember { mutableStateOf<String?>(null) }
    var showWebView by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    fun openInBrowser(target: String) {
        val u = target.trim()
        if (u.startsWith("http")) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
            } catch (t: Throwable) {
                status = "No browser app found."
            }
        }
    }

    fun doImport(target: String, html: String?) {
        val u = target.trim()
        if (importing) return
        if (!u.startsWith("http")) {
            status = "Paste a full link, it starts with https"
            return
        }
        importing = true
        status = "Reading the page..."
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val htmlText = html ?: fetchPage(u)
                htmlText?.let { HtmlRecipeParser.parse(it) }
            }
            if (parsed == null) {
                status = "Couldn't reach that page. Try the WebView import, or open it in the browser and share it back."
                showWebView = true
                importing = false
            } else if (parsed.ingredients.isEmpty() && parsed.steps.isEmpty()) {
                status = "No recipe found on that page. The WebView import can read pages that hide their recipe from simple fetches."
                showWebView = true
                importing = false
            } else {
                val id = insertWebRecipe(db, context, parsed, u)
                if (id > 0) {
                    onOpen(id)
                } else {
                    status = "Save failed, try again."
                    importing = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Import from web", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Paste a recipe link") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = { doImport(url, null) },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text(if (importing) "Importing..." else "Import", maxLines = 1, softWrap = false) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showWebView = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("WebView", maxLines = 1, softWrap = false) }
            OutlinedButton(
                onClick = { openInBrowser(url) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Browser", maxLines = 1, softWrap = false) }
        }
        status?.let { Text(it, fontSize = 15.sp) }
        if (showWebView) {
            WebViewImportBlock(
                url = url.trim(),
                modifier = Modifier.fillMaxSize(),
                onImportHtml = { html -> doImport(url, html) },
                onOpenInBrowser = { openInBrowser(url) },
            )
        }
    }
}

@Composable
private fun WebViewImportBlock(
    url: String,
    modifier: Modifier = Modifier,
    onImportHtml: (String) -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun doUpdateVisitedHistory(view: WebView, u: String, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, u, isReload)
                    // keep the field honest when the user navigates inside the page
                }
            }
        }
    }
    var loaded by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { raw ->
                        try {
                            val html = JSONTokener(raw).nextValue().toString()
                            loaded = true
                            onImportHtml(html)
                        } catch (t: Throwable) {
                            loaded = false
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (loaded) "Import again" else "Import this page", maxLines = 1, softWrap = false) }
        }
        Text(
            "Wait for the page to finish loading, then tap Import. Some sites need a moment.",
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        if (url.isNotBlank()) {
            AndroidView(
                factory = {
                    webView.apply {
                        loadUrl(url)
                    }
                },
                update = {},
                modifier = Modifier.weight(1f),
            )
        }
    }
}
