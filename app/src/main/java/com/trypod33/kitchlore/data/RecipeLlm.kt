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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
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

    // After a wedged engine call, stop trusting the GPU backend and start the
    // engine on CPU next time.
    @Volatile private var preferCpu = false

    // Deadlines for every AI step. Native engine calls can wedge without ever
    // returning or throwing, and a coroutine timeout cannot preempt a blocked
    // native call, so each step runs on its own thread under a hard deadline.
    // The result is the same either way: the review always opens, with the
    // AI-cleaned lines when the model cooperates and the raw read otherwise.
    private const val ENGINE_INIT_TIMEOUT_MS = 90_000L
    private const val LINE_TIMEOUT_MS = 45_000L
    private const val CLASSIFY_TIMEOUT_MS = 60_000L
    private const val MODEL_WAIT_MS = 180_000L

    /**
     * Runs block on a fresh watchdog thread with a hard deadline; null when it
     * does not finish in time. A wedged native call keeps its thread, so every
     * guarded call gets its own.
     */
    internal fun <T> guarded(timeoutMs: Long, block: () -> T): T? {
        val pool = Executors.newSingleThreadExecutor { r ->
            Thread(r, "kitchlore-ai-watchdog").apply { isDaemon = true }
        }
        return try {
            pool.submit(block).get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            null
        } finally {
            pool.shutdown()
        }
    }

    private class Box(val value: String?)

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
     * Runs the AI cleanup over the suspect lines. Every step is bounded, so
     * the caller always gets a recipe back: the AI-cleaned one when the model
     * cooperates, the deterministic parse otherwise.
     */
    suspend fun enhance(context: Context, recipe: ParsedRecipe, onStatus: (String) -> Unit): ParsedRecipe {
        val idxs = suspectIndices(recipe).toSet()
        if (idxs.isEmpty()) return recipe
        if (!hasModel(context)) {
            onStatus("One-time AI model download starting (about 400 MB)")
            val got = guarded(MODEL_WAIT_MS) { runBlocking { ensureModel(context, onStatus) } }
            if (got == null || !hasModel(context)) {
                onStatus("AI cleanup skipped: the model is not downloaded yet")
                return recipe
            }
        }
        if (!ready) {
            onStatus("Starting the on-device AI")
            if (!ensureEngine(context)) {
                onStatus("AI cleanup skipped: the model would not start")
                return recipe
            }
        }
        var announced = false
        val cleaned = recipe.ingredients.mapIndexed { i, ing ->
            if (i !in idxs) {
                ing
            } else {
                if (!ready) return recipe // the engine wedged and was torn down mid-pass
                val raw = ing.rawText.ifBlank { ing.name }
                if (!announced) {
                    onStatus("AI is reading the garbled lines")
                    announced = true
                }
                val fixed = cleanLine(raw)
                if (fixed == null) {
                    if (!ready) return recipe // wedged mid-pass: keep the raw read
                    ing
                } else ParsedIngredient(name = fixed, rawText = raw, amount = null, unit = null)
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

    private fun teardownAfterWedge() {
        preferCpu = true
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        engine = null
        ready = false
    }

    // Engine start: GPU first (faster inference), CPU fallback, and each start
    // on its own watchdog thread so a hung backend cannot freeze the screen.
    private fun initEngineBlocking(context: Context): Boolean {
        if (ready) return true
        val f = modelFile(context)
        val gpu = EngineConfig(modelPath = f.absolutePath, backend = Backend.GPU())
        val cpu = EngineConfig(modelPath = f.absolutePath, backend = Backend.CPU())
        val configs = if (preferCpu) listOf(cpu, gpu) else listOf(gpu, cpu)
        for (c in configs) {
            val started = guarded(ENGINE_INIT_TIMEOUT_MS) { Engine(c).also { it.initialize() } }
            if (started != null) {
                engine = started
                ready = true
                return true
            }
        }
        return false
    }

    /** Starts the engine if it is not running; false when it will not start. */
    suspend fun ensureEngine(context: Context): Boolean = withContext(Dispatchers.IO) {
        initEngineBlocking(context)
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
        val box = guarded(CLASSIFY_TIMEOUT_MS) { Box(runBlocking { rawClassify(e, system, prompt) }) }
        if (box == null) {
            teardownAfterWedge() // a reply that never came means the engine is not to be trusted
            return@withContext null
        }
        box.value
    }

    private suspend fun rawClassify(e: Engine, system: String, prompt: String): String? = try {
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

    private suspend fun cleanLine(line: String): String? = withContext(Dispatchers.IO) {
        val e = engine ?: return@withContext null
        val box = guarded(LINE_TIMEOUT_MS) { Box(runBlocking { rawClean(e, line) }) }
        if (box == null) {
            teardownAfterWedge() // a reply that never came means the engine is not to be trusted
            return@withContext null
        }
        box.value
    }

    private suspend fun rawClean(e: Engine, line: String): String? = try {
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
