package com.trypod33.kitchlore.data

// Deterministic measurement repair, the pass that runs before any AI: the
// scanner garbles fraction glyphs on recipe cards into a tiny, well-known
// vocabulary ("%"/"%%", "1%", Vs/V2/V4, "h" mushed into a unit), so a
// garbled token sitting next to a unit word snaps to the right fraction in
// plain code. The AI cleanup pass only ever sees what is left over.
//
// Corrections a user makes by hand are stored as rules (correction_rules
// table) and win over the built-in defaults here, so the same garble only
// has to be fixed once.
object AmountFixer {

    // Stacked fraction glyphs the recognizer flattens into stray digits:
    // "3\u00BD cups" reads as "34 cups", "\u00BC cup sugar" as "4 cup sugar",
    // "2\u00BC tsp" as "24 tsp", and "3\u00BD" as "3h" on the same card
    // (Keith's dinner-rolls card, Nov 6 2026). A digit+4 glued to a measure
    // is never a real two-digit amount (34 cups of flour is not a recipe),
    // so the second digit is repaired to the fraction whose bottom numeral
    // it came from: tsp/tbsp measures read \u00BC, cup measures read \u00BD
    // (whole+\u00BD dominates cup quantities on home cards), and a bare "4"
    // on a singular "cup" is the \u00BC that lost its top numeral. The raw
    // text stays on the line for the reviewer, who fixes the rare card
    // where the guess is wrong.
    private val DIGIT_H_FRACTION_RE = Regex(
        "(\\d)[hH](?=\\s*(?:cups?|tbsps?|tsps?|tablespoons?|teaspoons?)\\b)",
    )
    private val DIGIT4_SPOON_RE = Regex(
        "(\\d)4(?=\\s*(?:tbsps?|tsps?|tablespoons?|teaspoons?)\\b)",
    )
    private val DIGIT4_CUP_RE = Regex("(\\d)4(?=\\s*cups?\\b)")
    private val BARE4_CUP_RE = Regex("(^|\\s)4(\\s+cup\\b)")

    /** Line-level fraction repairs, applied to ingredient and step text alike. */
    fun fixFractions(line: String): String {
        var s = DIGIT_H_FRACTION_RE.replace(line) { "${it.groupValues[1]}\u00BD" }
        s = DIGIT4_SPOON_RE.replace(s) { "${it.groupValues[1]}\u00BC" }
        s = DIGIT4_CUP_RE.replace(s) { "${it.groupValues[1]}\u00BD" }
        s = BARE4_CUP_RE.replace(s) { "${it.groupValues[1]}\u00BC${it.groupValues[2]}" }
        return s
    }

    // "%" and "%%" garbles, with or without a whole-number part ("1%", "1%%").
    private val PERCENT_GARBLE_RE = Regex("^\\d*%+\\d*$")
    private val VEE_RE = Regex("^v[s2348a]$", RegexOption.IGNORE_CASE)
    // The same glyphs read as a Y on some cards ("Y4 to Y2 cup packed brown
    // sugar" on Keith's mulled-cider card): snap them the same way.
    private val WHY_RE = Regex("^y[s2348a]$", RegexOption.IGNORE_CASE)
    private val H_UNIT_RE = Regex(
        "^h(cups?|tbsps?|tsps?|tablespoons?|teaspoons?)$",
        RegexOption.IGNORE_CASE,
    )

    fun isGarbleAmount(tok: String): Boolean {
        val b = tok.trimEnd('.', ',').lowercase()
        return PERCENT_GARBLE_RE.matches(b) || VEE_RE.matches(b) || WHY_RE.matches(b) ||
            b == "h" || H_UNIT_RE.matches(b)
    }

    // Ingredients the recipe cards in the wild usually measure as 1/3 cup,
    // for the bare "%" that could be 1/4, 1/3 or 1/2. Everything else
    // defaults to 1/4, and a hand correction overrides both.
    private val THIRD_HINTS = listOf("flour", "cocoa", "cornstarch", "shortening", "parmesan")

    /** Rewrites garbled amount tokens in one line to clean fraction strings. */
    fun fixLine(line: String, rules: Map<String, String> = emptyMap()): String {
        val toks = line.trim().split(Regex("\\s+"))
        if (toks.none { isGarbleAmount(it) }) return line
        val lower = toks.map { it.lowercase() }
        val out = toks.mapIndexed { i, tok ->
            val punct = if (tok.endsWith(".") || tok.endsWith(",")) tok.takeLast(1) else ""
            val bare = tok.trimEnd('.', ',')
            val key = bare.lowercase()
            val learned = rules[key]
            when {
                learned != null -> learned + punct
                key == "vs" -> "1/3$punct"
                key == "v2" -> "1/2$punct"
                VEE_RE.matches(key) -> when (key.drop(1)) {
                    "3", "s" -> "1/3$punct"
                    "2" -> "1/2$punct"
                    "4", "a" -> "1/4$punct"
                    "8" -> "1/8$punct"
                    else -> tok
                }
                WHY_RE.matches(key) -> when (key.drop(1)) {
                    "3", "s" -> "1/3$punct"
                    "2" -> "1/2$punct"
                    "4", "a" -> "1/4$punct"
                    "8" -> "1/8$punct"
                    else -> tok
                }
                key == "h" -> "1/2$punct"
                H_UNIT_RE.matches(key) -> tok // "hcup" = 1/2 cup, split in the parser
                PERCENT_GARBLE_RE.matches(key) -> {
                    val whole = key.substringBefore('%')
                    val frac = if (whole.isNotEmpty()) "1/2" else fractionFor(lower, i)
                    if (whole.isNotEmpty()) "$whole $frac$punct" else frac + punct
                }
                else -> tok
            }
        }
        return out.joinToString(" ")
    }

    private fun fractionFor(lowerToks: List<String>, garbleIdx: Int): String {
        val rest = lowerToks.filterIndexed { j, _ -> j != garbleIdx }.joinToString(" ")
        return if (THIRD_HINTS.any { rest.contains(it) }) "1/3" else "1/4"
    }
}
