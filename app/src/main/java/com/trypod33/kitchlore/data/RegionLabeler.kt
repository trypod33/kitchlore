package com.trypod33.kitchlore.data

import android.content.Context

/** One ML Kit text block with its page position. This is the unit stage 1 labels. */
data class RegionBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Where a block belongs on a recipe page. The labeler only ever picks one of these. */
enum class RegionKind { TITLE, INGREDIENTS, STEPS, OTHER }

/**
 * Stage 1 of recipe understanding, as a hybrid. Deterministic cues label
 * every block they can read with confidence (amounts, step markers,
 * headers, nutrition footers); the on-device model arbitrates only the
 * blocks the cues leave undecided; a position heuristic covers a model
 * hiccup. The labeler never writes ingredient text, so it cannot invent a
 * quantity; the deterministic rules still do all parsing inside a labeled
 * region. Measured Oct 17 2026 against the six-layout test set: the 360M
 * alone failed all six layouts in every prompt shape tried (it copies the
 * example's answer pattern instead of reading block text), while the cue
 * pass read 48 of 49 blocks correctly, so cues lead and the model only
 * breaks ties.
 */
object RegionLabeler {

    /** A cue-pass verdict. A null kind means the cues are split and the block needs the model. */
    data class CueGuess(val kind: RegionKind?)

    private const val SYSTEM = (
        "You label the parts of a scanned recipe page. Each part is called a " +
            "block. Label each block exactly one of: title, ingredients, steps, other. " +
            "title = the recipe name. ingredients = amounts with food (cups, teaspoons, " +
            "onions). steps = instructions telling what to do (mix, bake, stir, preheat). " +
            "other = everything else (page decoration, timing notes, nutrition facts, " +
            "tips, captions, headers like Directions). Reply with one line per block, " +
            "in order, exactly like B1: title. Write nothing else.\n\n" +
            "Example:\n" +
            "B1 (top 4%, left 8%, width 60%): GERMAN CHOCOLATE CAKE\n" +
            "B2 (top 40%, left 8%, width 84%): 1 1/2 cups all-purpose flour; 3/4 teaspoon baking soda\n" +
            "B3 (top 55%, left 8%, width 84%): 1. Grease and flour two pans. Mix flour, soda and salt\n" +
            "Reply:\n" +
            "B1: title\n" +
            "B2: ingredients\n" +
            "B3: steps"
    )

    fun buildPrompt(blocks: List<RegionBlock>): String {
        val pageH = (blocks.maxOfOrNull { it.bottom } ?: 1).coerceAtLeast(1)
        val pageW = (blocks.maxOfOrNull { it.right } ?: 1).coerceAtLeast(1)
        val sb = StringBuilder()
        for ((i, b) in blocks.withIndex()) {
            val topPct = b.top * 100 / pageH
            val leftPct = b.left * 100 / pageW
            val widthPct = (b.right - b.left).coerceAtLeast(0) * 100 / pageW
            val text = b.text.replace(Regex("\\s+"), " ").trim().take(70)
            sb.append("B").append(i + 1)
                .append(" (top ").append(topPct).append("%, left ").append(leftPct)
                .append("%, width ").append(widthPct).append("%): ").append(text).append('\n')
        }
        return sb.toString().trimEnd()
    }

    private val LABEL_RE = Regex(
        "(?i)^\\s*B(\\d{1,3})\\s*[:,.\\-]\\s*(title|ingredient[s]?|steps?|directions?|other|ignore)\\b",
    )

