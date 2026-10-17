package com.trypod33.kitchlore

import com.trypod33.kitchlore.data.RegionBlock
import com.trypod33.kitchlore.data.RegionKind
import com.trypod33.kitchlore.data.RegionLabeler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionLabelerTest {
    private val blocks = listOf(
        RegionBlock("GERMAN CHOCOLATE CAKE", 100, 50, 900, 130),
        RegionBlock("1 1/2 cups all-purpose flour\n3/4 teaspoon baking soda", 100, 900, 1200, 1000),
        RegionBlock(
            "1. Grease and lightly flour two round pans.\n2. Blend chocolate and water in a saucepan",
            100, 1500, 1200, 2100,
        ),
    )

    @Test
    fun happyReplyLabelsAllBlocks() {
        val labels = RegionLabeler.parseLabels("B1: title\nB2: ingredients\nB3: steps", blocks)
        assertEquals(listOf(RegionKind.TITLE, RegionKind.INGREDIENTS, RegionKind.STEPS), labels)
    }

    @Test
    fun directionsReadsAsSteps() {
        val labels = RegionLabeler.parseLabels("B1: title\nB2: ingredients\nB3: directions", blocks)
        assertEquals(RegionKind.STEPS, labels!![2])
    }

    @Test
    fun missingIngredientsRegionFails() {
        assertNull(RegionLabeler.parseLabels("B1: title\nB2: steps\nB3: other", blocks))
    }

    @Test
    fun garbageReplyFails() {
        assertNull(RegionLabeler.parseLabels("I cannot label this page.", blocks))
        assertNull(RegionLabeler.parseLabels("", blocks))
    }

    @Test
    fun outOfRangeIndexFails() {
        assertNull(RegionLabeler.parseLabels("B1: title\nB2: ingredients\nB9: steps", blocks))
    }

    @Test
    fun unlabeledBlocksDefaultToOther() {
        val more = blocks + listOf(RegionBlock("Makes 12 servings", 100, 2200, 500, 2300))
        val labels = RegionLabeler.parseLabels("B1: title\nB2: ingredients\nB3: steps", more)
        assertEquals(RegionKind.OTHER, labels!![3])
    }

    @Test
    fun nutritionFooterNeverIngredients() {
        val footer = RegionBlock("Daily values: fat 12g, sodium 300mg", 100, 2200, 900, 2300)
        val labels = RegionLabeler.parseLabels(
            "B1: title\nB2: ingredients\nB3: steps\nB4: ingredients",
            blocks + footer,
        )
        assertEquals(RegionKind.OTHER, labels!![3])
    }

    @Test
    fun promptCarriesPositionAndText() {
        val prompt = RegionLabeler.buildPrompt(blocks)
        assertTrue("prompt: " + prompt, prompt.contains("B1 (top 2%, left 8%, width 66%): GERMAN CHOCOLATE CAKE"))
        assertFalse(prompt.contains("  "))
    }

    // ---- the six-layout test set, Keith's answer keys as cue expectations ----

    private val cardPancakes = listOf(
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

    private val twoColumnStirFry = listOf(
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

    private val bookletCookies = listOf(
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

    private val magazinePancakes = listOf(
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

    private val noHeaderStirFry = listOf(
        RegionBlock("Weeknight Chicken Stir-Fry", 60, 50, 585, 88),
        RegionBlock(
            "For the stir-fry, begin with 1 1/2 lbs chicken breast, sliced thin, " +
                "3 tablespoons soy sauce, 2 tablespoons cornstarch, 1/4 cup chicken broth, " +
                "2 tablespoons oyster sauce, 1 tablespoon sesame oil, 3 cloves garlic, minced, " +
                "1 tablespoon fresh ginger, grated, 2 cups broccoli florets, 1 red bell pepper, " +
                "sliced, and 2 tablespoons vegetable oil. Have everything sliced and measured " +
                "before the wok gets hot, because once you start there is no pausing.",
            60, 105, 720, 335,
        ),
        RegionBlock("Toss the chicken with 1 tablespoon soy sauce and the cornstarch; rest 10 minutes.", 60, 350, 620, 400),
        RegionBlock("Stir the broth, oyster sauce, remaining soy sauce, and sesame oil in a small bowl.", 60, 415, 700, 465),
        RegionBlock("Heat 1 tablespoon vegetable oil in a wok over high heat; sear chicken 4 minutes. Remove.", 60, 480, 705, 530),
        RegionBlock("Add remaining oil; stir-fry garlic, ginger, broccoli, and bell pepper 3 minutes.", 60, 545, 635, 592),
        RegionBlock("Return chicken to the wok; pour in sauce and toss until thickened, about 2 minutes.", 60, 605, 680, 655),
        RegionBlock("Serve over rice.", 60, 668, 215, 688),
    )

    private fun assertKinds(blocks: List<RegionBlock>, expected: List<RegionKind>) {
        val cues = RegionLabeler.cueLabels(blocks)
        assertEquals(
            "kinds: " + blocks.zip(cues) { b, c -> b.text.take(28) + "=" + (c.kind?.name ?: "AMBIGUOUS") },
            expected,
            cues.map { it.kind },
        )
    }

    @Test
    fun cuesReadTheCard() {
        assertKinds(
            cardPancakes,
            listOf(
                RegionKind.TITLE, RegionKind.OTHER, RegionKind.INGREDIENTS, RegionKind.OTHER,
                RegionKind.STEPS, RegionKind.STEPS, RegionKind.STEPS, RegionKind.STEPS, RegionKind.STEPS,
            ),
        )
    }

    @Test
    fun cuesReadTheTwoColumnCookbook() {
        assertKinds(
            twoColumnStirFry,
            listOf(
                RegionKind.TITLE, RegionKind.OTHER, RegionKind.OTHER, RegionKind.INGREDIENTS,
                RegionKind.OTHER, RegionKind.STEPS,
            ),
        )
    }

    @Test
    fun cuesReadTheApplianceBooklet() {
        assertKinds(
            bookletCookies,
            listOf(
                RegionKind.TITLE, RegionKind.OTHER, RegionKind.OTHER, RegionKind.INGREDIENTS,
                RegionKind.STEPS, RegionKind.OTHER, RegionKind.STEPS, RegionKind.STEPS, RegionKind.STEPS,
                RegionKind.OTHER, RegionKind.STEPS, RegionKind.STEPS, RegionKind.STEPS,
            ),
        )
    }

    @Test
    fun cuesReadTheMagazine() {
        assertKinds(
            magazinePancakes,
            listOf(
                RegionKind.TITLE, RegionKind.OTHER, RegionKind.OTHER, RegionKind.INGREDIENTS,
                RegionKind.OTHER, RegionKind.STEPS, RegionKind.OTHER, RegionKind.STEPS,
                RegionKind.OTHER, RegionKind.STEPS, RegionKind.OTHER, RegionKind.STEPS,
                RegionKind.OTHER, RegionKind.STEPS, RegionKind.OTHER,
            ),
        )
    }

    @Test
    fun cuesReadTheNoHeaderBook() {
        assertKinds(
            noHeaderStirFry,
            listOf(
                RegionKind.TITLE, RegionKind.INGREDIENTS, RegionKind.STEPS, RegionKind.STEPS,
                RegionKind.STEPS, RegionKind.STEPS, RegionKind.STEPS, RegionKind.STEPS,
            ),
        )
    }

    @Test
    fun theFiveLayoutsNeedNoModel() {
        // Every cue decided means the model never has to run on these pages.
        for (layout in listOf(cardPancakes, twoColumnStirFry, bookletCookies, magazinePancakes, noHeaderStirFry)) {
            assertTrue(RegionLabeler.cueLabels(layout).none { it.kind == null })
        }
    }

    @Test
    fun cuesNeverTrustTheVerbInAName() {
        // "Weeknight Chicken Stir-Fry" contains a step verb; it is still the title.
        val cues = RegionLabeler.cueLabels(twoColumnStirFry)
        assertEquals(RegionKind.TITLE, cues[0].kind)
    }

    @Test
    fun ambiguousBlocksWithoutAModelFallBack() {
        val prose = RegionBlock(
            "Mix 2 cups flour, 1 cup sugar, 1 teaspoon salt, then beat in 2 eggs until smooth.",
            60, 200, 700, 260,
        )
        assertEquals(RegionKind.INGREDIENTS, RegionLabeler.fallbackKind(prose))
        val steps = RegionBlock(
            "Heat the oven, butter the dish, and bake until golden.",
            60, 200, 700, 260,
        )
        assertEquals(RegionKind.STEPS, RegionLabeler.fallbackKind(steps))
    }
}
