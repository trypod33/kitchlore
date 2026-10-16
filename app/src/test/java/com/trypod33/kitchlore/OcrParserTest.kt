package com.trypod33.kitchlore

import com.trypod33.kitchlore.data.OcrParser
import com.trypod33.kitchlore.data.OcrParser.OcrLineBox
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrParserTest {
    private fun box(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrLineBox(text, left, top, right, bottom)

    // Keith's Great American Recipes onions card: two tight columns, left
    // column wraps nearly into the gutter, "Preparation:" sits higher on the
    // page than the ingredients header, and step 1 wraps onto a line reading
    // "ingredients for sauce." The old parser made that a false ingredients
    // header and swallowed steps 2-7 into the ingredient list.
    @Test
    fun twoColumnCardKeepsStepsOutOfIngredients() {
        val boxes = listOf(
            // Left column (x 130-820)
            box("Preparation time: 15 min.", 240, 90, 640, 130),
            box("Cooking time: 20 to 25 min.", 240, 140, 700, 180),
            box("For 4 servings you will need:", 130, 270, 700, 310),
            box("1 lb. small white onions", 180, 320, 620, 360),
            box("Sauce:", 180, 400, 300, 440),
            box("\u00BC cup white wine vinegar", 180, 450, 640, 490),
            box("\u00BC cup white wine", 180, 495, 500, 535),
            box("\u00BD cup chunky tomato sauce or", 180, 540, 815, 580),
            box("stewed tomatoes", 230, 585, 480, 625),
            box("3 Tbsp. brown sugar", 180, 630, 500, 670),
            box("1 tsp. dry mustard", 180, 675, 470, 715),
            box("\u00BD tsp. ground cloves", 180, 720, 500, 760),
            box("\u00BD tsp. ground ginger", 180, 765, 500, 805),
            box("1 Tbsp. olive oil or other oil", 180, 810, 640, 850),
            box("Dash hot pepper sauce", 180, 855, 520, 895),
            box("EASY & TASTY", 230, 1150, 600, 1250),
            box("Tips:", 130, 1400, 220, 1440),
            box("These onions will keep in the re-", 130, 1445, 620, 1485),
            box("frigerator for up to a week.", 130, 1490, 520, 1530),
            box("\u00A9MCMLXXIX GREAT AMERICAN RECIPES\u00AE ALL RIGHTS RESERVED", 350, 1700, 1500, 1740),
            // Right column (x 900-1700), starts higher than the ingredients header
            box("Microwave cooking: see other side", 1355, 90, 1780, 130),
            box("Preparation:", 900, 250, 1130, 290),
            box("1 In a saucepan, combine all", 900, 300, 1500, 340),
            box("ingredients for sauce.", 900, 345, 1290, 385),
            box("2 Bring to a boil. Boil for 10", 900, 390, 1520, 430),
            box("minutes or until just thickened.", 940, 435, 1560, 475),
            box("3 Trim and peel onions. Parboil", 900, 480, 1530, 520),
            box("for 6 minutes in salted water. Drain.", 940, 525, 1600, 565),
            box("4 Place drained onion in a sauce-", 900, 570, 1540, 610),
            box("pan. Pour barbecue sauce over.", 940, 615, 1520, 655),
            box("5 Bring to a boil. Reduce heat.", 900, 660, 1500, 700),
            box("6 Simmer over low to medium heat", 900, 705, 1560, 745),
            box("for 10 to 15 minutes or until onions", 940, 750, 1620, 790),
            box("are soft but not mushy.", 940, 795, 1330, 835),
            box("7 Serve as a side dish with any", 900, 840, 1500, 880),
            box("meat or poultry. Or let cool com-", 940, 885, 1540, 925),
            box("pletely and serve as a relish or", 940, 930, 1500, 970),
            box("cocktail condiment.", 940, 975, 1290, 1015),
        )
        val text = boxes.joinToString("\n") { it.text }
        val parsed = OcrParser.parse(text, boxes = boxes)

        val ingText = parsed.ingredients.joinToString(" | ") { it.rawText.ifBlank { it.name } }
        val stepText = parsed.steps.joinToString(" | ") { it.instructionText }

        assertFalse("steps leaked into ingredients: $ingText", ingText.contains("Bring to a boil"))
        assertFalse("steps leaked into ingredients: $ingText", ingText.contains("Simmer over low"))
        assertFalse("steps leaked into ingredients: $ingText", ingText.contains("Serve as a side dish"))
        assertFalse("false header leaked: $ingText", ingText.contains("ingredients for sauce"))
        assertTrue("vinegar missing: $ingText", ingText.contains("white wine vinegar"))
        assertTrue("brown sugar missing: $ingText", ingText.contains("brown sugar"))
        assertTrue("onions missing: $ingText", ingText.contains("small white onions"))
        assertTrue("step 2 missing: $stepText", stepText.contains("Bring to a boil"))
        assertTrue("step 6 missing: $stepText", stepText.contains("Simmer over low"))
        assertTrue("step 7 missing: $stepText", stepText.contains("Serve as a side dish"))
        assertTrue("step 3 trim missing: $stepText", stepText.contains("Trim and peel"))
    }

    // Bare single-column header card must keep working after the header
    // tightening.
    @Test
    fun bareHeaderCardStillParses() {
        val boxes = listOf(
            box("Lemon Bars", 100, 60, 500, 120),
            box("Ingredients:", 100, 200, 320, 240),
            box("1 cup flour", 100, 250, 350, 290),
            box("2 eggs", 100, 300, 250, 340),
            box("3/4 cup sugar", 100, 350, 340, 390),
            box("1 pkg (8 oz.) cream cheese", 100, 400, 460, 440),
            box("Directions:", 100, 500, 300, 540),
            box("1. Mix the crust ingredients and press into a pan.", 100, 550, 700, 590),
            box("2. Bake for 25 minutes at 350F.", 100, 600, 620, 640),
            box("3. Chill before serving.", 100, 650, 500, 690),
        )
        val text = boxes.joinToString("\n") { it.text }
        val parsed = OcrParser.parse(text, boxes = boxes)
        val ingText = parsed.ingredients.joinToString(" | ") { it.rawText.ifBlank { it.name } }
        val stepText = parsed.steps.joinToString(" | ") { it.instructionText }
        assertTrue("cream cheese missing: $ingText", ingText.contains("cream cheese"))
        assertFalse("steps leaked: $ingText", ingText.contains("Bake for"))
        assertTrue("bake step missing: $stepText", stepText.contains("Bake for 25 minutes"))
    }
}