    /**
     * Turns the model's reply into one label per block. Anything unreadable
     * or incomplete fails the whole pass (null), because half-labeled
     * regions make a worse recipe than the cues.
     */
    fun parseLabels(reply: String, blocks: List<RegionBlock>): List<RegionKind>? {
        if (blocks.size < 3) return null
        if (reply.isBlank() || reply.length > 80 * (blocks.size + 4)) return null
        val labels = arrayOfNulls<RegionKind>(blocks.size)
        var sawAny = false
        for (rawLine in reply.lines()) {
            val m = LABEL_RE.find(rawLine.trim()) ?: continue
            sawAny = true
            val idx = m.groupValues[1].toIntOrNull() ?: continue
            if (idx < 1 || idx > blocks.size) return null
            labels[idx - 1] = when (m.groupValues[2].lowercase()) {
                "title" -> RegionKind.TITLE
                "ingredient", "ingredients" -> RegionKind.INGREDIENTS
                "step", "steps", "direction", "directions" -> RegionKind.STEPS
                else -> RegionKind.OTHER
            }
        }
        if (!sawAny) return null
        // A block that is clearly a nutrition footer is never an ingredient
        // region, whatever the model said.
        val out = labels.mapIndexed { i, k ->
            val kind = k ?: RegionKind.OTHER
            if (kind == RegionKind.INGREDIENTS &&
                OcrParser.NUTRITION_RE.containsMatchIn(blocks[i].text)
            ) RegionKind.OTHER else kind
        }
        if (out.none { it == RegionKind.INGREDIENTS } || out.none { it == RegionKind.STEPS }) return null
        return out
    }

    // ---- the cue pass: what the rules can already see ----

    private val UNITS_RE = "(cups?|tablespoons?|tbsp|tbs|teaspoons?|tsp|lbs?|pounds?|ounces?|oz|cloves?|sticks?|pinch|dash|quarts?|pints?|packages?|envelopes?|cans?|slices?|sheets?)"

    // A block that OPENS with an amount is an ingredient list almost always.
    private val CUE_AMOUNT_RE = Regex(
        "(?i)^(\\d+[\\s\\d/.\\u2044-]{0,6})\\s*$UNITS_RE\\b",
    )

    // Amounts anywhere in the block, not just the line start: fused prose
    // ("begin with 1 1/2 lbs chicken breast, sliced thin, 3 tablespoons
    // soy sauce...") is the cue pass's hardest case.
    private val CUE_AMOUNT_ANY_RE = Regex(
        "(?i)\\b(\\d+[\\s\\d/.\\u2044-]{0,6})\\s*$UNITS_RE\\b",
    )

    private val CUE_STEP_MARK_RE = Regex("(?i)^\\s*(step\\s*\\d+|\\d+\\s*[.):])")
    private val CUE_HEADER_RE = Regex(
        "(?i)^(ingredients?|what you'?ll need|you will need|method|directions?|instructions|preparation)\\b",
    )
    private val CUE_NOISE_RE = Regex(
        "(?i)^(tip|caution|note|warning|makes|model|serves|yield|per serving|calories|nutrition)\\b",
    )
    private val CUE_VERB_RE = Regex(
        "(?i)\\b(whisk|beat|stir|mix|pour|heat|preheat|bake|cook|add|fold|grease|serve|return|toss|place|remove|combine|cut|slice|chop|simmer|brown|cool|cover|spread|press|flip|drizzle|season|knead|marinate|refrigerate)\\b",
    )
    private val LONE_MARK_RE = Regex("^\\s*(\\d{1,2}|\\[[^\\]]*\\])\\s*[.):]?\\s*$")
    private val CAPS_ONLY_RE = Regex("[\\sA-Z&'!:,.\\-]+")

