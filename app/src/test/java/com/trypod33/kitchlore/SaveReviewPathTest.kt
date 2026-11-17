package com.trypod33.kitchlore

import com.trypod33.kitchlore.data.OcrParser
import com.trypod33.kitchlore.data.OcrParser.OcrLineBox
import org.junit.Test

// Mimics ReviewImportScreen.saveReview's non-DB work on the dinner-rolls
// card's real review lines: re-parse each display ingredient line, timerFor
// each step. Any throw here leaves the review screen stuck on Save.
class SaveReviewPathTest {
    private fun box(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrLineBox(text, left, top, right, bottom)

    @Test
    fun reviewLinesNeverThrow() {
        val boxes = listOf(
            box("temanade", 880, 60, 1320, 210),
            box("Ingredients", 150, 260, 420, 310),
            box("O 34 cups all-purpose flour", 150, 330, 640, 375),
            box("O packet (24 tsp)", 150, 385, 560, 430),
            box("active dry yeast", 190, 435, 500, 480),
            box("1 cup warm milk (110\u00B0F)", 150, 490, 620, 535),
            box("O 4 cup sugar", 150, 545, 420, 590),
            box("O 4 cup butter, melted", 150, 600, 540, 645),
            box("O 1 large egg", 150, 655, 400, 700),
            box("O 1 tsp salt", 150, 710, 380, 755),
            box("Instructions", 830, 260, 1120, 310),
            box("1 In a large bowl, dissolve the yeast", 830, 330, 1300, 375),
            box("and sugar in the warm milk. Let sit 5 minutes until foamy.", 830, 380, 1250, 475),
            box("2 Add melted butter, egg, and salt.", 830, 490, 1290, 535),
            box("Stir in 3h cups flour add a", 830, 550, 1240, 595),
            box("little more if needed) and mix", 830, 600, 1240, 645),
            box("until a soft dough forms.", 830, 650, 1180, 695),
            box("Knead the dough for 5-7", 830, 710, 1140, 755),
            box("minutes until smooth.", 830, 760, 1120, 805),
            box("A Place in a greased bowl,", 830, 820, 1230, 865),
            box("cover, and let rise 1 hour, or", 830, 870, 1230, 915),
            box("until doubled. and place in a", 830, 920, 1230, 965),
            box("greased 9x13 pan.", 830, 970, 1100, 1015),
            box("Cover and let rise 30-45", 830, 1030, 1170, 1075),
            box("minutes.", 830, 1080, 980, 1125),
            box("Bake at 350\u00B0F for 18-22", 830, 1140, 1210, 1185),
            box("minutes, until golden brown.", 830, 1190, 1250, 1235),
            box("Brush with melted butter", 830, 1250, 1170, 1295),
            box("while warm.", 830, 1300, 1020, 1345),
        )
        val text = boxes.joinToString("\n") { it.text }
        val r = OcrParser.parse(text, boxes = boxes)
        println("title=" + r.title + " ings=" + r.ingredients.size + " steps=" + r.steps.size)
        for (ing in r.ingredients) {
            val p = OcrParser.parseIngredientLine(ing.rawText)
            println("ING raw=[" + ing.rawText + "] name=[" + p.name + "] amt=" + p.amount + " unit=" + p.unit)
        }
        for (st in r.steps) {
            val t = OcrParser.timerFor(st.instructionText)
            println("STEP [" + st.instructionText + "] timer=" + t)
        }
    }
}
