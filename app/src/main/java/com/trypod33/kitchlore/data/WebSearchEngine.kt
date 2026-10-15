package com.trypod33.kitchlore.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * The hidden search engine: an off-screen WebView, a real browser, loads the
 * search page exactly the way a person's browser would. The page's own
 * JavaScript hands back the result links and titles, and the app renders only
 * Kitchlore cards. Nothing that looks like a browser is ever on screen: no
 * address bar, no engine page, no visible chrome of any kind.
 */
object WebSearchEngine {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"

    /** How long one engine gets to answer before the next one gets its turn. */
    private const val ENGINE_TIMEOUT_MS = 10_000L

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private class Engine(val name: String, val pageUrl: String, val js: String)

    private fun engines(query: String): List<Engine> = listOf(
        Engine(
            "bing",
            "https://www.bing.com/search?q=" + enc(query),
            "(function(){var o=[];document.querySelectorAll('li.b_algo').forEach(function(li){" +
                "var a=li.querySelector('h2 a')||li.querySelector('a');" +
                "if(a&&a.href&&a.innerText&&a.innerText.trim())o.push({t:a.innerText.trim(),u:a.href});" +
                "});return JSON.stringify(o)})()",
        ),
        Engine(
            "ddghtml",
            "https://html.duckduckgo.com/html/?q=" + enc(query),
            "(function(){var o=[];document.querySelectorAll('a.result__a').forEach(function(a){" +
                "if(a.href&&a.innerText&&a.innerText.trim())o.push({t:a.innerText.trim(),u:a.href});" +
                "});return JSON.stringify(o)})()",
        ),
        Engine(
            "ddglite",
            "https://lite.duckduckgo.com/lite/?q=" + enc(query),
            "(function(){var o=[];document.querySelectorAll('a.result-link').forEach(function(a){" +
                "if(a.href&&a.innerText&&a.innerText.trim())o.push({t:a.innerText.trim(),u:a.href});" +
                "});return JSON.stringify(o)})()",
        ),
        Engine(
            "mojeek",
            "https://www.mojeek.com/search?q=" + enc(query),
            "(function(){var o=[];document.querySelectorAll('a.title, a.ob, ul.results-standard li a').forEach(function(a){" +
                "if(a.href&&a.innerText&&a.innerText.trim())o.push({t:a.innerText.trim(),u:a.href});" +
                "});return JSON.stringify(o)})()",
        ),
    )

    /** Runs the hidden-browser search, engine by engine, first hit list wins. */
    suspend fun search(context: Context, query: String, timeoutMs: Long = ENGINE_TIMEOUT_MS): List<KeylessSearch.Result> =
        withContext(Dispatchers.Main) {
            val activity = findActivity(context)
            if (activity == null || query.isBlank()) return@withContext emptyList()
            for (engine in engines(query)) {
                val hits = runEngine(activity, engine, timeoutMs)
                if (hits.isNotEmpty()) return@withContext hits
            }
            emptyList()
        }

    private fun findActivity(c: Context): Activity? {
        var ctx: Context = c
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private suspend fun runEngine(activity: Activity, engine: Engine, timeoutMs: Long): List<KeylessSearch.Result> =
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            var finished = false
            val wv = WebView(activity)

            fun finish(hits: List<KeylessSearch.Result>) {
                if (finished) return
                finished = true
                main.post {
                    try {
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.stopLoading()
                        wv.destroy()
                    } catch (_: Throwable) {
                    }
                }
                if (cont.isActive) cont.resume(hits)
            }

            @SuppressLint("SetJavaScriptEnabled")
            fun setUp() {
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.userAgentString = UA
                wv.settings.blockNetworkImage = true
                wv.settings.loadsImagesAutomatically = false
                wv.visibility = View.INVISIBLE
                wv.isFocusable = false
                wv.setBackgroundColor(0x00000000)
                wv.webViewClient = object : WebViewClient() {
                    private var attempts = 0
                    override fun onPageFinished(view: WebView, url: String) {
                        if (finished || url.startsWith("about:")) return
                        fun scrape() {
                            if (finished) return
                            view.evaluateJavascript(engine.js) { raw ->
                                val hits = parseResults(raw)
                                if (hits.isNotEmpty()) {
                                    finish(hits)
                                } else if (attempts < 2) {
                                    attempts++
                                    main.postDelayed({ scrape() }, 1_200L)
                                }
                            }
                        }
                        main.postDelayed({ scrape() }, 500L)
                    }
                }
            }

            setUp()
            try {
                // A one-pixel invisible passenger on the window: the WebView
                // needs a real view tree to load in, and this never shows.
                val decor = activity.window.decorView as? ViewGroup
                decor?.addView(wv, ViewGroup.LayoutParams(1, 1))
            } catch (_: Throwable) {
                finish(emptyList())
                return@suspendCancellableCoroutine
            }
            try {
                CookieManager.getInstance().setAcceptCookie(true)
            } catch (_: Throwable) {
            }
            wv.loadUrl(engine.pageUrl)
            main.postDelayed({ finish(emptyList()) }, timeoutMs)
            cont.invokeOnCancellation { finish(emptyList()) }
        }

    /**
     * evaluateJavascript answers with a JSON-encoded copy of the script's
     * return value, so unwrap one layer before parsing the hit array.
     */
    private fun parseResults(raw: String?): List<KeylessSearch.Result> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val payload = JSONTokener(raw.trim()).nextValue().toString()
            val arr = JSONArray(payload)
            val seen = LinkedHashSet<String>()
            val out = mutableListOf<KeylessSearch.Result>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("t").trim()
                val url = cleanUrl(o.optString("u"))
                if (url.isEmpty() || title.length < 8) continue
                if (!seen.add(url)) continue
                out.add(KeylessSearch.Result(title = title, url = url, engine = "web"))
                if (out.size >= 15) break
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** Follows the engines' redirect wrappers (DuckDuckGo's uddg=) back to the real recipe page. */
    private fun cleanUrl(input: String): String {
        var url = input.trim()
        if (url.startsWith("//")) url = "https:$url"
        val marker = "uddg="
        val idx = url.indexOf(marker)
        if (idx >= 0) {
            val tail = url.substring(idx + marker.length)
            val end = tail.indexOf('&')
            url = Uri.decode(if (end >= 0) tail.substring(0, end) else tail)
        }
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return ""
        val host = try {
            java.net.URI(url).host?.lowercase() ?: ""
        } catch (_: Throwable) {
            ""
        }
        val blocked = listOf("duckduckgo.com", "bing.com", "microsoft.com", "mojeek.com", "google.com", "yahoo.com")
        if (blocked.any { host == it || host.endsWith(".$it") }) return ""
        return url
    }
}
