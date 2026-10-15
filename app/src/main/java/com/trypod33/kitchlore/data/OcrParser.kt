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
        "pulse", "process", "whirl", "liquefy", "puree", "microwave",
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
    private val PROSE_LINE_RE = Regex(
        "^(?:you may|tip|tips|note|variation|make ahead|storage|to serve|serving suggestion|" +
            "store|stored|keeps?|sealed|airtight|wrap(?:ped)?|and|or|until|with|for|in)\\b",
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
        "^\\s*(?:[hvV]\\s*\\d?\\s*)?(?:cups?|tbsps?|tsps?|tablespoons?|teaspoons?|qts?|qts\\.|oz|ounces?|lbs?|pounds?|cloves?|slices?)\\b|^(?:Vs|V\\d)\\b|^\\d+\\s*[/%%]\\s*\\d?|^[hV]\\b",
        RegexOption.IGNORE_CASE,
    )

    private val NUM_PREFIX_RE = Regex("^\\s*(?:step\\s*)?(\\d+)[.)\\]]?\\s*", RegexOption.IGNORE_CASE)
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
    private fun columnOrdered(boxes: List<OcrLineBox>): List<String>? {
        val usable = boxes.filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
        if (usable.size < 6) return null
        val centers = usable.map { (it.left + it.right) / 2.0 }.sorted()
        var bestIdx = -1
        var bestGap = 0.0
        for (i in 0 until centers.size - 1) {
            val gap = centers[i + 1] - centers[i]
            if (gap > bestGap) {
                bestGap = gap
                bestIdx = i
            }
        }
        val span = (usable.maxOf { it.right } - usable.minOf { it.left }).toDouble()
        if (span <= 0 || bestGap < span * 0.25) return null
        val split = (centers[bestIdx] + centers[bestIdx + 1]) / 2.0
        val leftCol = usable.filter { (it.left + it.right) / 2.0 <= split }.sortedBy { it.top }
        val rightCol = usable.filter { (it.left + it.right) / 2.0 > split }.sortedBy { it.top }
        if (leftCol.size < 3 || rightCol.size < 3) return null
        // True side-by-side: the columns have to share vertical range, not
        // stack like two pages.
        val overlap = minOf(leftCol.maxOf { it.bottom }, rightCol.maxOf { it.bottom }) -
            maxOf(leftCol.minOf { it.top }, rightCol.minOf { it.top })
        val shorter = minOf(
            leftCol.maxOf { it.bottom } - leftCol.minOf { it.top },
            rightCol.maxOf { it.bottom } - rightCol.minOf { it.top },
        ).toDouble()
        if (overlap <= 0 || overlap < shorter * 0.3) return null
        return (leftCol + rightCol).map { it.text.trim() }.filter { it.isNotEmpty() }
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
        if (lines.isEmpty()) return ParsedRecipe("(untitled)")

        val servings =
            SERVINGS_RE.find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: SERVINGS_BEFORE_RE.find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: 1
        val prep = parseTimeLine(text, PREP_RE)
        val cook = parseTimeLine(text, COOK_RE)

        var title: String? = null
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
                    !LEADING_QTY_ALT_RE.containsMatchIn(it) && !it.trimEnd().endsWith(":")
            }
        }

        val titleLineIdx = lines.indexOfFirst { it.equals(title, ignoreCase = true) }
        val startIdx = if (titleLineIdx >= 0) titleLineIdx + 1 else 0

        val ingIdx = lines.indexOfFirst { SECTION_ING_RE.matches(it) }
        var stepIdx = -1
        if (ingIdx >= 0) {
            val rel = lines.subList(ingIdx + 1, lines.size).indexOfFirst { SECTION_STEP_RE.matches(it) }
            if (rel >= 0) stepIdx = ingIdx + 1 + rel
        }

        val ingredients = mutableListOf<ParsedIngredient>()
        val steps = mutableListOf<ParsedStep>()

        when {
            ingIdx >= 0 -> {
                val ingLines = lines.subList(ingIdx + 1, if (stepIdx >= 0) stepIdx else lines.size)
                val stepLines = if (stepIdx >= 0) lines.subList(stepIdx + 1, lines.size) else emptyList()
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
            }
            else -> {
                var lastWasStep = false
                lines.forEachIndexed { i, l ->
                    if (i < startIdx || i == titleLineIdx || isNoise(l)) return@forEachIndexed
                    when {
                        looksLikeStep(l) -> {
                            appendToSteps(steps, l)
                            lastWasStep = true
                        }
                        isIngredientNoise(l) -> {}
                        // A lowercase tail with no amount straight after an
                        // open step is that step's continuation, not an
                        // ingredient.
                        lastWasStep &&
                            steps.isNotEmpty() &&
                            l.trim().firstOrNull()?.isUpperCase() != true &&
                            !LEADING_QTY_RE.containsMatchIn(l.trim()) &&
                            !LEADING_QTY_ALT_RE.containsMatchIn(l.trim()) &&
                            !SENTENCE_END_RE.containsMatchIn(steps.last().instructionText.trimEnd()) -> {
                            appendToSteps(steps, l)
                            lastWasStep = true
                        }
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
                        else -> {
                            ingredients.add(parseIngredientLine(l, rules))
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
                bare == "|" -> listOf("1")
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
        // Section headers ("RECIPES") and stray one-word prose fragments.
        if (clean.none { it.isDigit() } &&
            clean.trimEnd(',', '.', ';', ':', '!', '?').lowercase().let { w ->
                w.split(Regex("\\s+")).size == 1 && (w in PAGE_HEADERS || w in NOISE_WORDS)
            }
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
        // A truncated fragment ("oth,", "glass j") is the scanner chewing a
        // wrapped line, never an ingredient.
        val frag = line.trim().split(Regex("\\s+"))
        if (frag.size in 1..3 && parseAmount(frag[0]) == null &&
            frag[0].trimEnd(',', '.', ';').lowercase() !in UNITS
        ) {
            val last = frag.last().trimEnd(',', '.', ';', ':')
            if (last.length <= 2 || frag.joinToString(" ").length <= 4) return true
        }
        return false
    }

    private fun looksLikeStep(line: String): Boolean {
        val lower = line.lowercase()
        return (NUM_PREFIX_RE.containsMatchIn(line) && line.length > 12) ||
            (BULLET_PREFIX_RE.containsMatchIn(line) && line.length > 20) ||
            ACTION_VERBS.any { lower.startsWith(it) }
    }

    private fun stripStepPrefix(line: String): String {
        var s = line.trim().replace(NUM_PREFIX_RE, "")
        s = s.replace(BULLET_PREFIX_RE, "")
        return s.trim().removePrefix(":").trim()
    }

    private val STEP_VERBS = setOf(
        "stir", "mix", "bake", "serve", "chill", "rest", "cool", "whisk", "fold", "cover",
        "simmer", "boil", "brown", "drain", "pour", "sprinkle", "garnish", "enjoy",
        "refrigerate", "freeze", "marinate", "broil", "melt", "beat", "knead", "roll",
        "spread", "layer", "season", "taste", "remove", "return", "repeat", "combine",
        "add", "heat", "cook", "cut", "slice", "chop", "blend", "shape", "fill", "place",
        "arrange", "meanwhile", "preheat", "spray", "grease", "brush", "press", "pat",
        "dip", "coat", "toss", "wait", "let", "salt",
    )

    private val SENTENCE_END_RE = Regex("[.!?]$")

    private fun verbStart(text: String): Boolean {
        val lower = text.lowercase()
        if (ACTION_VERBS.any { lower.startsWith(it) }) return true
        // OCR often glues one junk character onto the first word ("BBake",
        // "Bwhip"); the word minus its first letter still names the action.
        val first = lower.split(Regex("\\s+")).firstOrNull() ?: return false
        val trimmed = first.drop(1)
        return trimmed.length >= 3 && ACTION_VERBS.any { trimmed.startsWith(it) }
    }

    // OCR lines split steps mid-sentence and two-column cards scramble their
    // reading order. A line joins the previous step when it starts lowercase
    // or the previous step is still open (no sentence-ending punctuation); a
    // capitalized verb line starts a fresh step. Nothing is invented: only
    // lines the recognizer actually saw are stitched back together.
    private fun appendToSteps(out: MutableList<ParsedStep>, raw: String) {
        val l = raw.trim()
        if (l.isEmpty() || isNoise(l)) return
        val hasNum = NUM_PREFIX_RE.containsMatchIn(l)
        val text = stripStepPrefix(l)
        if (text.isBlank()) return
        val words = text.split(Regex("\\s+"))
        val firstWord = words[0].trimEnd('.', '!', '?', ',').lowercase()
        if (words.size == 1 && !STEP_VERBS.contains(firstWord)) return
        val prev = out.lastOrNull()
        val prevOpen = prev != null && !SENTENCE_END_RE.containsMatchIn(prev.instructionText.trimEnd())
        val startsNew = hasNum || prev == null ||
            (words[0].firstOrNull()?.isUpperCase() == true && (!prevOpen || verbStart(text)))
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