    fun cueLabels(blocks: List<RegionBlock>): List<CueGuess> {
        val out = mutableListOf<CueGuess>()
        var titleTaken = false
        for ((i, b) in blocks.withIndex()) {
            val flat = b.text.replace(Regex("\\s+"), " ").trim()
            val wordCount = flat.split(" ").size
            val lines = b.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val first = lines.firstOrNull() ?: ""
            // The topmost short block without amounts is the title. The verb
            // check must never run first here: "Weeknight Chicken Stir-Fry"
            // contains "stir", and a one-word all-caps line is a header, not
            // a name.
            if (!titleTaken && i <= 1 && wordCount <= 8 &&
                !(wordCount == 1 && flat == flat.uppercase()) &&
                !CUE_AMOUNT_ANY_RE.containsMatchIn(flat) &&
                !CUE_HEADER_RE.containsMatchIn(flat) &&
                !CUE_NOISE_RE.containsMatchIn(flat) &&
                !LONE_MARK_RE.containsMatchIn(flat) &&
                !flat.startsWith("[")
            ) {
                out += CueGuess(RegionKind.TITLE)
                titleTaken = true
                continue
            }
            if (OcrParser.NUTRITION_RE.containsMatchIn(flat)) { out += CueGuess(RegionKind.OTHER); continue }
            if (CUE_NOISE_RE.containsMatchIn(flat)) { out += CueGuess(RegionKind.OTHER); continue }
            if (CUE_HEADER_RE.containsMatchIn(flat)) { out += CueGuess(RegionKind.OTHER); continue }
            if (LONE_MARK_RE.containsMatchIn(flat)) { out += CueGuess(RegionKind.OTHER); continue }
            if (CAPS_ONLY_RE.matches(flat)) { out += CueGuess(RegionKind.OTHER); continue }
            val amounts = CUE_AMOUNT_ANY_RE.findAll(flat).count()
            val startsAmount = CUE_AMOUNT_RE.containsMatchIn(first)
            val isStep = CUE_STEP_MARK_RE.containsMatchIn(first)
            val verbs = CUE_VERB_RE.findAll(flat).count()
            out += CueGuess(
                when {
                    isStep -> RegionKind.STEPS
                    startsAmount && amounts >= 1 && verbs <= 1 -> RegionKind.INGREDIENTS
                    // Amounts AND action verbs together: the cues are split.
                    amounts >= 3 && verbs >= 2 -> null
                    amounts >= 2 && verbs <= 2 -> RegionKind.INGREDIENTS
                    verbs >= 2 && amounts == 0 -> RegionKind.STEPS
                    verbs >= 1 && amounts <= 1 -> RegionKind.STEPS
                    amounts == 0 && verbs == 0 -> RegionKind.OTHER
                    else -> null
                },
            )
        }
        return out
    }

    /**
     * A cue the rules could not decide, with no model available: heavy
     * amounts mean ingredient material, action verbs mean instructions,
     * anything else stays out of the recipe.
     */
    fun fallbackKind(block: RegionBlock): RegionKind {
        val flat = block.text.replace(Regex("\\s+"), " ").trim()
        val amounts = CUE_AMOUNT_ANY_RE.findAll(flat).count()
        val verbs = CUE_VERB_RE.findAll(flat).count()
        return when {
            amounts >= 3 -> RegionKind.INGREDIENTS
            verbs >= 2 -> RegionKind.STEPS
            else -> RegionKind.OTHER
        }
    }

    /**
     * Final labels: cue verdicts stand, the model speaks only on the blocks
     * the cues left undecided, and the heuristic covers a model failure.
     */
    fun resolveLabels(
        blocks: List<RegionBlock>,
        cues: List<CueGuess>,
        modelLabels: List<RegionKind>?,
    ): List<RegionKind> =
        cues.mapIndexed { i, guess ->
            guess.kind
                ?: modelLabels?.getOrNull(i)
                ?: fallbackKind(blocks[i])
        }

    suspend fun label(
        context: Context,
        blocks: List<RegionBlock>,
        onStatus: (String) -> Unit,
    ): List<RegionKind>? {
        if (blocks.size < 3) return null
        val cues = cueLabels(blocks)
        val ambiguous = cues.none { it.kind == null }
        if (ambiguous) return cues.map { it.kind!! }
        // Only pages the cues cannot read reach the model.
        if (!RecipeLlm.hasModel(context)) return resolveLabels(blocks, cues, null)
        return try {
            if (!RecipeLlm.ready) RecipeLlm.initEngine(context)
            onStatus("AI is finding the recipe sections")
            val reply = RecipeLlm.classify(SYSTEM, buildPrompt(blocks)) ?: return resolveLabels(blocks, cues, null)
            resolveLabels(blocks, cues, parseLabels(reply, blocks))
        } catch (t: Throwable) {
            resolveLabels(blocks, cues, null)
        }
    }
}
