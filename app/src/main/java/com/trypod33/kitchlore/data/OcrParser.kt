package com.trypod33.kitchlore.data

// Deterministic recipe parser (the issue #1 parser): raw OCR or document text
// -> structured recipe, no LLM involved.

data class ParsedIngredient(
    val name: String,
    val rawText: String,
    val amount: Double? = null,
    val unit: String? = null,
    /** A unit came through with no number ("tablespoons creamy peanut butter"): the scanner lost the amount. Shown to the reviewer. */
    val needsAmount: Boolean = false,
)

data class ParsedStep(
    val instructionText: String,
    val timerSeconds: Int? = null,
)

data class ParsedRecipe(
    val title: String,
    val baseServings: Int = 1,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val ingredients: List<ParsedIngredient> = emptyList(),
    val steps: List<ParsedStep> = emptyList(),
)

object OcrParser {

    private val FRACTIONS = mapOf(
        '½' to 0.5, '¼' to 0.25, '¾' to 0.75,
        '⅓' to 1.0 / 3, '⅔' to 2.0 / 3,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
        '⅕' to 0.2, '⅖' to 0.4, '⅗' to 0.6, '⅘' to 0.8,
        '⅙' to 1.0 / 6, '⅚' to 5.0 / 6,
    )

    private val UNITS = setOf(
        "cup", "cups", "c", "tbsp", "tbs", "tablespoon", "tablespoons",
        "tsp", "teaspoon", "teaspoons", "oz", "ounce", "ounces",
        "lb", "lbs", "pound", "pounds", "g", "gram", "grams", "kg",
        "ml", "l", "liter", "liters", "litre", "litres",
        "pint", "pints", "quart", "quarts", "quart", "stick", "sticks",
        "clove", "cloves", "can", "cans", "package", "packages",
        "pkg", "pkt",
        "envelope", "envelopes", "pinch", "dash", "sprig", "sprigs",
        "slice", "slices", "sheet", "sheets", "strip", "strips",
        "small", "medium", "large", "bunch", "bunches", "head", "heads",
        "ear", "ears", "square", "squares", "bag", "bags", "box", "boxes",
        "jar", "jars", "tube", "tubes", "container", "containers",
        "packet", "packets", "carton", "cartons", "bottle", "bottles",
    )

    private val ACTION_VERBS = listOf(
        "preheat", "mix", "stir", "whisk", "fold", "beat", "cream", "add",
        "pour", "heat", "cook", "bake", "roast", "grill", "fry", "saute",
        "sauté", "simmer", "boil", "place", "spread", "cover", "remove",
        "serve", "top", "drizzle", "season", "chop", "dice", "mince",
        "slice", "grease", "line", "knead", "marinate", "refrigerate",
        "seal", "soften", "whip",
        "pulse", "process", "whirl", "liquefy", "puree", "microwave", "blend",
        "steep", "strain", "toast", "zest", "juice",
        "transfer", "combine", "layer", "sprinkle", "melt", "brown",
        "garnish", "rest", "carve", "brush", "coat", "press", "shape",
        "roll", "cut", "let",
    )

