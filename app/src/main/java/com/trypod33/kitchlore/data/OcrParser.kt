package com.trypod33.kitchlore.data

// Deterministic recipe parser (the issue #1 parser): raw OCR or document text
// -> structured recipe, no LLM involved.

data class ParsedIngredient(
    val name: String,
    val rawText: String,
    val amount: Double? = null,
    val unit: String? = null,
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
    // A line that is just an oven temperature, optionally with "bake/heat at".
    private val TEMP_LINE_RE = Regex(
        "^(?:bake|heat|preheat|cook|set)?\\s*(?:at\\s*)?\\d{2,4}\\s*(?:degrees?\\s*)?(?:°\\s*)?[FCfF]\\b\\.?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    private val NUM_PREFIX_RE = Regex("^\\s*(?:step\\s*)?(\\d+)[.)\\]]?\\s*", RegexOption.IGNORE_CASE)
    private val BULLET_PREFIX_RE = Regex("^\\s*[•*\\-–]\\s+")
    private val TIMER_RE = Regex(
        "(\\d+)\\s*(?:to\\s*\\d+\\s*)?(min(?:ute)?s?|hours?|hrs?|seconds?|secs?)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String, blocks: List<Pair<String, Int>> = emptyList()): ParsedRecipe {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ParsedRecipe("(untitled)")

        val servings =
            SERVINGS_RE.find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: SERVINGS_BEFORE_RE.find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: 1
        val prep = parseTimeLine(text, PREP_RE)
        val cook = parseTimeLine(text, COOK_RE)

        var title: String? = null
        if (blocks.isNotEmpty()) {
            val cand = blocks.maxByOrNull { it.second }?.first?.trim()
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
                    ingredients.add(parseIngredientLine(l))
                }
                steps.addAll(parseSteps(stepLines))
            }
            else -> {
                lines.forEachIndexed { i, l ->
                    if (i < startIdx || i == titleLineIdx || isNoise(l)) return@forEachIndexed
                    when {
                        looksLikeStep(l) -> steps.add(ParsedStep(stripStepPrefix(l), timerFor(stripStepPrefix(l))))
                        isIngredientNoise(l) -> {}
                        else -> ingredients.add(parseIngredientLine(l))
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

    fun parseIngredientLine(line: String): ParsedIngredient {
        val raw = line.trim().replace(BULLET_PREFIX_RE, "")
        val tokens = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }
        var idx = 0
        var amount: Double? = null
        var unit: String? = null
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
        if (amount != null && idx < tokens.size) {
            val cand = tokens[idx].trimEnd('.', ',').lowercase()
            if (cand in UNITS) {
                unit = tokens[idx].trimEnd('.', ',')
                idx++
            }
        }
        val name = tokens.drop(idx).joinToString(" ").trim()
        return ParsedIngredient(
            name = name.ifBlank { raw },
            rawText = raw,
            amount = amount,
            unit = unit,
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

    private fun isNoise(line: String): Boolean {
        if (SERVINGS_RE.containsMatchIn(line) || PREP_RE.containsMatchIn(line) || COOK_RE.containsMatchIn(line)) {
            return true
        }
        if (JUNK_LINE_RE.containsMatchIn(line)) return true
        val clean = line.trim().trimEnd('.', ' ').trim()
        if (clean.endsWith(":") && clean.none { it.isDigit() } && clean.length <= 25) return true
        if (clean.length <= 30 && clean.lowercase() in JUNK_EXACT) return true
        return false
    }

    // Rules that only apply to ingredient lines: steps can run long and can
    // legitimately contain temperatures ("Bake at 350°F for 30 minutes").
    private fun isIngredientNoise(line: String): Boolean {
        if (isNoise(line)) return true
        // Tips/notes prose: ingredient lines never run past ~12 words.
        if (line.split(Regex("\\s+")).size > 12) return true
        // A bare temperature ("350°F", "450 degrees F") is not an ingredient.
        if (TEMP_LINE_RE.containsMatchIn(line.trim())) return true
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

    private fun parseSteps(lines: List<String>): List<ParsedStep> {
        val out = mutableListOf<ParsedStep>()
        for (raw in lines) {
            val l = raw.trim()
            if (l.isEmpty() || isNoise(l)) continue
            val hasNum = NUM_PREFIX_RE.containsMatchIn(l)
            val text = stripStepPrefix(l)
            if (text.isBlank()) continue
            if (hasNum || out.isEmpty()) {
                out.add(ParsedStep(text, timerFor(text)))
            } else {
                val last = out.removeAt(out.size - 1)
                out.add(
                    ParsedStep(
                        (last.instructionText + " " + text).trim(),
                        last.timerSeconds ?: timerFor(text),
                    )
                )
            }
        }
        return out
    }
}
