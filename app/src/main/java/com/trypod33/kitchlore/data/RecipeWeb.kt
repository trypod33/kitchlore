package com.trypod33.kitchlore.data

import android.content.Context
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Ingredient
import com.trypod33.kitchlore.data.local.entity.Recipe
import com.trypod33.kitchlore.data.local.entity.Step
import com.trypod33.kitchlore.ui.quantityTokenValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.net.URLDecoder

const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

/** Fetches a page's HTML with a browser user agent. Returns null on any failure. */
fun fetchPage(url: String): String? = try {
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        null
    } else {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 12000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", BROWSER_UA)
        if (conn.responseCode !in 200..299) {
            null
        } else {
            conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        }
    }
} catch (t: Throwable) {
    null
}

internal fun stripTags(s: String): String = s
    .replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&#x27;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace(Regex("\\s+"), " ")
    .trim()

/** What we could pull off a recipe page, from JSON-LD first, microdata second, generic selectors last. */
data class ParsedWebRecipe(
    val title: String? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val yieldServings: Int? = null,
    val totalMinutes: Int? = null,
    val imageUrl: String? = null,
)

object HtmlRecipeParser {
    fun parse(html: String): ParsedWebRecipe? {
        val fromJson = parseJsonLd(html)
        if (fromJson != null && (fromJson.ingredients.isNotEmpty() || fromJson.steps.isNotEmpty())) return fromJson
        val fromMicro = parseMicrodata(html)
        if (fromMicro != null && (fromMicro.ingredients.isNotEmpty() || fromMicro.steps.isNotEmpty())) return fromMicro
        return parseGenericSelectors(html)
    }

    private fun parseJsonLd(html: String): ParsedWebRecipe? {
        val re = Regex(
            "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        var best: ParsedWebRecipe? = null
        for (m in re.findAll(html)) {
            try {
                val text = m.groupValues[1].trim()
                val root: Any = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
                for (obj in recipeNodes(root)) {
                    val parsed = fromRecipeObject(obj)
                    if (parsed != null && (best == null ||
                            parsed.ingredients.size + parsed.steps.size >
                            best!!.ingredients.size + best!!.steps.size)
                    ) {
                        best = parsed
                    }
                }
            } catch (t: Throwable) {
                // a malformed block just loses this round
            }
        }
        return best
    }

    private fun recipeNodes(node: Any?): Sequence<JSONObject> = sequence {
        when (node) {
            is JSONObject -> {
                val type = node.opt("@type")
                val isRecipe = (type is String && type.equals("Recipe", true)) ||
                    (type is JSONArray && (0 until type.length()).any { type.optString(it).equals("Recipe", true) })
                if (isRecipe) yield(node)
                for (key in listOf("@graph", "mainEntity", "itemListElement", "recipeInstructions")) {
                    node.opt(key)?.let { yieldAll(recipeNodes(it)) }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) yieldAll(recipeNodes(node.opt(i)))
            }
        }
    }

    private fun fromRecipeObject(o: JSONObject): ParsedWebRecipe? {
        val title = o.optString("name").ifBlank { null }
        val ings = mutableListOf<String>()
        o.optJSONArray("recipeIngredient")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { ings.add(it.trim()) }
        } ?: o.optString("recipeIngredients").ifBlank { null }?.let { ings.add(it) }

        val steps = mutableListOf<String>()
        when (val ins = o.opt("recipeInstructions")) {
            is String -> steps.add(ins)
            is JSONArray -> collectInstructions(ins, steps)
            else -> {}
        }
        if (steps.isEmpty()) {
            o.opt("recipeInstructions")?.let { recipeNodes(it).forEach { s -> s.optString("text").ifBlank { null }?.let { steps.add(it) } } }
        }

        val servings = o.optString("recipeYield").takeIf { it.isNotBlank() }?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
        val minutes = isoDurationToMinutes(o.optString("totalTime"))
        val image = extractImage(o.opt("image"))
        if (title == null && ings.isEmpty() && steps.isEmpty()) return null
        return ParsedWebRecipe(title, ings, steps, servings, minutes, image)
    }

