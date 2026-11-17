package com.trypod33.kitchlore.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Reads a recipe page the way Recipe hunt needs: plain HTTP first, and when a
 * site refuses plain callers (several big recipe sites answer 403 to them),
 * the same off-screen WebView the import screen uses, which loads like a real
 * browser. Only the page's HTML comes back; images stay blocked.
 */
object WebPageReader {

    /** True when the string looks like a real page rather than an error stub or a bot-wall shell. */
    private fun looksLikePage(html: String?): Boolean =
        html != null && html.length > 500 && !html.contains("captcha", ignoreCase = true) &&
            html.contains("<", ignoreCase = false)

    suspend fun read(context: Context, url: String, timeoutMs: Long = 20_000L): String? {
        val plain = withContext(Dispatchers.IO) { fetchPage(url) }
        if (looksLikePage(plain)) return plain
        return webFetch(context, url, timeoutMs)
    }

    /**
     * Plain HTTP only, no hidden-browser fallback. The hunt's first naming
     * pass uses this so twelve fast reads never queue behind a slow WebView
     * page; pages that refuse plain callers come back null and are retried
     * with the full read in pass 2.
     */
    suspend fun readPlain(url: String): String? =
        withContext(Dispatchers.IO) { fetchPage(url) }.takeIf { looksLikePage(it) }

    private fun findActivity(c: Context): Activity? {
        var ctx: Context = c
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun webFetch(context: Context, url: String, timeoutMs: Long): String? =
        withContext(Dispatchers.Main) {
            val activity = findActivity(context) ?: return@withContext null
            suspendCancellableCoroutine { cont ->
                val main = Handler(Looper.getMainLooper())
                var finished = false
                val wv = WebView(activity)

                fun finish(html: String?) {
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
                    if (cont.isActive) cont.resume(html)
                }

                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.userAgentString = BROWSER_UA
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
                            view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                val html = try {
                                    val payload = JSONTokener(raw.trim()).nextValue()
                                    if (payload is String) payload else null
                                } catch (_: Throwable) {
                                    null
                                }
                                if (looksLikePage(html)) {
                                    finish(html)
                                } else if (attempts < 2) {
                                    attempts++
                                    main.postDelayed({ scrape() }, 1_200L)
                                } else {
                                    finish(null)
                                }
                            }
                        }
                        main.postDelayed({ scrape() }, 600L)
                    }
                }

                try {
                    // One invisible pixel on the window, same passenger the
                    // hidden search engine rides on.
                    val decor = activity.window.decorView as? ViewGroup
                    decor?.addView(wv, ViewGroup.LayoutParams(1, 1))
                } catch (_: Throwable) {
                    finish(null)
                    return@suspendCancellableCoroutine
                }
                try {
                    CookieManager.getInstance().setAcceptCookie(true)
                } catch (_: Throwable) {
                }
                wv.loadUrl(url)
                main.postDelayed({ finish(null) }, timeoutMs)
                cont.invokeOnCancellation { finish(null) }
            }
        }
}
