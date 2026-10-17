package com.trypod33.kitchlore

import com.trypod33.kitchlore.data.OcrLexicon
import com.trypod33.kitchlore.data.OcrParser
import com.trypod33.kitchlore.data.OcrParser.OcrLineBox
import com.trypod33.kitchlore.data.RegionBlock
import com.trypod33.kitchlore.data.RegionKind
import com.trypod33.kitchlore.data.RegionLabeler
import org.junit.Assert.assertEquals
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
    // Keith's Peachtree Street Chicken Salad card (Great American Recipes,
    // Salads and Green Dishes Card 14): the recipe text sits in two columns
    // on the right half of the card while the title and photo caption sit
    // far left. The old split took the WIDEST start-edge gap, which is the
    // gap between that far-left title/caption cluster and everything else,
    // not the real ingredient/steps gutter, so both columns scrambled
    // together into one ingredient list and the steps section came out
    // empty. The split now scores candidate gaps and keeps the one that
    // leaves two substantial side-by-side columns.
    @Test
    fun chickenSaladCardReadsBothColumns() {
        val boxes = listOf(
            // Far-left title and photo caption
            box("Peachtree Street", 55, 60, 560, 125),
            box("Chicken Salad", 55, 130, 470, 195),
            box("GREAT AMERICAN", 1000, 95, 1400, 135),
            box("RECIPES", 1060, 150, 1330, 180),
            box("Salads and", 1310, 70, 1660, 105),
            box("Green Dishes Card 14", 1310, 110, 1930, 145),
            box("Group", 1790, 75, 1905, 105),
            box("2", 2140, 55, 2210, 140),
            box("Preparation time: 10 min.", 1310, 230, 1790, 258),
            box("Simmering time: 20 min.", 1310, 262, 1770, 290),
            box("Microwave cooking:", 1970, 225, 2260, 252),
            box("see other side", 2045, 258, 2260, 285),
            // Ingredient column (x ~1245-1880)
            box("For 4 servings you will need:", 1245, 365, 1830, 395),
            box("3 chicken breasts", 1285, 405, 1650, 435),
            box("\u00BD cup pecans", 1285, 440, 1560, 470),
            box("2 medium peaches, ripe", 1285, 475, 1730, 505),
            box("4 Tbsp. mayonnaise", 1285, 510, 1640, 540),
            box("2 Tbsp. plain yogurt or sour cream", 1285, 545, 1880, 575),
            box("2 tsp. lemon juice", 1285, 580, 1590, 610),
            box("\u00BE tsp. salt", 1285, 615, 1490, 645),
            box("\u00BC tsp. pepper", 1285, 650, 1520, 680),
            box("1 tsp. brown sugar", 1285, 685, 1570, 715),
            box("DELICIOUS", 1330, 880, 1810, 975),
            box("Tips:", 1245, 1080, 1330, 1105),
            box("Leftover chicken is often too dry", 1245, 1110, 1800, 1135),
            box("and too chilled to make a good", 1245, 1140, 1770, 1165),
            box("addition to a great chicken salad.", 1245, 1170, 1780, 1195),
            box("A whole broiler-fryer can be sim-", 1245, 1200, 1780, 1225),
            box("mered, covered, for about 30 min-", 1245, 1230, 1790, 1255),
            box("utes in a small amount of water.", 1245, 1260, 1750, 1285),
            box("Place breast down so it self-bastes.", 1245, 1290, 1795, 1315),
            box("Tender chunks of chicken and diced, fragrant peaches are tossed with", 55, 1385, 1210, 1412),
            box("pecans and a lemon-laced mayonnaise. A delicious summer luncheon.", 55, 1417, 1130, 1444),
            // Steps column (x ~1900-2445)
            box("Preparation:", 1900, 360, 2140, 390),
            box("1 Place chicken in a saucepan,", 1900, 395, 2440, 425),
            box("breast side down, in 1\u00BD inches of", 1900, 430, 2430, 458),
            box("water. Cover.", 1900, 463, 2130, 491),
            box("2 Simmer for 20 minutes until", 1900, 496, 2390, 524),
            box("chicken is fork-tender. Cool.", 1900, 529, 2360, 557),
            box("3 Cut into bite-size pieces.", 1900, 562, 2320, 590),
            box("4 During this time, toast pecans at", 1900, 595, 2440, 623),
            box("350\u00B0F for 10 minutes. Remove.", 1900, 628, 2330, 656),
            box("Coarsely break up.", 1900, 661, 2190, 689),
            box("5 Pare peaches, if desired. Cut into", 1900, 694, 2440, 722),
            box("\u00BE-inch pieces.", 1900, 727, 2140, 755),
            box("6 In a small bowl, combine the", 1900, 760, 2380, 788),
            box("mayonnaise, yogurt or sour cream,", 1900, 793, 2445, 821),
            box("lemon juice, salt, pepper and brown", 1900, 826, 2440, 854),
            box("sugar.", 1900, 859, 2020, 887),
            box("7 Toss gently with the chicken,", 1900, 892, 2410, 920),
            box("pecans and peaches. Serve on a", 1900, 925, 2400, 953),
            box("green leaf lettuce.", 1900, 958, 2190, 986),
            box("Good served with:", 1900, 1230, 2260, 1258),
            box("Honey buns or French buns and", 1900, 1263, 2390, 1291),
            box("iced tea. No dessert is needed!", 1900, 1296, 2380, 1324),
            box("\u00A9MCMXXVIII GREAT AMERICAN RECIPES\u00AE ALL RIGHTS RESERVED", 1390, 1405, 2320, 1432),
        )
        val text = boxes.joinToString("\n") { it.text }
        val parsed = OcrParser.parse(
            text,
            blocks = listOf("Peachtree Street" to 65, "Chicken Salad" to 65, "DELICIOUS" to 95),
            boxes = boxes,
        )

        val ingText = parsed.ingredients.joinToString(" | ") { it.rawText.ifBlank { it.name } }
        val stepText = parsed.steps.joinToString(" | ") { it.instructionText }

        assertTrue("title wrong: ${parsed.title}", parsed.title.contains("Peachtree"))
        assertTrue("servings wrong: ${parsed.baseServings}", parsed.baseServings == 4)
        assertTrue("chicken breasts missing: $ingText", ingText.contains("chicken breasts"))
        assertTrue("pecans missing: $ingText", ingText.contains("pecans"))
        assertTrue("brown sugar missing: $ingText", ingText.contains("brown sugar"))
        assertTrue("lemon juice missing: $ingText", ingText.contains("lemon juice"))
        assertFalse("steps leaked into ingredients: $ingText", ingText.contains("saucepan"))
        assertFalse("steps leaked into ingredients: $ingText", ingText.contains("Simmer"))
        assertFalse("steps leaked into ingredients: $ingText", ingText.contains("Coarsely"))
        assertFalse("tips leaked into ingredients: $ingText", ingText.contains("Leftover chicken"))
        assertFalse("caption leaked into ingredients: $ingText", ingText.contains("Tender chunks"))
        assertFalse("served-with leaked into ingredients: $ingText", ingText.contains("Honey buns"))
        assertTrue("step 1 missing: $stepText", stepText.contains("Place chicken in a saucepan"))
        assertTrue("step 2 missing: $stepText", stepText.contains("Simmer for 20 minutes"))
        assertTrue("step 4 missing: $stepText", stepText.contains("toast pecans"))
        assertTrue("step 7 missing: $stepText", stepText.contains("Toss gently"))
        assertFalse("served-with leaked into steps: $stepText", stepText.contains("Honey buns"))
        assertFalse("tips leaked into steps: $stepText", stepText.contains("Leftover chicken"))
    }
    // Keith's stuffed mushrooms cookbook card (Better Homes style): a
    // single-column card with NO Ingredients header - the ingredient list
    // sits under "PREP: 25 MINUTES BAKE: 8 MINUTES" and the numbered steps
    // follow. The old parser split the card into fake columns (the gap
    // between the quantity column and the wrapped text), threw away the
    // wrapped tails of the numbered steps as prose ("reserve caps. Chop
    // enough stems...", "green onions, and garlic in margarine or butter
    // till"), and picked the title from body text. The tallest OCR line is
    // the title, the height-vs-gap rule keeps one-column cards whole, and a
    // lowercase tail of an open numbered step joins it.
    @Test
    fun stuffedMushroomsCookbookCardParses() {
        fun b(text: String, l: Int, t: Int, r: Int, bo: Int) =
            com.trypod33.kitchlore.data.OcrParser.OcrLineBox(text, l, t, r, bo)
        val boxes = listOf(
            b("STUFFED MUSHROOMS", 700, 40, 1850, 264),
            b("PREP: 25 MINUTES BAKE: 8 MINUTES", 52, 267, 1426, 388),
            b("Oven 425\u00B0", 1997, 393, 2491, 482),
            b("24 large fresh mushrooms, 1\u00BD to 2 inches", 164, 496, 2420, 652),
            b("in diameter", 481, 658, 1606, 772),
            b("\u00BC cup sliced green onions (2)", 188, 802, 1788, 940),
            b("1 clove garlic, minced", 187, 970, 1619, 1078),
            b("\u00BC cup margarine or butter", 186, 1108, 1744, 1226),
            b("\u2153 cup fine dry bread crumbs", 188, 1236, 1744, 1370),
            b("\u00BD cup shredded cheddar or crumbled blue", 188, 1381, 2462, 1505),
            b("cheese (2 ounces)", 473, 1529, 1410, 1637),
            b("1. Rinse and drain mushrooms. Remove stems;", 46, 1815, 2471, 1930),
            b("reserve caps. Chop enough stems to make 1 cup.", 37, 1965, 2429, 2089),
            b("2. In a medium saucepan cook the chopped stems,", 33, 2099, 2469, 2230),
            b("green onions, and garlic in margarine or butter till", 30, 2248, 2473, 2371),
            b("tender. Stir in bread crumbs and cheese. Spoon", 29, 2389, 2474, 2498),
            b("crumb mixture into mushroom caps. Arrange", 27, 2535, 2471, 2644),
            b("mushrooms in a 15\u00D710\u00D71-inch baking pan. Bake in", 25, 2677, 2473, 2784),
            b("a 425\u00B0 oven for 8 to 10 minutes or till heated", 23, 2824, 2473, 2927),
            b("through. Makes 24 mushrooms.", 19, 2965, 1545, 3067),
        )
        val text = boxes.joinToString("\n") { it.text }
        val parsed = OcrParser.parse(
            text,
            blocks = listOf(
                "STUFFED MUSHROOMS" to 224,
                "PREP: 25 MINUTES BAKE: 8 MINUTES" to 121,
                "Oven 425\u00B0" to 89,
                "24 large fresh mushrooms, 1\u00BD to 2 inches" to 156,
            ),
            boxes = boxes,
        )

        val ingText = parsed.ingredients.joinToString(" | ") { it.rawText.ifBlank { it.name } }
        val stepText = parsed.steps.joinToString(" | ") { it.instructionText }

        assertTrue("title wrong: ${parsed.title}", parsed.title.lowercase().contains("stuffed"))
        assertTrue("servings wrong: ${parsed.baseServings}", parsed.baseServings == 24)
        assertTrue("prep wrong: ${parsed.prepTimeMinutes}", parsed.prepTimeMinutes == 25)
        assertTrue("bake wrong: ${parsed.cookTimeMinutes}", parsed.cookTimeMinutes == 8)
        assertTrue("mushrooms missing: $ingText", ingText.contains("large fresh mushrooms"))
        assertTrue("green onions missing: $ingText", ingText.contains("green onions"))
        assertTrue("garlic missing: $ingText", ingText.contains("garlic"))
        assertTrue("margarine missing: $ingText", ingText.contains("margarine"))
        assertTrue("bread crumbs missing: $ingText", ingText.contains("bread crumbs"))
        assertTrue("cheese missing: $ingText", ingText.contains("cheddar"))
        assertFalse("step text leaked into ingredients: $ingText", ingText.contains("Rinse"))
        assertFalse("step text leaked into ingredients: $ingText", ingText.contains("Arrange"))
        assertFalse("title leaked into ingredients: $ingText", ingText.contains("STUFFED"))
        assertTrue("expected 6 ingredients, got ${parsed.ingredients.size}: $ingText", parsed.ingredients.size == 6)
        assertTrue("step 1 tail missing: $stepText", stepText.contains("reserve caps"))
        assertTrue("step 1 tail missing: $stepText", stepText.contains("Chop enough stems"))
        assertTrue("step 2 tail missing: $stepText", stepText.contains("green onions, and garlic"))
        assertTrue("step 2 tail missing: $stepText", stepText.contains("Arrange mushrooms"))
        assertTrue("step 2 tail missing: $stepText", stepText.contains("Bake in a 425"))
        assertFalse("tips in steps: $stepText", stepText.contains("PREP:"))
    }

    // Keith's mulled-cider card: the scanner read the fraction glyphs as
    // "Y4" and "Y2", so "1/4 to 1/2 cup packed brown sugar" arrived garbled.
    @Test
    fun yFractionGarblesSnapToFractions() {
        val parsed = OcrParser.parseIngredientLine("Y4 to Y2 cup packed brown sugar")
        assertTrue("amount wrong: ${parsed.amount}", parsed.amount == 0.25)
        assertTrue("unit wrong: ${parsed.unit}", parsed.unit == "cup")
        assertTrue("name wrong: ${parsed.name}", parsed.name == "packed brown sugar")
        val half = OcrParser.parseIngredientLine("Y2 cup white vinegar")
        assertTrue("half wrong: ${half.amount}", half.amount == 0.5)
        val eighth = OcrParser.parseIngredientLine("Y8 tsp ground cinnamon")
        assertTrue("eighth wrong: ${eighth.amount}", eighth.amount == 0.125)
    }

    // A digit read where a letter belongs ("teasp0on") used to skip the
    // lexicon because tokens with digits were never repaired.
    @Test
    fun digitGarbleInUnitWordIsFixed() {
        val line = OcrLexicon.fixLine("1 teasp0on whole cloves")
        assertTrue("line wrong: $line", line == "1 teaspoon whole cloves")
        val units = OcrParser.parseIngredientLine(line)
        assertTrue("unit wrong: ${units.unit}", units.unit == "teaspoon")
        assertTrue("amount wrong: ${units.amount}", units.amount == 1.0)
    }

    // The cider card's directions wrapped onto a line reading
    // "photo, page 83). Add bag to" which rode into the ingredient list
    // ahead of the first step-shaped line.
    @Test
    fun proseTailInsideIngredientWindowIsDropped() {
        val text = """
            MULLED CIDER
            Ingredients:
            8 cups apple cider
            1 teaspoon whole allspice
            photo, page 83). Add bag to
            1. Combine all ingredients in a saucepan
        """.trimIndent()
        val parsed = OcrParser.parse(text)
        val ingText = parsed.ingredients.joinToString(" | ") { it.rawText.ifBlank { it.name } }
        val stepText = parsed.steps.joinToString(" | ") { it.instructionText }
        assertFalse("prose tail leaked into ingredients: $ingText", ingText.contains("photo, page"))
        assertFalse("prose tail leaked into ingredients: $ingText", ingText.contains("Add bag"))
        assertTrue("cider missing: $ingText", ingText.contains("apple cider"))
        assertTrue("step missing: $stepText", stepText.contains("Combine"))
    }

    // Keith's hot-spiced-cider cookbook card: no Ingredients header at all,
    // numbered steps whose tails capitalize ("For spice bag, ..."), a
    // duration opening a wrapped line ("10 minutes. Meanwhile..."), a step
    // verb mid-step ("Serve cider in mugs..."), nutrition footers riding in
    // as measurement-shaped lines, and page-margin fragments from the
    // neighboring card ("ite,", "vder,"). v0.5.13 dropped half of step 1,
    // split step 2 in two, and let the nutrition lines in as ingredients.
    private fun ciderBoxes(): List<OcrParser.OcrLineBox> = listOf(
            box("Daily Values: 13% vit. A, 1% vit. C, 11% calcium, 0% iron", 500, 20, 2620, 110),
            box("HOT SPICED CIDER", 940, 360, 2620, 480),
            box("PREP: 10 MINUTES COOK: 15 MINUTES", 500, 495, 1850, 570),
            box("Fast", 560, 645, 900, 720),
            box("No-Fat", 1700, 645, 2250, 720),
            box("ite,", 0, 700, 300, 780),
            box("8 cups apple cider or apple juice", 560, 900, 2500, 990),
            box("Y4 to Y2 cup packed brown sugar", 500, 1000, 2400, 1090),
            box("6 inches stick cinnamon", 560, 1100, 1900, 1190),
            box("er", 0, 1150, 250, 1240),
            box("1 teaspoon whole allspice", 560, 1200, 2100, 1290),
            box("I teasp00n whole cloves", 560, 1300, 1900, 1390),
            box("vder,", 0, 1350, 300, 1440),
            box("8 thin orange edges (optional)", 560, 1400, 2250, 1490),
            box("ocoa", 0, 1500, 300, 1580),
            box("8 whole cloves (optional)", 560, 1500, 1900, 1590),
            box("1. In a saucepan combine cider and brown sugar.", 500, 1700, 2500, 1790),
            box("For spice bag, place cinnamon, allspice, and the", 500, 1800, 2500, 1890),
            box("1 teaspoon whole cloves on a double-thick, 6-inch-", 500, 1900, 2500, 1990),
            box("square piece of 100% cotton cheesecloth. Bring", 500, 2000, 2400, 2090),
            box("corners together and tie with a clean string (see", 500, 2100, 2400, 2190),
            box("photo, page 83). Add bag to cider mixture.", 500, 2200, 2250, 2290),
            box("2. Bring to bowling; reduce heat. Cover and simmer", 500, 2320, 2500, 2410),
            box("10 minutes. Meanwhile, if desired, stud orange", 500, 2420, 2400, 2510),
            box("wedges with cloves. Remove spice bag; discard.", 500, 2520, 2300, 2610),
            box("Serve cider in mugs with studied orange edges,", 500, 2620, 2450, 2710),
            box("if desired. Makes 8 (about 8-ounce) servings.", 500, 2720, 2250, 2810),
            box("Nutrition Facts per serving: 145 cal., 0 g total fat, 0 mg chol.,", 500, 2880, 2650, 2960),
            box("9 mg sodium, 40 g carbo., 0 g fiber, 0 g pro.", 500, 2970, 2100, 3050),
            box("Daily Values: 0% vit. A, 4% vit. C, 1% calcium, 9% iron", 500, 3060, 2400, 3140),
        )

    @Test
    fun hotSpicedCiderCardParses() {
        val boxes = listOf(
            box("Daily Values: 13% vit. A, 1% vit. C, 11% calcium, 0% iron", 500, 20, 2620, 110),
            box("HOT SPICED CIDER", 940, 360, 2620, 480),
            box("PREP: 10 MINUTES COOK: 15 MINUTES", 500, 495, 1850, 570),
            box("Fast", 560, 645, 900, 720),
            box("No-Fat", 1700, 645, 2250, 720),
            box("ite,", 0, 700, 300, 780),
            box("8 cups apple cider or apple juice", 560, 900, 2500, 990),
            box("Y4 to Y2 cup packed brown sugar", 500, 1000, 2400, 1090),
            box("6 inches stick cinnamon", 560, 1100, 1900, 1190),
            box("er", 0, 1150, 250, 1240),
            box("1 teaspoon whole allspice", 560, 1200, 2100, 1290),
            box("I teasp00n whole cloves", 560, 1300, 1900, 1390),
            box("vder,", 0, 1350, 300, 1440),
            box("8 thin orange edges (optional)", 560, 1400, 2250, 1490),
            box("ocoa", 0, 1500, 300, 1580),
            box("8 whole cloves (optional)", 560, 1500, 1900, 1590),
            box("1. In a saucepan combine cider and brown sugar.", 500, 1700, 2500, 1790),
            box("For spice bag, place cinnamon, allspice, and the", 500, 1800, 2500, 1890),
            box("1 teaspoon whole cloves on a double-thick, 6-inch-", 500, 1900, 2500, 1990),
            box("square piece of 100% cotton cheesecloth. Bring", 500, 2000, 2400, 2090),
            box("corners together and tie with a clean string (see", 500, 2100, 2400, 2190),
            box("photo, page 83). Add bag to cider mixture.", 500, 2200, 2250, 2290),
            box("2. Bring to bowling; reduce heat. Cover and simmer", 500, 2320, 2500, 2410),
            box("10 minutes. Meanwhile, if desired, stud orange", 500, 2420, 2400, 2510),
            box("wedges with cloves. Remove spice bag; discard.", 500, 2520, 2300, 2610),
            box("Serve cider in mugs with studied orange edges,", 500, 2620, 2450, 2710),
            box("if desired. Makes 8 (about 8-ounce) servings.", 500, 2720, 2250, 2810),
            box("Nutrition Facts per serving: 145 cal., 0 g total fat, 0 mg chol.,", 500, 2880, 2650, 2960),
            box("9 mg sodium, 40 g carbo., 0 g fiber, 0 g pro.", 500, 2970, 2100, 3050),
            box("Daily Values: 0% vit. A, 4% vit. C, 1% calcium, 9% iron", 500, 3060, 2400, 3140),
        )
        val text = boxes.joinToString("\n") { it.text }
        val parsed = OcrParser.parse(text, boxes = boxes)

        assertTrue("title: ${parsed.title}", parsed.title == "HOT SPICED CIDER")
        val ingText = parsed.ingredients.joinToString(" | ") { it.rawText.ifBlank { it.name } }
        val stepText = parsed.steps.joinToString(" | ") { it.instructionText }

        assertTrue("7 ingredients, got ${parsed.ingredients.size}: $ingText", parsed.ingredients.size == 7)
        assertTrue("cider: $ingText", ingText.contains("apple cider or apple juice"))
        assertTrue("brown sugar: $ingText", ingText.contains("packed brown sugar"))
        assertTrue("cinnamon: $ingText", ingText.contains("stick cinnamon"))
        assertTrue("allspice: $ingText", ingText.contains("whole allspice"))
        assertTrue("teaspoon cloves: $ingText", ingText.contains("teaspoon whole cloves"))
        assertTrue("orange wedges: $ingText", ingText.contains("orange wedges"))
        assertFalse("no nutrition in ingredients: $ingText", ingText.contains("mg sodium"))
        assertFalse("no daily values: $ingText", ingText.contains("Daily Values"))
        assertFalse("no step tail in ingredients: $ingText", ingText.contains("photo, page 83"))
        assertFalse("no margin fragments: $ingText", ingText.contains("vder"))
        assertFalse("no badges: $ingText", ingText.contains("No-Fat"))

        assertTrue("2 steps, got ${parsed.steps.size}: $stepText", parsed.steps.size == 2)
        assertTrue("step 1 saucepan: $stepText", stepText.contains("In a saucepan combine cider and brown sugar"))
        assertTrue("step 1 spice bag: $stepText ING=[$ingText]", stepText.contains("For spice bag"))
        assertTrue("step 1 cheesecloth: $stepText", stepText.contains("cheesecloth"))
        assertTrue("step 1 add bag: $stepText", stepText.contains("Add bag to cider mixture"))
        assertTrue("step 2 boiling: $stepText", stepText.contains("Bring to boiling"))
        assertTrue("step 2 simmer 10: $stepText", stepText.contains("simmer 10 minutes"))
        assertTrue("step 2 serve: $stepText", stepText.contains("Serve cider in mugs"))
        assertFalse("servings note is not a step: $stepText", stepText.contains("Makes 8"))
        assertFalse("no bowling: $stepText", stepText.contains("bowling"))
        assertFalse("no studied: $stepText", stepText.contains("studied"))
        assertFalse("no edges: $stepText", stepText.contains("orange edges"))
        assertFalse("no margin fragments in steps: $stepText", stepText.contains("vder"))
        assertTrue("servings 8: ${parsed.baseServings}", parsed.baseServings == 8)
    }
    @Test
    fun regionModeParsesCookbookCard() {
        val regions = OcrParser.Regions(
            title = "GERMAN CHOCOLATE CAKE",
            ingredients = "1 1/2 cups all-purpose flour\n3/4 teaspoon baking soda\n" +
                "1/2 teaspoon salt\n4 (1-ounce) squares German sweet chocolate\n" +
                "1/2 cup boiling water\n1 cup butter, softened\n2 cups sugar\n" +
                "4 eggs, separated\n1 teaspoon vanilla extract\n1 cup buttermilk",
            steps = "1. Grease and lightly flour two 8x1 1/2-inch round pans.\n" +
                "2. Melt chocolate in the boiling water. Cool.\n" +
                "3. Cream butter and sugar until light and fluffy. Beat in egg yolks and vanilla.\n" +
                "4. Bake at 350 degrees for 30 minutes. Cool and frost.",
        )
        val text = "GERMAN CHOCOLATE CAKE\nMakes 12 servings\n" + regions.ingredients + "\n" + regions.steps
        val parsed = OcrParser.parse(text, emptyList(), emptyMap(), emptyList(), regions = regions)
        assertEquals("GERMAN CHOCOLATE CAKE", parsed.title)
        assertEquals(10, parsed.ingredients.size)
        println("STEP: " + parsed.steps.joinToString(" | ") { it.instructionText })
        assertEquals(
            "steps: " + parsed.steps.joinToString(" | ") { it.instructionText },
            4,
            parsed.steps.size,
        )
        assertTrue(
            "baking soda line: " + parsed.ingredients.joinToString { it.name },
            parsed.ingredients.any { it.name.contains("baking soda") },
        )
        assertTrue(
            "steps must not leak into ingredients: " + parsed.ingredients.joinToString { it.name },
            parsed.ingredients.none { it.name.contains("Bake") },
        )
        assertEquals(12, parsed.baseServings)
    }


    // ---- the six-layout test set: cue labels feed regions, the rules parse ----

    private fun regionsFrom(blocks: List<RegionBlock>): OcrParser.Regions {
        val labels = RegionLabeler.resolveLabels(blocks, RegionLabeler.cueLabels(blocks), null)
        val title = blocks[labels.indexOfFirst { it == RegionKind.TITLE }].text
        val ing = blocks.withIndex()
            .filter { labels[it.index] == RegionKind.INGREDIENTS }
            .joinToString("\n") { it.value.text }
        val stp = blocks.withIndex()
            .filter { labels[it.index] == RegionKind.STEPS }
            .joinToString("\n") { it.value.text }
        return OcrParser.Regions(title, ing, stp)
    }

    private fun parseLayout(blocks: List<RegionBlock>) =
        OcrParser.parse(blocks.joinToString("\n") { it.text }, emptyList(), emptyMap(), emptyList(), regions = regionsFrom(blocks))

    @Test
    fun hybridReadsTheCard() {
        val blocks = listOf(
            RegionBlock("Fluffy Buttermilk Pancakes", 62, 55, 730, 100),
            RegionBlock("Ingredients", 62, 138, 242, 168),
            RegionBlock(
                "2 cups all-purpose flour\n2 tablespoons sugar\n2 teaspoons baking powder\n" +
                    "1/2 teaspoon baking soda\n1/2 teaspoon salt\n2 cups buttermilk\n" +
                    "2 large eggs\n1/4 cup melted butter",
                62, 185, 420, 462,
            ),
            RegionBlock("Directions", 62, 492, 220, 518),
            RegionBlock("1. Whisk the flour, sugar, baking powder, baking soda, and salt in a large bowl.", 62, 538, 660, 592),
            RegionBlock("2. In a separate bowl, beat the buttermilk, eggs, and melted butter.", 62, 610, 660, 664),
            RegionBlock("3. Pour the wet ingredients into the dry and stir until just combined; lumps are fine.", 62, 682, 660, 736),
            RegionBlock("4. Heat a griddle over medium heat and grease lightly.", 62, 754, 660, 808),
            RegionBlock("5. Pour 1/4 cup batter per pancake; flip when bubbles form, about 2 minutes per side.", 62, 826, 660, 880),
        )
        val parsed = parseLayout(blocks)
        assertEquals("Fluffy Buttermilk Pancakes", parsed.title)
        assertEquals(8, parsed.ingredients.size)
        assertTrue(parsed.ingredients.any { it.name.contains("baking soda") })
        assertTrue(parsed.ingredients.any { it.name.contains("melted butter") })
        assertEquals(5, parsed.steps.size)
        assertTrue(parsed.steps[0].instructionText.startsWith("Whisk"))
    }

    @Test
    fun hybridReadsTheTwoColumnCookbook() {
        val blocks = listOf(
            RegionBlock("Weeknight Chicken Stir-Fry", 62, 45, 715, 95),
            RegionBlock("[ photo ]", 400, 180, 570, 220),
            RegionBlock("INGREDIENTS", 62, 330, 228, 355),
            RegionBlock(
                "1 1/2 lbs chicken breast, sliced thin\n3 tablespoons soy sauce\n" +
                    "2 tablespoons cornstarch\n1/4 cup chicken broth\n2 tablespoons oyster sauce\n" +
                    "1 tablespoon sesame oil\n3 cloves garlic, minced\n1 tablespoon fresh ginger, grated\n" +
                    "2 cups broccoli florets\n1 red bell pepper, sliced\n2 tablespoons vegetable oil",
                62, 365, 340, 748,
            ),
            RegionBlock("METHOD", 400, 330, 535, 355),
            RegionBlock(
                "1. Toss the chicken with 1 tablespoon soy sauce and the cornstarch; rest 10 minutes.\n" +
                    "2. Stir the broth, oyster sauce, remaining soy sauce, and sesame oil in a small bowl.\n" +
                    "3. Heat 1 tablespoon vegetable oil in a wok over high heat; sear chicken 4 minutes. Remove.\n" +
                    "4. Add remaining oil; stir-fry garlic, ginger, broccoli, and bell pepper 3 minutes.\n" +
                    "5. Return chicken to the wok; pour in sauce and toss until thickened, about 2 minutes.\n" +
                    "6. Serve over rice.",
                400, 365, 940, 700,
            ),
        )
        val parsed = parseLayout(blocks)
        assertEquals("Weeknight Chicken Stir-Fry", parsed.title)
        assertEquals(11, parsed.ingredients.size)
        assertTrue(parsed.ingredients.any { it.name.contains("chicken breast") })
        assertEquals(6, parsed.steps.size)
        assertTrue(parsed.steps[0].instructionText.startsWith("Toss"))
        assertTrue(parsed.steps.last().instructionText.contains("rice"))
    }

    @Test
    fun hybridReadsTheApplianceBooklet() {
        val blocks = listOf(
            RegionBlock("CHEWY CHOCOLATE CHIP COOKIES", 60, 40, 560, 72),
            RegionBlock("Model ABC-123 Cookie Program | Makes about 36 cookies", 60, 85, 480, 102),
            RegionBlock("YOU WILL NEED:", 60, 120, 210, 138),
            RegionBlock(
                "2 1/4 cups all-purpose flour; 1 teaspoon baking soda; 1 teaspoon salt; " +
                    "1 cup butter, softened; 3/4 cup granulated sugar; 3/4 cup brown sugar; " +
                    "2 large eggs; 2 teaspoons vanilla extract; 2 cups chocolate chips.",
                60, 150, 745, 240,
            ),
            RegionBlock("Step 1: Preheat oven to 375\u00b0F. Line baking sheets with parchment.", 60, 262, 570, 305),
            RegionBlock("CAUTION: Baking sheets will be hot. Always use oven mitts when handling.", 60, 345, 730, 388),
            RegionBlock("Step 2: Whisk flour, baking soda, and salt in a bowl.", 60, 398, 540, 416),
            RegionBlock("Step 3: Beat butter and both sugars until fluffy, about 3 minutes.", 60, 425, 640, 466),
            RegionBlock("Step 4: Beat in eggs one at a time, then vanilla.", 60, 480, 500, 498),
            RegionBlock("NOTE: Do not overmix once flour is added, or cookies will be tough.", 60, 505, 690, 543),
            RegionBlock("Step 5: Fold in the dry ingredients until just combined, then the chocolate chips.", 60, 552, 690, 595),
            RegionBlock("Step 6: Scoop 2-tablespoon balls onto sheets, 2 inches apart.", 60, 600, 655, 618),
            RegionBlock("Step 7: Bake 9 to 11 minutes until edges are golden. Cool on the sheet 5 minutes.", 60, 640, 700, 682),
        )
        val parsed = parseLayout(blocks)
        assertTrue("title: " + parsed.title, parsed.title.equals("CHEWY CHOCOLATE CHIP COOKIES", ignoreCase = true))
        assertEquals(9, parsed.ingredients.size)
        assertTrue(parsed.ingredients.any { it.name.contains("granulated sugar") })
        assertTrue(parsed.ingredients.any { it.name.contains("chocolate chips") })
        assertEquals(7, parsed.steps.size)
        assertTrue(parsed.steps[0].instructionText.contains("Preheat"))
        assertTrue(parsed.steps.last().instructionText.contains("Bake"))
    }

    @Test
    fun hybridReadsTheMagazine() {
        val blocks = listOf(
            RegionBlock("Fluffy Buttermilk Pancakes", 70, 55, 925, 105),
            RegionBlock(
                "The secret is resting the batter, ten quiet minutes gives you the " +
                    "fluffiest stack of your life. A slow Sunday morning, made better.",
                70, 115, 850, 170,
            ),
            RegionBlock("What you'll need", 88, 205, 300, 228),
            RegionBlock(
                "2 cups all-purpose flour\n2 tablespoons sugar\n2 teaspoons baking powder\n" +
                    "1/2 teaspoon baking soda\n1/2 teaspoon salt\n2 cups buttermilk\n" +
                    "2 large eggs\n1/4 cup melted butter",
                88, 240, 340, 478,
            ),
            RegionBlock("1", 432, 215, 468, 268),
            RegionBlock("Whisk the flour, sugar, baking powder, baking soda, and salt in a large bowl.", 485, 222, 890, 272),
            RegionBlock("2", 432, 288, 468, 340),
            RegionBlock("In a separate bowl, beat the buttermilk, eggs, and melted butter.", 485, 295, 855, 342),
            RegionBlock("3", 432, 360, 468, 412),
            RegionBlock("Pour the wet ingredients into the dry and stir until just combined; lumps are fine.", 485, 367, 895, 415),
            RegionBlock("4", 432, 428, 468, 480),
            RegionBlock("Heat a griddle over medium heat and grease lightly.", 485, 435, 820, 480),
            RegionBlock("5", 432, 498, 468, 550),
            RegionBlock("Pour 1/4 cup batter per pancake; flip when bubbles form, about 2 minutes per side.", 485, 505, 880, 575),
            RegionBlock(
                "TIP: Keep finished pancakes warm in a 200\u00b0F oven while you work through the " +
                    "batter. Never press them with the spatula, you'll squeeze out the fluff.",
                88, 805, 900, 872,
            ),
        )
        val parsed = parseLayout(blocks)
        assertEquals("Fluffy Buttermilk Pancakes", parsed.title)
        assertEquals(8, parsed.ingredients.size)
        assertEquals(5, parsed.steps.size)
        assertTrue(parsed.steps[0].instructionText.startsWith("Whisk"))
    }

    @Test
    fun hybridReadsTheNoHeaderBook() {
        val prose =
            "For the stir-fry, begin with 1 1/2 lbs chicken breast, sliced thin, " +
                "3 tablespoons soy sauce, 2 tablespoons cornstarch, 1/4 cup chicken broth, " +
                "2 tablespoons oyster sauce, 1 tablespoon sesame oil, 3 cloves garlic, minced, " +
                "1 tablespoon fresh ginger, grated, 2 cups broccoli florets, 1 red bell pepper, " +
                "sliced, and 2 tablespoons vegetable oil. Have everything sliced and measured " +
                "before the wok gets hot, because once you start there is no pausing."
        val blocks = listOf(
            RegionBlock("Weeknight Chicken Stir-Fry", 60, 50, 585, 88),
            RegionBlock(prose, 60, 105, 720, 335),
            RegionBlock("Toss the chicken with 1 tablespoon soy sauce and the cornstarch; rest 10 minutes.", 60, 350, 620, 400),
            RegionBlock("Stir the broth, oyster sauce, remaining soy sauce, and sesame oil in a small bowl.", 60, 415, 700, 465),
            RegionBlock("Heat 1 tablespoon vegetable oil in a wok over high heat; sear chicken 4 minutes. Remove.", 60, 480, 705, 530),
            RegionBlock("Add remaining oil; stir-fry garlic, ginger, broccoli, and bell pepper 3 minutes.", 60, 545, 635, 592),
            RegionBlock("Return chicken to the wok; pour in sauce and toss until thickened, about 2 minutes.", 60, 605, 680, 655),
            RegionBlock("Serve over rice.", 60, 668, 215, 688),
        )
        val parsed = parseLayout(blocks)
        assertEquals("Weeknight Chicken Stir-Fry", parsed.title)
        assertEquals(
            "ingredients: " + parsed.ingredients.joinToString(" | ") { it.rawText.ifBlank { it.name } },
            11,
            parsed.ingredients.size,
        )
        assertTrue(parsed.ingredients.any { it.name.contains("broccoli") })
        assertTrue(parsed.ingredients.any { it.name.contains("bell pepper") })
        assertEquals(6, parsed.steps.size)
        assertTrue(parsed.steps[0].instructionText.startsWith("Toss"))
    }

    @Test
    fun hybridReadsTwoRecipesOnePage() {
        val blocks = listOf(
            RegionBlock("Fluffy Buttermilk Pancakes", 63, 40, 575, 78),
            RegionBlock(
                "Ingredients: 2 cups all-purpose flour | 2 tablespoons sugar | 2 teaspoons baking\n" +
                    "powder | 1/2 teaspoon baking soda | 1/2 teaspoon salt | 2 cups buttermilk | 2 large\n" +
                    "eggs | 1/4 cup melted butter",
                63, 95, 900, 165,
            ),
            RegionBlock("1) Whisk the flour, sugar, baking powder, baking soda, and salt in a large bowl.", 63, 180, 900, 200),
            RegionBlock("2) In a separate bowl, beat the buttermilk, eggs, and melted butter.", 63, 205, 900, 225),
            RegionBlock("3) Pour the wet ingredients into the dry and stir until just combined; lumps are fine.", 63, 230, 900, 250),
            RegionBlock("4) Heat a griddle over medium heat and grease lightly.", 63, 255, 900, 275),
            RegionBlock("5) Pour 1/4 cup batter per pancake; flip when bubbles form, about 2 minutes per side.", 63, 280, 900, 300),
            RegionBlock("Chewy Chocolate Chip Cookies", 63, 370, 675, 405),
            RegionBlock("1. Preheat oven to 375\u00b0F. Line baking sheets with\nparchment.", 63, 425, 590, 465),
            RegionBlock("2. Whisk flour, baking soda, and salt in a bowl.", 63, 480, 590, 500),
            RegionBlock("3. Beat butter and both sugars until fluffy, about 3\nminutes.", 63, 505, 590, 545),
            RegionBlock("4. Beat in eggs one at a time, then vanilla.", 63, 565, 590, 585),
            RegionBlock("5. Fold in the dry ingredients until just combined, then\nthe chocolate chips.", 63, 600, 590, 640),
            RegionBlock("6. Scoop 2-tablespoon balls onto sheets, 2 inches\napart.", 63, 655, 590, 695),
            RegionBlock("\u00b7 2 1/4 cups all-purpose flour", 645, 425, 895, 445),
            RegionBlock("\u00b7 1 teaspoon baking soda", 645, 450, 895, 470),
            RegionBlock("\u00b7 1 teaspoon salt", 645, 475, 895, 495),
            RegionBlock("\u00b7 1 cup butter, softened", 645, 500, 895, 520),
            RegionBlock("\u00b7 3/4 cup granulated sugar", 645, 525, 895, 545),
            RegionBlock("\u00b7 3/4 cup brown sugar", 645, 550, 895, 570),
            RegionBlock("\u00b7 2 large eggs", 645, 575, 895, 595),
            RegionBlock("\u00b7 2 teaspoons vanilla extract", 645, 600, 895, 620),
            RegionBlock("\u00b7 2 cups chocolate chips", 645, 625, 895, 645),
        )
        val labels = RegionLabeler.resolveLabels(blocks, RegionLabeler.cueLabels(blocks), null)
        val groups = RegionLabeler.splitRecipes(blocks, labels)
        assertEquals(2, groups.size)
        val recipes = groups.map { g ->
            val title = StringBuilder(); val ing = StringBuilder(); val steps = StringBuilder()
            for (b in g) when (labels[blocks.indexOf(b)]) {
                RegionKind.TITLE -> title.append(b.text).append('\n')
                RegionKind.INGREDIENTS -> ing.append(b.text).append('\n')
                RegionKind.STEPS -> steps.append(b.text).append('\n')
                RegionKind.OTHER -> {}
            }
            OcrParser.parse(
                blocks.joinToString("\n") { it.text }, emptyList(), emptyMap(), emptyList(),
                regions = OcrParser.Regions(title.toString(), ing.toString(), steps.toString()),
            )
        }
        val first = recipes[0]
        assertEquals("Fluffy Buttermilk Pancakes", first.title)
        assertEquals(8, first.ingredients.size)
        assertTrue(first.ingredients.any { it.name.contains("melted butter") })
        assertEquals(5, first.steps.size)
        assertTrue(first.steps[0].instructionText.startsWith("Whisk"))
        val second = recipes[1]
        assertEquals("Chewy Chocolate Chip Cookies", second.title)
        assertEquals(
            "ingredients: " + second.ingredients.joinToString(" | ") { it.rawText.ifBlank { it.name } },
            9,
            second.ingredients.size,
        )
        assertTrue(second.ingredients.any { it.name.contains("granulated sugar") })
        assertTrue(second.ingredients.none { it.name.startsWith("\u00b7") })
        assertEquals(6, second.steps.size)
        assertTrue(second.steps[0].instructionText.startsWith("Preheat"))
        assertTrue(second.steps.last().instructionText.contains("apart"))
    }
}
