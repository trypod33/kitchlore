package com.trypod33.kitchlore.data

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * The optional AI pass over scanned ingredient lines. Nothing ships in the APK:
 * SmolLM2-360M-Instruct (~400 MB, LiteRT format, Apache 2.0) is pulled once, on
 * the first scan that has garbled lines, from BuildConfig.MODEL_URL, and runs
 * fully on-device after that. Ungated: no Hugging Face account or token, the
 * download just runs.
 */
object RecipeLlm {
    @Volatile private var engine: Engine? = null

    @Volatile
    var ready: Boolean = false
        private set

    fun modelFile(context: Context): File = File(context.filesDir, "kitchlore-smollm2-360m.litertlm")

    fun hasModel(context: Context): Boolean =
        modelFile(context).let { it.exists() && it.length() > 100L * 1024 * 1024 }

    // Lines the deterministic parser deliberately leaves ambiguous ("%"/"1%"
    // read as 1/4, 1/3 and 1 1/2 on different cards) or garbled ("h cup", "V2",
    // "Vs", "3/4"); only these go to the model, so clean scans stay fast.
    private val SUSPECT_RE = Regex(
        "(?:^|\\s)(?:%|\\d+\\s*[%/]\\s*\\d?|[hVv]\\d?\\s*(?:cups?|tbsps?|tsps?|tablespoons?|teaspoons?|qts?|oz|lbs?|pounds?)|V\\d|Vs)(?=\\s|$)",
    )

    // A cleaned line must still look like an ingredient: a quantity up front.
    private val OK_LINE_RE = Regex(
        "^\\s*(?:\\d+(?:\\.\\d+)?(?:\\s+\\d+\\s*/\\s*\\d+)?|\\d+\\s*/\\s*\\d+)\\s+\\S",
    )

    fun suspectIndices(recipe: ParsedRecipe): List<Int> =
        recipe.ingredients.indices.filter { i ->
            val raw = recipe.ingredients[i].rawText.ifBlank { recipe.ingredients[i].name }
            SUSPECT_RE.containsMatchIn(raw)
        }

    private const val SYSTEM = (
        "You fix OCR-mangled recipe ingredient lines. You are given one line. " +
        "Reply with only the corrected line, writing fractions in plain digits " +
        "and slashes like 1/4, 1/2, 3/4, 1 1/2. Fraction glyphs often OCR as a " +
        "percent sign: a bare % before a unit is 1/4, 1/3 or 1/2 depending on " +
        "what is typical for that ingredient (1/3 cup flour, 1/4 cup green " +
        "onion); 1% before a unit is usually 1 1/2, occasionally 1 1/3; Vs, V2 " +
        "or h before a unit is 1/2. Fix obvious letter garbles in food words " +
        "only when the intended word is unambiguous from the line (onic -> " +
        "onion). Keep the units and food words as written, keep the meaning, " +
        "and never add, remove or invent anything."
    )

    /**
     * Runs the AI cleanup over the suspect lines. Failures are thrown to the
     * caller, which falls back to the deterministic parse.
     */
    suspend fun enhance(context: Context, recipe: ParsedRecipe, onStatus: (String) -> Unit): ParsedRecipe {
        val idxs = suspectIndices(recipe).toSet()
        if (idxs.isEmpty()) return recipe
        if (!hasModel(context)) {
            ensureModel(context, onStatus)
        }
        if (!ready) {
            onStatus("Starting the on-device AI")
            initEngine(context)
        }
        var announced = false
        val cleaned = recipe.ingredients.mapIndexed { i, ing ->
            if (i !in idxs) {
                ing
            } else {
                val raw = ing.rawText.ifBlank { ing.name }
                if (!announced) {
                    onStatus("AI is reading the garbled lines")
                    announced = true
                }
                val fixed = cleanLine(raw)
                if (fixed == null) ing else ParsedIngredient(name = fixed, rawText = raw, amount = null, unit = null)
            }
        }
        return recipe.copy(ingredients = cleaned)
    }

    private suspend fun ensureModel(context: Context, onStatus: (String) -> Unit): File {
        if (hasModel(context)) return modelFile(context)
        var attempt = 0
        while (true) {
            attempt++
            try {
                return download(
                    context,
                    com.trypod33.kitchlore.BuildConfig.MODEL_URL,
                ) { pct ->
                    onStatus("Downloading the AI model, $pct% (one time, about 400 MB)")
                }
            } catch (t: Throwable) {
                if (attempt >= 3) throw t
                onStatus("Model download hiccup, retrying")
                kotlinx.coroutines.delay(2000L * attempt)
            }
        }
    }

