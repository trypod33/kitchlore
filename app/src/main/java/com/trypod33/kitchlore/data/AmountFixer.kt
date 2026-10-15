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

    // "%" and "%%" garbles, with or without a whole-number part ("1%", "1%%").
    private val PERCENT_GARBLE_RE = Regex("^\\d*%+\\d*$")
    private val VEE_RE = Regex("^v[s2348a]$", RegexOption.IGNORE_CASE)
    private val H_UNIT_RE = Regex(
        "^h(cups?|tbsps?|tsps?|tablespoons?|teaspoons?)$",
        RegexOption.IGNORE_CASE,
    )

    fun isGarbleAmount(tok: String): Boolean {
        val b = tok.trimEnd('.', ',').lowercase()
        return PERCENT_GARBLE_RE.matches(b) || VEE_RE.matches(b) || b == "h" ||
            H_UNIT_RE.matches(b)
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