    private fun collectInstructions(arr: JSONArray, out: MutableList<String>) {
        for (i in 0 until arr.length()) {
            when (val el = arr.opt(i)) {
                is String -> out.add(el)
                is JSONObject -> {
                    el.optString("text").ifBlank { null }?.let { out.add(it) }
                    el.optJSONArray("itemListElement")?.let { collectInstructions(it, out) }
                }
            }
        }
    }

    private fun isoDurationToMinutes(s: String): Int? {
        if (s.isBlank()) return null
        val m = Regex("P(?:\\d+D)?T?(?:(\\d+)H)?(?:(\\d+)M)?", RegexOption.IGNORE_CASE).find(s) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: 0
        val min = m.groupValues[2].toIntOrNull() ?: 0
        val total = h * 60 + min
        return if (total > 0) total else null
    }

    private fun extractImage(node: Any?): String? = when (node) {
        is String -> node.ifBlank { null }
        is JSONObject -> node.optString("url").ifBlank { null }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { extractImage(node.opt(it)) }
        else -> null
    }

    private fun parseMicrodata(html: String): ParsedWebRecipe? {
        val title = Regex("itemprop=[\"']name[\"'][^>]*>([^<]{2,150})<", RegexOption.IGNORE_CASE)
            .findAll(html).firstOrNull()?.groupValues?.get(1)?.let { stripTags(it) }
        val ings = buildList {
            Regex("itemprop=[\"']recipeIngredient[\"'][^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { add(stripTags(it.groupValues[1])) }
            Regex("itemprop=[\"']recipeIngredient[\"'][^>]*>([^<]+)<", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { add(stripTags(it.groupValues[1])) }
        }.filter { it.isNotBlank() }.distinct()
        val steps = buildList {
            Regex("itemprop=[\"']recipeInstructions[\"'][^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { add(stripTags(it.groupValues[1])) }
            Regex("itemprop=[\"']recipeInstructions[\"'][^>]*>(.*?)</[a-z]+>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(html).forEach { add(stripTags(it.groupValues[1])) }
        }.filter { it.isNotBlank() && it.length > 12 }.distinct()
        if (title == null && ings.isEmpty() && steps.isEmpty()) return null
        return ParsedWebRecipe(title, ings, steps, null, null, null)
    }

    private fun parseGenericSelectors(html: String): ParsedWebRecipe? {
        val title = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
            .findAll(html).firstOrNull()?.groupValues?.get(1)?.let { stripTags(it) }?.takeIf { it.length in 3..120 }
        val ings = Regex(
            "(?is)<li[^>]*class=[\"'][^\"']*(?:ingredient|recipe-ingredient)[^\"']*[\"'][^>]*>(.*?)</li>"
        ).findAll(html).map { stripTags(it.groupValues[1]) }.filter { it.isNotBlank() && it.length in 3..200 }.distinct().toList()
        val steps = Regex(
            "(?is)<li[^>]*class=[\"'][^\"']*(?:instruction|direction|step|method)[^\"']*[\"'][^>]*>(.*?)</li>"
        ).findAll(html).map { stripTags(it.groupValues[1]) }.filter { it.isNotBlank() && it.length > 12 }.distinct().toList()
        if (title == null && ings.isEmpty() && steps.isEmpty()) return null
        return ParsedWebRecipe(title, ings, steps, null, null, null)
    }
}

/**
 * Keyless web search, best effort: a public SearXNG instance first, DuckDuckGo
 * Lite second. Both can refuse or CAPTCHA an automated caller, so empty means
 * empty and the UI says so honestly.
 */
object KeylessSearch {
    data class Result(val title: String, val url: String, val engine: String)

    // A wide list on purpose: these instances rate-limit or refuse an automated
    // caller at any hour, so the chain walks as many as it needs and accepts
    // either the JSON feed or the HTML page they fall back to serving.
    private val SEARX_INSTANCES = listOf(
        "https://searx.be", "https://priv.au", "https://paulgo.io",
        "https://search.inetol.net", "https://baresearch.org", "https://searxng.site",
        "https://search.mdosch.de", "https://opnxng.com", "https://search.bus-hit.me",
    )

    fun search(query: String): List<Result> {
        val fromSearx = searx(query)
        if (fromSearx.isNotEmpty()) return fromSearx
        val fromLite = ddgLite(query)
        if (fromLite.isNotEmpty()) return fromLite
        return ddgHtml(query)
    }

    private fun searx(query: String): List<Result> {
        for (base in SEARX_INSTANCES) {
            try {
                val body = fetchBody(
                    "$base/search?format=json&q=" + URLEncoder.encode(query, "UTF-8")
                ) ?: continue
                val fromJson = parseSearxJson(body)
                if (fromJson.isNotEmpty()) return fromJson
                // Some instances ignore the format param and answer with the HTML
                // page, which still carries the results. Parse it rather than skip.
                val fromHtml = parseSearxHtml(body)
                if (fromHtml.isNotEmpty()) return fromHtml
            } catch (t: Throwable) {
                // try the next instance
            }
        }
        return emptyList()
    }

    private fun parseSearxJson(body: String): List<Result> {
        return try {
            val json = JSONObject(body)
            val arr = json.optJSONArray("results") ?: return emptyList()
            val out = mutableListOf<Result>()
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val u = r.optString("url")
                val t = r.optString("title")
                if (u.startsWith("http") && t.isNotBlank()) out.add(Result(stripTags(t), u, "SearXNG"))
                if (out.size >= 10) break
            }
            out
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun parseSearxHtml(body: String): List<Result> {
        // Real result anchors first: searx renders each hit as <h3><a href>.
        val strict = Regex(
            "<h3[^>]*>\\s*<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val strictHits = collectHtmlResults(body, strict)
        if (strictHits != null) return strictHits
        val broad = Regex(
            "<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        return collectHtmlResults(body, broad) ?: emptyList()
    }

    private fun collectHtmlResults(body: String, re: Regex): List<Result>? {
        val out = mutableListOf<Result>()
        for (m in re.findAll(body)) {
            val url = m.groupValues[1]
            val title = stripTags(m.groupValues[2])
            if (title.length < 12) continue // nav labels, not results
            if (url.contains("/search?") || url.startsWith("#")) continue
            if (SEARX_INSTANCES.any { url.contains(it.removePrefix("https://")) }) continue
            out.add(Result(title, url, "SearXNG"))
            if (out.size >= 10) break
        }
        return if (out.isEmpty()) null else out
    }

    /** Last resort: DuckDuckGo's full HTML endpoint, same idea as Lite but a different door. */
    private fun ddgHtml(query: String): List<Result> {
        val body = fetchBody("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")) ?: return emptyList()
        val re = Regex(
            "<a[^>]+class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val out = mutableListOf<Result>()
        for (m in re.findAll(body)) {
            var url = m.groupValues[1]
            val title = stripTags(m.groupValues[2])
            if (title.isBlank()) continue
            // DDG wraps hits in its own redirector; pull the real target out.
            if (url.contains("uddg=")) {
                val target = Regex("uddg=([^&]+)").find(url)?.groupValues?.get(1)
                if (target != null) url = try { URLDecoder.decode(target, "UTF-8") } catch (t: Throwable) { url }
            }
            if (!url.startsWith("http") || url.contains("duckduckgo.com")) continue
            out.add(Result(title, url, "DuckDuckGo"))
            if (out.size >= 10) break
        }
        return out
    }

    private fun ddgLite(query: String): List<Result> {
        val body = fetchBody("https://lite.duckduckgo.com/lite/?q=" + URLEncoder.encode(query, "UTF-8")) ?: return emptyList()
        val re = Regex("<a[^>]*href=[\"'](http[^\"']+)[\"'][^>]*class=[\"']result-link[\"'][^>]*>(.*?)</a>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val out = mutableListOf<Result>()
        for (m in re.findAll(body)) {
            val url = m.groupValues[1]
            val title = stripTags(m.groupValues[2])
            if (title.isBlank()) continue
            if (url.contains("duckduckgo.com") || url.contains("/y.js")) continue
            out.add(Result(title, url, "DuckDuckGo"))
            if (out.size >= 10) break
        }
        return out
    }

    private fun fetchBody(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", BROWSER_UA)
        if (conn.responseCode !in 200..299) null else conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    } catch (t: Throwable) {
        null
    }
}

/** Files a parsed web recipe into the library, photo downloaded when we have one. Returns the new id or -1. */
suspend fun insertWebRecipe(db: AppDatabase, context: Context, parsed: ParsedWebRecipe, sourceUrl: String): Long =
    withContext(Dispatchers.IO) {
        try {
            var photoPath: String? = null
            parsed.imageUrl?.let { img ->
                try {
                    val bytes = URL(img).openConnection().apply {
                        connectTimeout = 10000
                        readTimeout = 10000
                        setRequestProperty("User-Agent", BROWSER_UA)
                    }.getInputStream().use { it.readBytes() }
                    val dir = File(context.filesDir, "photos")
                    dir.mkdirs()
                    val f = File(dir, "import-${System.currentTimeMillis()}.jpg")
                    f.writeBytes(bytes)
                    photoPath = f.absolutePath
                } catch (t: Throwable) {
                    photoPath = null
                }
            }
            val host = try {
                URI(sourceUrl).host?.removePrefix("www.") ?: "web"
            } catch (t: Throwable) {
                "web"
            }
            val recipe = Recipe(
                title = parsed.title?.ifBlank { null } ?: host,
                baseServings = parsed.yieldServings ?: 4,
                sourceType = "web",
                sourceNote = host,
                originalImagePath = photoPath,
                // Auto-tag from title + ingredients; the source stays in
                // sourceType/sourceNote, where it belongs.
                tagsCsv = RecipeTagger.autoTags(parsed.title?.takeIf { it.isNotBlank() } ?: host, parsed.ingredients)
                    .joinToString(", "),
            )
            val rid = db.recipeDao().insert(recipe)
            val ings = parsed.ingredients.mapIndexed { i, line ->
                val (name, amount, unit) = splitIngredientLine(line)
                Ingredient(recipeId = rid, name = name, rawText = line, amount = amount, unit = unit, sortOrder = i)
            }
            if (ings.isNotEmpty()) db.ingredientDao().insertAll(ings)
            val steps = parsed.steps.mapIndexed { idx, text -> Step(recipeId = rid, stepNumber = idx + 1, instructionText = text) }
            if (steps.isNotEmpty()) db.stepDao().insertAll(steps)
            rid
        } catch (t: Throwable) {
            -1L
        }
    }

/** Splits "1 1/2 cups flour" into name, amount and unit using the shared quantity tokenizer. */
internal fun splitIngredientLine(line: String): Triple<String, Double?, String?> {
    val tokens = line.trim().split(Regex("\\s+"))
    var amount: Double? = null
    var consumed = 0
    for (t in tokens) {
        val v = quantityTokenValue(t) ?: break
        amount = (amount ?: 0.0) + v
        consumed++
    }
    var unit: String? = null
    if (consumed < tokens.size && tokens[consumed].length in 2..8 && tokens[consumed].all { it.isLetter() }) {
        unit = tokens[consumed]
        consumed++
    }
    val name = tokens.drop(consumed).joinToString(" ").ifBlank { line.trim() }
    return Triple(name, amount, unit)
}