    private val SERVINGS_BEFORE_RE = Regex(
        "(\\d+)\\s*(?:servings?|serves|portions?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val SERVINGS_RE = Regex(
        "(?:serves|servings?|makes|yield(?:s)?|yields)\\s*:?\\s*(\\d+)",
        RegexOption.IGNORE_CASE,
    )
    private val PREP_RE = Regex(
        "prep(?:aration)?\\s*time\\s*:?\\s*(\\d+)\\s*(min(?:ute)?s?|hours?|hrs?|h)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val COOK_RE = Regex(
        "(?:cook|bake|chill|total)\\s*time\\s*:?\\s*(\\d+)\\s*(min(?:ute)?s?|hours?|hrs?|h)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val SECTION_ING_RE = Regex(
        "(?:^(?:ingredients?|what you'?ll need|you will need)\\s*:?\\s*$|.*\\bwill need\\s*:?\\s*$)",
        RegexOption.IGNORE_CASE,
    )
    private val SECTION_STEP_RE = Regex(
        "^(?:instructions?|instruction|directions?|method|steps?|preparation|procedure)\\s*:?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    // Layout-aware section detection: the scan only ever fills a recipe from
    // inside a labeled block, so page headers, description prose, timing
    // notes and storage instructions (which live outside the blocks) never
    // reach the ingredient list. Headers survive OCR in variants
    // ("lngredients", "Directlons"), so match the stable middle of the word,
    // and only on short lines so a step mentioning ingredients never counts.
    private fun headerKey(line: String): String {
        val l = line.trim().trimEnd(':', ' ', '.').lowercase()
        return if (l.length <= 30 && l.split(Regex("\\s+")).size <= 4) l else ""
    }

    private fun isIngHeader(line: String): Boolean {
        val t = line.trim().trimEnd(':', ' ', '.').lowercase()
        // "For 4 servings you will need:" leads with for/what/you. A prose
        // line that merely ends in "will need" ("add as much as you will
        // need") does not.
        if ((t.startsWith("for") || t.startsWith("what") || t.startsWith("you")) &&
            t.endsWith("will need")
        ) return true
        val l = headerKey(line)
        if (l.contains("what you")) return true
        if (l.contains("gredient")) {
            // Bare "Ingredients" is a header. A wrapped step tail like
            // "ingredients for sauce." (the onions card's step 1) once
            // counted as one and swallowed every step below it.
            return l.split(Regex("\\s+")).size <= 2
        }
        return false
    }

    private fun isStepHeader(line: String): Boolean {
        val l = headerKey(line)
        return l.contains("nstruct") || l.contains("irect") || l.contains("reparat") ||
            l.contains("rocedure") || l == "method" || l.startsWith("method ") ||
            l == "steps" || l.startsWith("steps ")
    }
    // Side sections that share a column with the recipe body ("Tips:",
    // "Good served with:"). A body block stops when one of these appears,
    // so their prose never rides into the ingredients or the steps.
    private fun isNoiseSectionHeader(line: String): Boolean {
        val l = headerKey(line)
        return l in setOf(
            "tips", "tip", "note", "notes", "hint", "hints", "good served with",
            "serving suggestion", "variation", "variations", "make ahead",
            "storage", "kitchen notes", "to serve",
        )
    }

    private val PROSE_LINE_RE = Regex(
        "^(?:you may|tip|tips|note|variation|make ahead|storage|to serve|serving suggestion|" +
            "store|stored|keeps?|sealed|airtight|wrap(?:ped)?|and|or|until|with|for|in|" +
            "from|may|inside|outside|these|this|they)\\b",
        RegexOption.IGNORE_CASE,
    )

    // A line that is just an oven temperature, optionally with "bake/heat at".
    private val TEMP_LINE_RE = Regex(
        "^(?:bake|heat|preheat|cook|set)?\\s*(?:at\\s*)?\\d{2,4}\\s*(?:degrees?\\s*)?(?:°\\s*)?[FCfF]\\b\\.?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    // A line that starts with an amount the scanner garbled: "h cup broth",
    // "V2 cup sugar", "Vs tsp salt", "1% cups flour", "3/4 cup milk".
    private val GARBLED_QTY_LINE_RE = Regex(
        "^\\s*(?:[hvVy]\\s*\\d?\\s*)?(?:cups?|tbsps?|tsps?|tablespoons?|teaspoons?|qts?|qts\\.|oz|ounces?|lbs?|pounds?|cloves?|slices?)\\b|^(?:Vs|V\\d|[Yy]\\d)\\b|^\\d+\\s*[/%%]\\s*\\d?|^[hVy]\\b",
        RegexOption.IGNORE_CASE,
    )

    private val NUM_PREFIX_RE = Regex("^\\s*(?:step\\s*)?(\\d{1,2})(?:[.)\\]])?(?=\\s|$)", RegexOption.IGNORE_CASE)
    private val BULLET_PREFIX_RE = Regex("^\\s*[•*\\-–]\\s+")
    private val TIMER_RE = Regex(
        "(\\d+)\\s*(?:to\\s*\\d+\\s*)?(min(?:ute)?s?|hours?|hrs?|seconds?|secs?)\\b",
        RegexOption.IGNORE_CASE,
    )

    // One recognized line with its pixel box on the page, so side-by-side
    // layouts can be read back in true column order.
    data class OcrLineBox(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    // Rebuilds reading order for a side-by-side layout: split the lines on
    // the widest horizontal gap between line centers, keep each column's
    // vertical order, left column first. Returns null unless the card is
    // clearly two-column, and the caller then falls back to plain order.
    // Two-column detection off the line START edges: a wrapped column's
    // lines share their left edge, so the gutter shows up as a wide gap
    // between start positions even when the text runs nearly into it (the
    // onions card's left column wraps within a character of the steps
    // column, so center-based splitting found no gap).
    private fun columnSplit(boxes: List<OcrLineBox>): Pair<List<OcrLineBox>, List<OcrLineBox>>? {
        val usable = boxes.filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
        if (usable.size < 6) return null
        val span = (usable.maxOf { it.right } - usable.minOf { it.left }).toDouble()
        if (span <= 0) return null
        val starts = usable.map { it.left }.sorted()
        // The widest start-edge gap is not always the real gutter: a title or
        // caption stranded on the far left (Keith's chicken-salad card) opens
        // a gap wider than the ingredient/steps gutter, and splitting there
        // strands a 4-line fragment as the "left column" while the real two
        // columns scramble together. Score every wide gap and keep the split
        // that leaves two substantial side-by-side columns.
        // A genuine gutter is also WIDE relative to the text: on a
        // single-column cookbook card (Keith's stuffed mushrooms) the gap
        // between an ingredient's quantity column and its wrapped text is
        // 2-3 line heights, while a real gutter runs 10x. Reject gaps under
        // 4 median line heights so one-column cards never split.
        val heights = usable.map { (it.bottom - it.top).coerceAtLeast(1) }.sorted()
        val medianH = heights[heights.size / 2].toDouble()
        var best: Pair<List<OcrLineBox>, List<OcrLineBox>>? = null
        var bestScore = 0
        var bestGap = 0.0
        for (i in 0 until starts.size - 1) {
            val gap = (starts[i + 1] - starts[i]).toDouble()
            if (gap < span * 0.1 || gap < medianH * 4) continue
            val split = (starts[i] + starts[i + 1]) / 2.0
            val leftCol = usable.filter { it.left < split }.sortedBy { it.top }
            val rightCol = usable.filter { it.left >= split }.sortedBy { it.top }
            if (leftCol.size < 3 || rightCol.size < 3) continue
            // True side-by-side: the columns have to share vertical range,
            // not stack like two pages.
            val overlap = minOf(leftCol.maxOf { it.bottom }, rightCol.maxOf { it.bottom }) -
                maxOf(leftCol.minOf { it.top }, rightCol.minOf { it.top })
            val shorter = minOf(
                leftCol.maxOf { it.bottom } - leftCol.minOf { it.top },
                rightCol.maxOf { it.bottom } - rightCol.minOf { it.top },
            ).toDouble()
            if (overlap <= 0 || overlap < shorter * 0.3) continue
            val score = minOf(leftCol.size, rightCol.size)
            if (score > bestScore || (score == bestScore && gap > bestGap)) {
                bestScore = score
                bestGap = gap
                best = leftCol to rightCol
            }
        }
        return best
    }

    private fun columnOrdered(boxes: List<OcrLineBox>): List<String>? {
        val cols = columnSplit(boxes) ?: return null
        return (cols.first + cols.second).map { it.text.trim() }.filter { it.isNotEmpty() }
    }

    fun parse(
        text: String,
        blocks: List<Pair<String, Int>> = emptyList(),
        rules: Map<String, String> = emptyMap(),
        boxes: List<OcrLineBox> = emptyList(),
    ): ParsedRecipe {
        // A two-column card read straight off the page interleaves the
        // columns, which bleeds steps into the ingredient list and
      // ingredients into the steps (Keith's cream-cheese mints card).
        // With line boxes the reading order is rebuilt column by column;
        // without them, plain text order stands.
        val ordered = if (boxes.isNotEmpty()) columnOrdered(boxes) else null
        val srcLines: List<String> = ordered ?: text.lines()
        // Character-level cleanup first (recognizer garbles like "SMOCTHIE"
        // for SMOOTHIE), then amounts the card's tight wrapping orphaned onto
        // their own line are rejoined to the unit line below them.
        val lines = srcLines.map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { OcrLexicon.fixLine(it) }
            .let { rejoinOrphanAmounts(it) }
            .let { joinHyphenLines(it) }
        if (lines.isEmpty()) return ParsedRecipe("(untitled)")

        val servings =
            SERVINGS_RE.find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: SERVINGS_BEFORE_RE.find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: 1
        val prep = parseTimeLine(text, PREP_RE)
            ?: parseTimeLine(text, Regex("prep\\s*:\\s*(\\d+)\\s*(min(?:ute)?s?|hours?|hrs?|h)\\b", RegexOption.IGNORE_CASE))
        val cook = parseTimeLine(text, COOK_RE)
            ?: parseTimeLine(text, Regex("(?:bake|cook|chill)\\s*:\\s*(\\d+)\\s*(min(?:ute)?s?|hours?|hrs?|h)\\b", RegexOption.IGNORE_CASE))

        var title: String? = null
        // The title is the tallest text at the TOP of the card: with line
        // boxes, take the tallest line in the top fifth of the page and
        // relax the all-caps card-art rule for it (real cookbook titles are
        // ALL CAPS; a stamp like "DELICIOUS" sits mid-card and is never the
        // top zone's tallest line).
        if (boxes.isNotEmpty()) {
            val hs = boxes.map { (it.bottom - it.top).coerceAtLeast(1) }.sorted()
            val medianH = hs[hs.size / 2]
            val pageTop = boxes.minOf { it.top }
            val pageBottom = boxes.maxOf { it.bottom }
            val topZoneEnd = pageTop + (pageBottom - pageTop) / 5
            val tall = boxes.filter { it.top <= topZoneEnd }
                .sortedByDescending { it.bottom - it.top }
                .firstOrNull { b ->
                    val c = OcrLexicon.fixLine(b.text.trim())
                    c.length in 3..90 && !c.contains("serves", ignoreCase = true) &&
                        !LEADING_QTY_RE.containsMatchIn(c) && !LEADING_QTY_ALT_RE.containsMatchIn(c) &&
                        !c.trimEnd().endsWith(":") && !NUTRITION_RE.containsMatchIn(c)
                }
            if (tall != null && (tall.bottom - tall.top) >= medianH * 5 / 4) {
                title = OcrLexicon.fixLine(tall.text.trim())
            }
        }
        if (blocks.isNotEmpty()) {
            val cand = blocks.maxByOrNull { it.second }?.first?.let { OcrLexicon.fixLine(it.trim()) }
            if (!cand.isNullOrBlank() && cand.length <= 90 && !cand.contains("serves", ignoreCase = true) &&
                !isNoise(cand) && !LEADING_QTY_RE.containsMatchIn(cand) &&
                    !LEADING_QTY_ALT_RE.containsMatchIn(cand) && !cand.trimEnd().endsWith(":")
            ) {
                title = cand
            }
        }
        if (title == null) {
            title = lines.firstOrNull {
                !SERVINGS_RE.containsMatchIn(it) && !PREP_RE.containsMatchIn(it) && !COOK_RE.containsMatchIn(it) &&
                    !isNoise(it) && !LEADING_QTY_RE.containsMatchIn(it) &&
                    !LEADING_QTY_ALT_RE.containsMatchIn(it) && !it.trimEnd().endsWith(":") &&
                    // "PREP: 25 MINUTES BAKE: 8 MINUTES", "Oven 425": timing
                    // and temperature notes, never the recipe title.
                    !Regex("^(?:prep|bake|cook|chill|total|oven)\\b.*\\d", RegexOption.IGNORE_CASE)
                        .containsMatchIn(it)
            }
        }

        val titleLineIdx = lines.indexOfFirst { it.equals(title, ignoreCase = true) }
        val startIdx = if (titleLineIdx >= 0) titleLineIdx + 1 else 0

        val ingIdx = lines.indexOfFirst { isIngHeader(it) }
        var stepIdx = -1
        if (ingIdx >= 0) {
            val rel = lines.subList(ingIdx + 1, lines.size).indexOfFirst { isStepHeader(it) }
            if (rel >= 0) stepIdx = ingIdx + 1 + rel
        } else {
            stepIdx = lines.indexOfFirst { isStepHeader(it) }
        }

        val ingredients = mutableListOf<ParsedIngredient>()
        val steps = mutableListOf<ParsedStep>()

        // Layout-driven extraction: the section headers mark real spots on
        // the page, and only the lines that physically sit between the
        // Ingredients header and the next header are ingredients. Prose
        // above the block, timing notes and footers below it never enter,
        // and ingredients cannot leak into the steps.
        val layout = if (boxes.isNotEmpty()) layoutSections(boxes) else null

        when {
            layout != null && layout.first.isNotEmpty() -> {
                val ingLines = rejoinOrphanAmounts(
                    joinHyphenLines(layout.first.map { OcrLexicon.fixLine(it.trim()) })
                )
                ingLines.forEach { l ->
                    if (isIngredientNoise(l)) return@forEach
                    val prev = ingredients.lastOrNull()
                    if (prev != null && isContinuation(l, prev)) {
                        ingredients[ingredients.size - 1] = ParsedIngredient(
                            name = (prev.name + " " + l).trim(),
                            rawText = (prev.rawText + " " + l).trim(),
                            amount = prev.amount,
                            unit = prev.unit,
                        )
                    } else {
                        ingredients.add(parseIngredientLine(l, rules))
                    }
                }
                // A measurement-shaped line that continues an OPEN step
                // ("...toast pecans at" / "mayonnaise, yogurt or sour cream,")
                // is that step's continuation, not an ingredient the header
                // match ran past; only a measurement line after a CLOSED step
                // is dropped here.
                val stepJoined = joinHyphenLines(layout.second.map { OcrLexicon.fixLine(it.trim()) })
                val stepLines = mutableListOf<String>()
                var prevOpen = false
                var sawNumbered = false
                for (l in stepJoined) {
                    if (NUTRITION_RE.containsMatchIn(l)) continue
                    if (STEP_NUM_RE.containsMatchIn(l)) {
                        sawNumbered = true
                        stepLines.add(l)
                        prevOpen = false
                        continue
                    }
                    if (sawNumbered) {
                        // Once the card numbers its steps, every line in the
                        // directions region belongs to the current step until
                        // the next number: an amount-led line here is step
                        // prose ("1 teaspoon whole cloves on a double-thick,
                        // 6-inch-"), never an ingredient the header missed.
                        if (stepLines.isNotEmpty()) {
                            stepLines[stepLines.size - 1] = (stepLines.last() + " " + l).trim()
                        } else {
                            stepLines.add(l)
                        }
                        continue
                    }
                    if (!isMeasurementLine(l) || looksLikeStep(l) || hasStepVerb(l) || prevOpen) {
                        stepLines.add(l)
                        prevOpen = !SENTENCE_END_RE.containsMatchIn(l.trimEnd())
                    }
                }
                steps.addAll(parseSteps(stepLines))
            }
            ingIdx >= 0 -> {
                // Strict window: only the lines between the Ingredients
                // header and the next section header are ingredients.
                // Everything before the header or after the next one (page
                // headers, description prose, timing notes, storage
                // instructions) stays out of the recipe.
                // No instructions header on the card: the first step-shaped
                // line after the ingredients header ends the ingredient block
                // (cards list ingredients, then instructions), so step lines
                // cannot ride in with the amounts.
                val softEnd = if (stepIdx >= 0) stepIdx else {
                    val rel = lines.subList(ingIdx + 1, lines.size)
                        .indexOfFirst { looksLikeStep(it) && !isIngredientNoise(it) }
                    if (rel >= 0) ingIdx + 1 + rel else lines.size
                }
                val windowEnd = if (stepIdx >= 0) stepIdx else softEnd
                val ingLines = lines.subList(ingIdx + 1, windowEnd)
                val stepLines = if (stepIdx >= 0) {
                    // Steps keep everything after their header, except a plain
                    // measurement line with no step verb anywhere ("pkg. (8
                    // oz.) cream cheese"): that is an ingredient the header
                    // match ran past, not an instruction.
                    lines.subList(stepIdx + 1, lines.size).filter { l ->
                        !isMeasurementLine(l) || looksLikeStep(l) || hasStepVerb(l)
                    }
                } else {
                    lines.subList(softEnd, lines.size)
                        .filter { looksLikeStep(it) && !isIngredientNoise(it) }
                }
                ingLines.forEach { l ->
                    if (isIngredientNoise(l)) return@forEach
                    val prev = ingredients.lastOrNull()
                    if (prev != null && isContinuation(l, prev)) {
                        ingredients[ingredients.size - 1] = ParsedIngredient(
                            name = (prev.name + " " + l).trim(),
                            rawText = (prev.rawText + " " + l).trim(),
                            amount = prev.amount,
                            unit = prev.unit,
                        )
                    } else {
                        ingredients.add(parseIngredientLine(l, rules))
                    }
                }
                steps.addAll(parseSteps(stepLines))
                if (stepIdx < 0) {
                    // The ingredient block ended at the first step-shaped
                    // line; the measurement shape signal is the backstop for
                    // whatever still slipped in.
                    ingredients.removeAll { ing -> !isMeasurementLine(ing.rawText.ifBlank { ing.name }) }
                }
            }
            else -> {
                var lastWasStep = false
                var sawNumberedStep = false
                lines.forEachIndexed { i, l ->
                    if (i < startIdx || i == titleLineIdx || isNoise(l)) return@forEachIndexed
                    when {
                        // Once the card numbers its steps, an unnumbered
                        // line that follows is that step's own continuation
                        // ("Serve cider in mugs..." mid step 2 of the
                        // hot-spiced-cider card), never a fresh verb-led
                        // step. Checked before isIngredientNoise: the prose
                        // rules would otherwise drop the servings line.
                        sawNumberedStep &&
                            steps.isNotEmpty() &&
                            isStepTail(l, steps.last().instructionText, numberedMode = true) -> {
                            appendToSteps(steps, l, forceJoin = true)
                        }
                        looksLikeStep(l) -> {
                            appendToSteps(steps, l)
                            lastWasStep = true
                            if (NUM_PREFIX_RE.containsMatchIn(l)) sawNumberedStep = true
                        }
                        // A line continuing the previous step (a lowercase
                        // tail, a measurement-shaped line after an open
                        // step, prose left hanging on a function word) is
                        // that step's continuation, not an ingredient.
                        // Checked BEFORE isIngredientNoise: the prose rules
                        // otherwise eat wrapped step tails ("reserve caps.
                        // Chop enough stems to make 1 cup.").
                        lastWasStep &&
                            steps.isNotEmpty() &&
                            isStepTail(l, steps.last().instructionText, numberedMode = false) -> {
                            appendToSteps(steps, l, forceJoin = true)
                            lastWasStep = true
                        }
                        isIngredientNoise(l) -> {}
                        isContinuation(l, ingredients.lastOrNull()) -> {
                            val prev = ingredients.last()
                            ingredients[ingredients.size - 1] = ParsedIngredient(
                                name = (prev.name + " " + l).trim(),
                                rawText = (prev.rawText + " " + l).trim(),
                                amount = prev.amount,
                                unit = prev.unit,
                            )
                            lastWasStep = false
                        }
                        isMeasurementLine(l) -> {
                            ingredients.add(parseIngredientLine(l, rules))
                            lastWasStep = false
                        }
                        else -> {
                            // No amount, no unit, not a wrapped tail: prose,
                            // footer text or a stray fragment. Out.
                            lastWasStep = false
                        }
                    }
                }
            }
        }

        return ParsedRecipe(
            title = (title ?: "(untitled)").removeSuffix(":").trim(),
            baseServings = servings.coerceAtLeast(1),
            prepTimeMinutes = prep,
            cookTimeMinutes = cook,
            ingredients = ingredients.filter { it.name.isNotBlank() || it.rawText.isNotBlank() },
            steps = steps.filter { it.instructionText.isNotBlank() },
        )
    }

    fun parseIngredientLine(
        line: String,
        rules: Map<String, String> = emptyMap(),
    ): ParsedIngredient {
        val raw = line.trim().replace(BULLET_PREFIX_RE, "")
        // OCR garbles the fraction glyphs, and one garble can mean different
        // fractions on the same card ("%"/"1%" read as 1/4, 1/3 and 1 1/2 on
        // Keith's test card), so those stay visible for the reviewer to fix.
        // The reliable reads get cleaned up: "Vs" is 1/3, "V2" is 1/2, "|" is
        // a 1, and a unit mushed into an "h" ("hcup") is 1/2 of that unit.
        // The deterministic amount pass: garbled tokens ("% ", "1%", "Vs",
        // "V2", "h", "hcup") snap to their fractions in code, hand
        // corrections first, so the reviewer rarely has to fix an amount.
        val tokens = AmountFixer.fixLine(raw, rules).split(Regex("\\s+")).filter { it.isNotEmpty() }.flatMap { tok ->
            val bare = tok.trimEnd('.', ',')
            val hun = Regex("^h(cups?|tbsps?|tsps?|tablespoons?|teaspoons?)$", RegexOption.IGNORE_CASE).find(bare)
            when {
                bare.equals("Vs", true) -> listOf("1/3")
                bare.equals("V2", true) -> listOf("1/2")
                bare == "|" || bare == "I" || bare == "l" -> listOf("1")
                hun != null -> listOf("1/2", hun.groupValues[1])
                else -> listOf(tok)
            }
        }
        var idx = 0
        var amount: Double? = null
        var unit: String? = null
        var missingAmount = false
        if (tokens.isNotEmpty()) {
            val a = parseAmount(tokens[0])
            if (a != null) {
                amount = a
                idx = 1
                // mixed number like "1 1/2"
                if (idx + 1 < tokens.size && Regex("^\\d+$").matches(tokens[idx]) &&
                    tokens[idx + 1].contains('/')
                ) {
                    val b = parseAmount(tokens[idx] + " " + tokens[idx + 1])
                    if (b != null) {
                        amount = b
                        idx += 2
                    }
                }
                // "1/4 to 1/2 cup": a range keeps the first amount, and both
                // ends drop out of the name instead of riding in as text.
                if (idx + 1 < tokens.size && tokens[idx].equals("to", ignoreCase = true) &&
                    parseAmount(tokens[idx + 1]) != null
                ) {
                    idx += 2
                }
            }
        }
        val unitCand: (Int) -> String? = { i ->
            val c = tokens[i].trimEnd('.', ',').lowercase()
            if (c in UNITS) tokens[i].trimEnd('.', ',') else null
        }
        if (amount != null && idx < tokens.size) {
            unitCand(idx)?.let {
                unit = it
                idx++
            }
        } else if (amount == null && idx < tokens.size) {
            // "cup chopped green onion": the scan lost the amount, keep the
            // unit and flag the line so the reviewer knows the number is gone.
            unitCand(idx)?.let {
                unit = it
                idx++
                missingAmount = idx < tokens.size
            }
        }
        val name = tokens.drop(idx).joinToString(" ").trim()
        return ParsedIngredient(
            name = name.ifBlank { raw },
            rawText = raw,
            amount = amount,
            unit = unit,
            needsAmount = missingAmount,
        )
    }

    fun parseAmount(s: String): Double? {
        var str = s.trim()
        var total = 0.0
        var found = false
        for ((ch, v) in FRACTIONS) {
            if (str.contains(ch)) {
                total += v
                found = true
                str = str.replace(ch.toString(), " ")
            }
        }
        str = str.trim()
        if (str.isEmpty()) return if (found) total else null
        val mixed = Regex("^(\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)$").find(str)
        if (mixed != null) {
            return total + mixed.groupValues[1].toDouble() +
                mixed.groupValues[2].toDouble() / mixed.groupValues[3].toDouble()
        }
        val frac = Regex("^(\\d+)\\s*/\\s*(\\d+)$").find(str)
        if (frac != null) {
            val d = frac.groupValues[2].toDouble()
            if (d > 0) return total + frac.groupValues[1].toDouble() / d
        }
        val dec = Regex("^(\\d+(?:[.,]\\d+)?)$").find(str)
        if (dec != null) return total + dec.groupValues[1].replace(',', '.').toDouble()
        return if (found) total else null
    }

    fun timerFor(text: String): Int? {
        val m = TIMER_RE.find(text) ?: return null
        val n = m.groupValues[1].toDouble()
        return when (m.groupValues[2].lowercase()) {
            "h", "hour", "hours", "hr", "hrs" -> (n * 3600).toInt()
            else -> n.toInt() * 60  // minutes and anything ambiguous
        }
    }

    private fun parseTimeLine(text: String, re: Regex): Int? {
        val m = re.find(text) ?: return null
        val n = m.groupValues[1].toDouble()
        return when (m.groupValues[2].lowercase()) {
            "hour", "hours", "hr", "hrs", "h" -> (n * 60).toInt()
            else -> n.toInt()
        }
    }

    private val JUNK_EXACT = setOf(
        "dessert", "entree", "main dish", "salad", "soup", "appetizer", "bread",
        "beverage", "breakfast", "side dish", "favorites", "delicious", "tips",
        "great american recipes", "all rights reserved",
    )
    private val JUNK_LINE_RE = Regex(
        "^(?:dessert|favorites|great american|all rights|www\\.)\\b.*$|" +
            "(?:card|group)\\s*\\d+\\s*$|" +
            "preparation time|baking time|cook time|oven temperature|microwave cooking|see other side|" +
            "^\\d+\\s*servings?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val LEADING_QTY_RE = Regex(
        "^(?:\\d+|\\d+/\\d+|[¼½¾⅓⅔⅛⅜⅝⅞])\\s",
    )
    private val LEADING_QTY_ALT_RE = Regex(
        "^(?:\\d+|[¼½¾⅓⅔⅛⅜⅝⅞])(?:\\s|\\d*[/½¾⅓⅔⅛⅜⅝⅞])",
    )

    // Page furniture the scanner sweeps up: section headers and banner words.
    private val PAGE_HEADERS = setOf(
        "recipes", "recipe", "kitchen", "cookbook", "favorites", "favorite",
        "index", "contents", "page",
    )

    // One-word fragments prose leaves behind when the scanner chops a
    // paragraph into the ingredient grid. Never ingredient names.
    private val NOISE_WORDS = setOf(
        "time", "them", "they", "these", "this", "that", "from", "your", "you",
        "any", "all", "and", "the", "for", "with", "when", "until", "after",
        "before", "then", "also", "once", "while", "its", "it's", "are", "was",
        "were", "over", "into", "onto", "each", "every", "some", "more", "very",
        "well", "just", "only", "gifts", "wonderful",
    )

    private val PHRASE_NOISE = setOf(
        "room temperature", "at room temperature", "well wrapped", "to serve",
        "or until firm", "unless otherwise noted",
    )

    private fun isNoise(line: String): Boolean {
        if (SERVINGS_RE.containsMatchIn(line) || PREP_RE.containsMatchIn(line) || COOK_RE.containsMatchIn(line)) {
            return true
        }
        if (JUNK_LINE_RE.containsMatchIn(line)) return true
        val clean = line.trim().trimEnd('.', ' ').trim()
        if (clean.endsWith(":") && clean.none { it.isDigit() } && clean.length <= 25) return true
        // Card art: a short all-caps banner or stamp ("QUICK & DELICIOUS",
        // "QUICK&") is never recipe content. A one-word all-caps line keeps
        // passing so an ingredient written in caps survives.
        if (clean.length in 3..20 && clean.none { it.isDigit() } &&
            clean.none { it.isLowerCase() } && clean.any { it.isLetter() } &&
            (clean.any { !it.isLetterOrDigit() && it != ' ' } || clean.split(Regex("\\s+")).size >= 2)
        ) return true
        if (clean.length <= 30 && clean.lowercase() in JUNK_EXACT) return true
        // Legal footer ("(c) MCMLXXIX GREAT AMERICAN RECIPES(R) ALL RIGHTS RESERVED").
        if (clean.contains("rights reserved", ignoreCase = true) || clean.contains("\u00A9")) return true
        // Section headers ("RECIPES") and stray one-word prose fragments.
        if (clean.none { it.isDigit() } &&
            clean.trimEnd(',', '.', ';', ':', '!', '?').lowercase().let { w ->
                w.split(Regex("\\s+")).size == 1 && (w in PAGE_HEADERS || w in NOISE_WORDS)
            }
        ) return true
        // OCR welds footer brands into one word ("GREATAMERICAN"): a single
        // all-caps token that long is page furniture, never an ingredient.
        if (clean.length >= 11 && clean.none { it.isDigit() } &&
            clean.none { it.isLowerCase() } && clean.any { it.isLetter() }
        ) return true
        // Long all-caps banner text ("CMOMI VXXXVII GREAT AMERI"): page
        // decoration, never content. A one-word all-caps line still passes so
        // an ingredient written in caps survives.
        if (clean.length > 20 && clean.none { it.isDigit() } &&
            clean.none { it.isLowerCase() } && clean.any { it.isLetter() } &&
            clean.split(Regex("\\s+")).size >= 2
        ) return true
        return false
    }

    // OCR wraps long ingredient lines; a short lowercase line with no leading
    // amount that follows an amount-bearing ingredient is its continuation
    // ("1.5 cups concentrated chicken" + "broth").
    private fun isContinuation(line: String, prev: ParsedIngredient?): Boolean {
        if (prev == null) return false
        // A previous line whose amount OCR garbled ("1% cups concentrated
        // chicken") may not have parsed to a number, but it is still an
        // amount-led ingredient, so its wrapped tail ("broth") belongs to it.
        if (prev.amount == null &&
            !GARBLED_QTY_LINE_RE.containsMatchIn(prev.rawText.ifBlank { prev.name })
        ) return false
        val t = line.trim()
        if (t.isEmpty()) return false
        if (LEADING_QTY_RE.containsMatchIn(t) || LEADING_QTY_ALT_RE.containsMatchIn(t)) return false
        // A line that carries its own amount (plain or OCR-garbled, "1/2 cup
        // broth" or "h cup broth") is a new ingredient, never a continuation;
        // without this the next ingredient's amount lands inside the last one
        // ("celery, sliced h cup").
        if (GARBLED_QTY_LINE_RE.containsMatchIn(t)) return false
        val tokens = t.split(Regex("\\s+"))
        if (tokens.size > 3) return false
        // A one-token line the lexicon doesn't know ("vder,", "stir") is a
        // torn page-margin fragment, never the tail of the ingredient
        // above it.
        if (tokens.size == 1 && !OcrLexicon.isKnownWord(tokens[0])) return false
        // A line that talks ("mixture is soft, creamy and") is never the
        // tail of the ingredient above it.
        if (Regex("\\b(?:is|are|should|make|makes|will|can)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(t)
        ) return false
        val c = tokens[0][0]
        if (c.isDigit() || c.isUpperCase()) return false
        if (parseAmount(tokens[0]) != null) return false
        return true
    }

    // A short line with no amount and no unit that dangles on a preposition or
    // conjunction ("Poultry at", "well wrapped and") is a torn-off fragment of
    // a wrapped line, not an ingredient.
    private val DANGLING_END_RE = Regex(
        "\\b(?:at|or|and|of|in|with|for|to|from|into|over|until)\\s*[.,;]??\\s*$",
        RegexOption.IGNORE_CASE,
    )

    // Rules that only apply to ingredient lines: steps can run long and can
    // legitimately contain temperatures ("Bake at 350°F for 30 minutes").
    private fun isIngredientNoise(line: String): Boolean {
        // Nutrition footers are measurement-shaped but never ingredients
        // ("9 mg sodium, 40 g carbo." / "Daily Values: 0% vit. A").
        if (NUTRITION_RE.containsMatchIn(line)) return true
        // A lowercase line carrying a mid-sentence ")." is a wrapped tail of
        // an instruction ("photo, page 83). Add bag to cider"), never an
        // ingredient, however the window around the Ingredients header read.
        val t0 = line.trim()
        if (t0.isNotEmpty() && t0[0].isLowerCase() &&
            Regex("\\)\\.\\s").containsMatchIn(t0)
        ) return true
        if (isNoise(line)) return true
        // Tips/notes prose: ingredient lines never run past ~12 words.
        if (line.split(Regex("\\s+")).size > 12) return true
        // Torn-off fragment ("Poultry at").
        val fragTokens = line.trim().split(Regex("\\s+"))
        if (fragTokens.size in 1..3 && parseAmount(fragTokens[0]) == null &&
            fragTokens[0].lowercase() !in UNITS && DANGLING_END_RE.containsMatchIn(line.trim())
        ) return true
        // A bare temperature ("350°F", "450 degrees F") is not an ingredient.
        if (TEMP_LINE_RE.containsMatchIn(line.trim())) return true
        // Tips/notes prose and its wrapped fragments ("You may prepare and bake",
        // "and freeze them, well wrapped", storage instructions).
        if (PROSE_LINE_RE.containsMatchIn(line.trim())) return true
        // A timing note swept in with the ingredients ("Standing time: 4 hrs."):
        // an ingredient line never carries a duration without a food unit.
        val wordTokens = line.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        val durationWords = setOf("hr", "hrs", "hour", "hours", "minute", "minutes", "min", "mins", "seconds", "overnight")
        if (wordTokens.any { it in durationWords } && wordTokens.none { it in UNITS }) return true
        // Description prose the scanner chopped into the grid ("These mints
        // make wonderful gifts from your kitchen at any time"): a long line
        // with no amount and no unit anywhere is prose, not an ingredient.
        val proseWords = line.trim().split(Regex("\\s+"))
        if (proseWords.size >= 8 && wordTokens.none { it in UNITS } && !Regex("\\d").containsMatchIn(line)) return true
        // Prose verbs: a line that talks ("outside is dry", "these make
        // wonderful gifts") is a sentence, not an ingredient.
        if (!LEADING_QTY_RE.containsMatchIn(line.trim()) &&
            !LEADING_QTY_ALT_RE.containsMatchIn(line.trim()) &&
            Regex("\\b(?:is|are|should|make|makes|will|can)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(line)
        ) return true
        // A sentence period on a line with no amount and no unit: prose.
        if (line.trim().endsWith(".") && !LEADING_QTY_RE.containsMatchIn(line.trim()) &&
            !LEADING_QTY_ALT_RE.containsMatchIn(line.trim()) &&
            wordTokens.none { it in UNITS } && line.trim().split(Regex("\\s+")).size >= 3
        ) return true
        // Known non-food phrases the scanner lifts out of notes.
        if (line.trim().trimEnd('.', ',').lowercase() in PHRASE_NOISE) return true
        // A bare number with nothing after it is a step number or a page
        // fragment, never an ingredient.
        val frag = line.trim().split(Regex("\\s+"))
        if (frag.size == 1 && parseAmount(frag[0]) != null) return true
        if (frag.size in 1..3 && parseAmount(frag[0]) == null &&
            frag[0].trimEnd(',', '.', ';').lowercase() !in UNITS
        ) {
            val last = frag.last().trimEnd(',', '.', ';', ':')
            if (last.length <= 2 || frag.joinToString(" ").length <= 4) return true
        }
        return false
    }

    // The shape signal for fallback paths: real ingredient lines carry a
    // measurement, either a leading amount (clean or OCR-garbled) or a unit
    // somewhere on the line. Prose, footer text and stray fragments carry
    // neither, so this is what keeps them out when the card gives up no
    // section headers.
    private fun isMeasurementLine(line: String): Boolean {
        val t = line.trim()
        if (LEADING_QTY_RE.containsMatchIn(t) || LEADING_QTY_ALT_RE.containsMatchIn(t)) return true
        if (GARBLED_QTY_LINE_RE.containsMatchIn(t)) return true
        val words = t.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        return words.any { it in UNITS }
    }

    // Does any known step verb appear anywhere on the line? A numbered step
    // ("1. Preheat the oven") is amount-led but still a step.
    private fun hasStepVerb(line: String): Boolean {
        return line.lowercase().split(Regex("[^a-z]+")).any { it in STEP_VERBS }
    }

    private fun looksLikeStep(line: String): Boolean {
        val lower = line.lowercase()
        // A bare one-word verb ("stir" from a page margin) is a fragment,
        // never a step; real steps carry at least two words.
        if (line.trim().split(Regex("\\s+")).size < 2) return false
        return (NUM_PREFIX_RE.containsMatchIn(line) && hasStepVerb(line)) ||
            (BULLET_PREFIX_RE.containsMatchIn(line) && line.length > 20) ||
            ACTION_VERBS.any { lower.startsWith(it) }
    }

    // Is this line the previous step's own continuation rather than a new
    // step or an ingredient? After a step that ends mid-clause ("...tie
    // with a clean string (see") anything un-numbered continues it; after a
    // closed step, a lowercase non-amount tail does, or a non-measurement
    // line left hanging on a function word ("For spice bag, place
    // cinnamon, allspice, and the"); and on a card whose steps carry
    // numbers, any un-numbered prose does ("Serve cider in mugs..." mid
    // step 2 of the hot-spiced-cider card). Nothing is invented: the line
    // itself must have been scanned.
    private fun isStepTail(line: String, prevStepText: String, numberedMode: Boolean): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.split(Regex("\\s+")).size < 2) return false
        if (STEP_NUM_RE.containsMatchIn(t)) return false
        if (BULLET_PREFIX_RE.containsMatchIn(t)) return false
        if (NUTRITION_RE.containsMatchIn(t)) return false
        if (!SENTENCE_END_RE.containsMatchIn(prevStepText.trimEnd())) return true
        // On a numbered card, a line with no leading amount is the current
        // step's continuation ("For spice bag, place cinnamon, allspice,
        // and the"); a leading amount only stays step text when a sentence
        // ends mid-line ("...cheesecloth. Bring"), otherwise it is a real
        // ingredient following the steps.
        val amountLed = LEADING_QTY_RE.containsMatchIn(t) ||
            LEADING_QTY_ALT_RE.containsMatchIn(t) || GARBLED_QTY_LINE_RE.containsMatchIn(t)
        if (numberedMode && (!amountLed || MID_STEP_SIGNAL_RE.containsMatchIn(t))) return true
        if (t.firstOrNull()?.isUpperCase() != true && !LEADING_QTY_RE.containsMatchIn(t) &&
            !LEADING_QTY_ALT_RE.containsMatchIn(t)
        ) return true
        if (!isMeasurementLine(t)) {
            val last = t.trimEnd(',', '.', ';', ':', '!', '?')
                .split(Regex("\\s+")).lastOrNull()?.lowercase() ?: return false
            return last in DANGLING_WORDS
        }
        return false
    }

    private fun stripStepPrefix(line: String): String {
        var s = line.trim()
        // "10 minutes. Meanwhile..." opens with a duration, not a step
        // number: the number is prose, never stripped.
        if (!DURATION_LED_RE.containsMatchIn(s)) s = s.replace(NUM_PREFIX_RE, "")
        s = s.replace(BULLET_PREFIX_RE, "")
        return s.trim().removePrefix(":").trim()
    }

    private val STEP_VERBS = setOf(
        "stir", "mix", "bake", "serve", "chill", "rest", "cool", "whisk", "fold", "cover",
        "simmer", "boil", "brown", "drain", "pour", "sprinkle", "garnish", "enjoy",
        "refrigerate", "freeze", "marinate", "broil", "melt", "beat", "knead", "roll",
        "spread", "layer", "season", "taste", "remove", "return", "repeat", "combine",
        "trim", "peel", "parboil", "reduce", "toast", "break", "pare",
        "add", "heat", "cook", "cut", "slice", "chop", "blend", "shape", "fill", "place",
        "arrange", "meanwhile", "preheat", "spray", "grease", "brush", "press", "pat",
        "dip", "coat", "toss", "wait", "let", "salt",
    )

    private val SENTENCE_END_RE = Regex("[.!?]$")

    // "10 minutes. Meanwhile..." opens with a duration, not a step number;
    // a duration-led line is the open step's continuation.
    private val DURATION_LED_RE = Regex(
        "^\\s*\\d+\\s*(?:to\\s*\\d+\\s*)?(?:min(?:ute)?s?|hours?|hrs?|seconds?|secs?)\\b",
        RegexOption.IGNORE_CASE,
    )
    // A sentence that ends mid-line ("...cheesecloth. Bring") marks a
    // wrapped instruction tail, not an ingredient.
    private val MID_STEP_SIGNAL_RE = Regex("\\.\\s+[A-Za-z]")
    // A numbered step marker carries its punctuation ("1." / "Step 2:");
    // "1 teaspoon whole cloves" is a quantity, not step one.
    private val STEP_NUM_RE = Regex(
        "^\\s*(?:step\\s*)?\\d{1,2}\\s*[.)\\]](?=\\s|$)",
        RegexOption.IGNORE_CASE,
    )
    // Nutrition footers arrive measurement-shaped ("9 mg sodium, 40 g
    // carbo.", "Daily Values: 0% vit. A, 4% vit. C") but are never
    // ingredients (Keith's hot-spiced-cider card).
    private val NUTRITION_RE = Regex(
        "(?i)\\b(?:nutrition facts|daily values|mg sodium|g carbo|g fiber|g pro\\.|cal\\.|chol\\.|total fat|vit\\.\\s*[a-z])",
    )
    private val DANGLING_WORDS = setOf(
        "the", "a", "an", "of", "and", "or", "with", "for", "to", "in", "on",
        "at", "by", "from", "into", "onto", "until", "about", "over", "before",
        "after",
    )

    private fun verbStart(text: String): Boolean {
        // The first word has to BE the verb: "cream the butter" is an
        // action, "creamy, delicious..." merely starts with the same letters.
        val first = (text.split(Regex("\\s+")).firstOrNull() ?: return false)
            .trim(',', '.', '!', '?', ':', ';').lowercase()
        if (ACTION_VERBS.any { first == it }) return true
        // OCR often glues one junk character onto the first word ("BBake",
        // "Bwhip"); the word minus its first letter still names the action.
        val trimmed = first.drop(1)
        return trimmed.length >= 3 && ACTION_VERBS.any { trimmed.startsWith(it) }
    }

    // OCR lines split steps mid-sentence and two-column cards scramble their
    // reading order. A line joins the previous step when it starts lowercase
    // or the previous step is still open (no sentence-ending punctuation); a
    // capitalized verb line starts a fresh step. Nothing is invented: only
    // lines the recognizer actually saw are stitched back together.
    private fun appendToSteps(out: MutableList<ParsedStep>, raw: String, forceJoin: Boolean = false) {
        val l = raw.trim()
        if (l.isEmpty() || isNoise(l)) return
        // A duration-led line ("10 minutes. Meanwhile...") is a wrapped
        // step tail, never a step numbered 10.
        val hasNum = NUM_PREFIX_RE.containsMatchIn(l) && !DURATION_LED_RE.containsMatchIn(l)
        val text = if (forceJoin) l.trim() else stripStepPrefix(l)
        if (text.isBlank()) return
        val words = text.split(Regex("\\s+"))
        val firstWord = words[0].trimEnd('.', '!', '?', ',').lowercase()
        val prev = out.lastOrNull()
        val prevOpen = prev != null && !SENTENCE_END_RE.containsMatchIn(prev.instructionText.trimEnd())
        if (words.size == 1 && !STEP_VERBS.contains(firstWord)) {
            // The wrapped tail of an open step ("...pepper and brown" /
            // "sugar.") is that step's ending, not junk to drop.
            if (prev != null && prevOpen) {
                out[out.size - 1] = ParsedStep(
                    (prev.instructionText + " " + text).trim(),
                    prev.timerSeconds ?: timerFor(text),
                )
            }
            return
        }
        val startsNew = !forceJoin && (hasNum || prev == null ||
            (words[0].firstOrNull()?.isUpperCase() == true && (!prevOpen || verbStart(text))))
        // Description prose ("creamy, delicious, and quick to prepare as
        // well.") that the layout sweep or a closed step leaves orphaned is
        // a sentence, never an instruction; a line joining an open step is
        // that step's continuation and stays.
        val leadWord = text.split(Regex("\\s+")).first()
            .trim(',', '.', '!', '?', ':', ';').lowercase()
        val fragmentStart = leadWord in setOf(
            "and", "or", "until", "for", "with", "to", "then", "before",
            "after", "about", "into", "over", "but", "at", "in", "on",
            "remaining", "optional", "reserved", "divided",
        )
        // The previous step being a real instruction ("...cook the chopped
        // stems," / "Rinse and drain mushrooms. Remove stems;") with no
        // sentence end makes a lowercase tail its continuation even when it
        // reads like prose ("reserve caps. Chop enough stems to make 1
        // cup."). Prose after prose ("creamy, delicious...") still drops.
        val prevRealInstruction = prev != null &&
            (hasStepVerb(prev.instructionText) || verbStart(prev.instructionText))
        if (isStepProse(text) && !forceJoin && !(prevOpen && (fragmentStart || prevRealInstruction))) return
        if (!startsNew && prev != null) {
            out[out.size - 1] = ParsedStep(
                (prev.instructionText + " " + text).trim(),
                prev.timerSeconds ?: timerFor(text),
            )
        } else {
            if (text.length > 240) {
                splitBlob(text).forEach { out.add(ParsedStep(it, timerFor(it))) }
            } else {
                out.add(ParsedStep(text, timerFor(text)))
            }
        }
    }

    // A paragraph the recognizer handed over as a single line still breaks
    // into steps: a numbered or "Step N." marker starts a fresh step, and a
    // long blob splits on sentence boundaries when a new sentence opens with
    // an action verb. Nothing is invented; only what OCR actually saw.
    private fun splitBlob(text: String): List<String> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        if (sentences.size < 3) return listOf(text)
        val out = mutableListOf<String>()
        var cur = ""
        for (s in sentences) {
            val startsAction = verbStart(s) || STEP_VERBS.any { s.lowercase().startsWith(it) }
            if (cur.length > 100 && startsAction) {
                out.add(cur.trim())
                cur = s
            } else {
                cur = if (cur.isEmpty()) s else "$cur $s"
            }
        }
        if (cur.isNotBlank()) out.add(cur.trim())
        return if (out.size > 1) out else listOf(text)
    }

    // A descriptive sentence, not an instruction: no action verb anywhere
    // ("creamy, delicious, and quick to prepare as well.") and no amount.
    private fun isStepProse(text: String): Boolean {
        if (verbStart(text)) return false
        if (hasStepVerb(text)) return false
        if (LEADING_QTY_RE.containsMatchIn(text) || LEADING_QTY_ALT_RE.containsMatchIn(text)) return false
        return text.split(Regex("\\s+")).size >= 3
    }

    // OCR wraps a line mid-word and keeps the trailing hyphen ("Combine
    // cream cheese, half-" / "and-half, cherry juice blend..."). The pair
    // joins so the line reads whole: the hyphen stays, a lowercase follow
    // joins with no space, and a section header never gets welded on.
    private fun joinHyphenLines(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            val next = lines.getOrNull(i + 1)
            if (l.endsWith("-") && next != null && next.length <= 120 &&
                !isIngHeader(next) && !isStepHeader(next) &&
                next.firstOrNull()?.isUpperCase() != true
            ) {
                out.add((l + next).take(300))
                i += 2
            } else {
                out.add(l)
                i++
            }
        }
        return out
    }

    // Keith's layout plan: use the recognizer's line boxes to find the real
    // Ingredients / Instructions blocks. The page is split into columns the
    // same way columnOrdered does, each column is read top to bottom, and a
    // section's lines are exactly the ones between its header and the next
    // header in the same column. Returns null when no section headers are
    // found, and the caller falls back to the text-sequence parser.
    private fun layoutSections(boxes: List<OcrLineBox>): Pair<List<String>, List<String>>? {
        val usable = boxes.filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
        if (usable.size < 6) return null
        val columns: List<List<OcrLineBox>> = columnSplit(usable)?.let { listOf(it.first, it.second) }
            ?: listOf(usable.sortedBy { it.top })
        val ingOut = mutableListOf<String>()
        val stepOut = mutableListOf<String>()
        for (col in columns) {
            val headers = col.mapIndexedNotNull { i, b ->
                when {
                    isIngHeader(b.text) -> i to 'i'
                    isStepHeader(b.text) -> i to 's'
                    isNoiseSectionHeader(b.text) -> i to 'n'
                    else -> null
                }
            }
            if (headers.isEmpty()) continue
            for ((pos, kind) in headers) {
                if (kind == 'n') continue
                // A section body ends at the next header of any kind: the
                // ingredients block must not run on into "Tips:" or "Good
                // served with:" text that shares its column.
                val stop = headers.firstOrNull { it.first > pos }?.first ?: col.size
                val body = col.subList(pos + 1, stop).map { it.text.trim() }
                if (kind == 'i') ingOut.addAll(body) else stepOut.addAll(body)
            }
        }
        return if (ingOut.isEmpty() && stepOut.isEmpty()) null else ingOut to stepOut
    }

    private fun parseSteps(lines: List<String>): List<ParsedStep> {
        val out = mutableListOf<ParsedStep>()
        for (raw in lines) {
            Regex("(?=(?:^|\\s)(?:step\\s*)?\\d{1,2}[.):]\\s+)", RegexOption.IGNORE_CASE)
                .split(raw)
                .forEach { appendToSteps(out, it) }
        }
        return out
    }

    // A tightly wrapped card can strand an ingredient's amount on its own line
    // right above the unit ("2" / "tablespoons creamy peanut butter"). A bare
    // number line directly above a line that opens with a unit word is that
    // ingredient's amount, rejoined so the quantity is not lost.
    private fun rejoinOrphanAmounts(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (l in lines) {
            val prev = out.lastOrNull()
            if (prev != null && isBareAmountLine(prev) && startsWithUnitWord(l)) {
                out[out.size - 1] = "$prev $l"
            } else {
                out.add(l)
            }
        }
        return out
    }

    private fun isBareAmountLine(line: String): Boolean {
        val t = line.trim()
        if (t.split(Regex("\\s+")).size != 1) return false
        return parseAmount(t) != null || AmountFixer.isGarbleAmount(t)
    }

    private fun startsWithUnitWord(line: String): Boolean {
        val first = line.trim().split(Regex("\\s+")).firstOrNull()?.trimEnd('.', ',')?.lowercase() ?: return false
        return first in UNITS
    }
}