    /**
     * Streams the model into app-private storage with resume + retries; a
     * partial file survives attempts and restarts and only the missing bytes
     * are re-requested. No storage permission needed.
     */
    private suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dest = modelFile(context)
        if (hasModel(context)) return@withContext dest
        val tmp = File(context.filesDir, "kitchlore-smollm2-360m.litertlm.part")
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        var attempt = 0
        while (true) {
            attempt++
            try {
                val offset = if (tmp.exists()) tmp.length() else 0L
                val req = Request.Builder().url(url).apply {
                    header("User-Agent", "Kitchlore/0.3")
                    if (offset > 0) header("Range", "bytes=$offset-")
                }.build()
                client.newCall(req).execute().use { r ->
                    check(r.isSuccessful) { "Download failed: HTTP ${r.code}" }
                    val resumed = r.code == 206 && offset > 0
                    if (!resumed && offset > 0) tmp.delete()
                    val total: Long = if (resumed) {
                        r.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull() ?: -1L
                    } else {
                        r.body?.contentLength() ?: -1L
                    }
                    val body = r.body ?: throw IllegalStateException("Empty download response")
                    var done = if (resumed) offset else 0L
                    if (total > 0) onProgress(((done * 100) / total).toInt())
                    FileOutputStream(tmp, resumed).use { out ->
                        body.byteStream().use { inp ->
                            val buf = ByteArray(256 * 1024)
                            var lastTick = 0
                            while (true) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = ((done * 100) / total).toInt()
                                    if (pct != lastTick) {
                                        lastTick = pct
                                        onProgress(pct)
                                    }
                                }
                            }
                        }
                    }
                }
                check(tmp.length() > 100L * 1024 * 1024) { "Model file came back too small" }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                return@withContext dest
            } catch (t: Throwable) {
                if (attempt >= 4) throw t
                kotlinx.coroutines.delay(2000L * attempt)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        dest
    }

    suspend fun initEngine(context: Context) = withContext(Dispatchers.IO) {
        if (ready) return@withContext
        val f = modelFile(context)
        val configs = listOf(
            EngineConfig(modelPath = f.absolutePath, backend = Backend.GPU()),
            EngineConfig(modelPath = f.absolutePath, backend = Backend.CPU()),
        )
        var last: Exception? = null
        for (c in configs) {
            try {
                engine = Engine(c).also { it.initialize() }
                ready = true
                return@withContext
            } catch (e: Exception) {
                last = e
                engine?.close()
                engine = null
            }
        }
        throw IllegalStateException("Couldn't start the on-device model: ${last?.message}")
    }

    /**
     * One line in, one corrected line out. The 360M model is small, so the
     * output is guarded: anything that does not read as a sane ingredient line
     * is ignored and the deterministic parse stands.
     */
    /**
     * One prompt in, one reply out, on the shared engine. Used by the region
     * labeler (stage 1) as well; the caller guards the reply's shape.
     */
    suspend fun classify(system: String, prompt: String): String? = withContext(Dispatchers.IO) {
        val e = engine ?: return@withContext null
        try {
            val convo = e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(system),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.1),
                ),
            )
            val sb = StringBuilder()
            convo.sendMessageAsync(prompt).collect { sb.append(it.toString()) }
            val out = sb.toString().trim()
            if (out.isBlank() || out.length > 4000) null else out
        } catch (t: Throwable) {
            null
        }
    }

    private suspend fun cleanLine(line: String): String? = withContext(Dispatchers.IO) {
        val e = engine ?: return@withContext null
        try {
            val convo = e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(SYSTEM),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.1),
                ),
            )
            val sb = StringBuilder()
            convo.sendMessageAsync(line).collect { sb.append(it.toString()) }
            val out = sb.toString().trim().trim('"', '`')
            val sane = out.isNotBlank() &&
                out.length <= line.length + 40 &&
                out.split(Regex("\\s+")).size <= 14 &&
                OK_LINE_RE.containsMatchIn(out)
            if (sane) out else null
        } catch (t: Throwable) {
            null
        }
    }
}
