package com.trypod33.kitchlore

import com.trypod33.kitchlore.data.OcrParser.OcrLineBox
import com.trypod33.kitchlore.data.ParsedRecipe
import com.trypod33.kitchlore.data.OcrParser
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Keith's report: the extra material on a scanned recipe never reached the
 * app. "Some extra things that are notes" and a recipe that "breaks out a
 * sauce from the main recipe" both vanished; these pin both into the parser.
 */
class OcrNotesSauceTest {
    private fun box(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrLineBox(text, left, top, right, bottom)

    private fun ingText(r: ParsedRecipe) = r.ingredients.joinToString(" | ") { it.name.ifBlank { it.rawText } }
    private fun stepText(r: ParsedRecipe) = r.steps.joinToString(" | ") { it.instructionText }

    // A NOTES section at the bottom of a card: the prose travels into the
    // recipe's notes field, never into the steps or the ingredient list.
    @Test
    fun notesSectionTravelsIntoNotesField() {
        val text = """
            Sunday Meatloaf
            Serves 6
            Ingredients
            2 lbs ground beef
            1/2 cup breadcrumbs
            Directions
            1 Preheat the oven to 350 degrees.
            2 Mix and shape into a loaf.
            3 Bake for 1 hour.
            Notes
            Let the meatloaf rest 10 minutes before slicing.
            Freezer: keeps 3 months wrapped well.
        """.trimIndent()
        val r = OcrParser.parse(text)
        val notes = r.notes ?: ""
        assertTrue("notes missing rest tip: $notes", notes.contains("rest 10 minutes"))
        assertTrue("notes missing freezer line: $notes", notes.contains("Freezer: keeps 3 months"))
        assertFalse("notes leaked into steps: ${stepText(r)}", stepText(r).contains("Freezer"))
        assertFalse("notes leaked into ingredients: ${ingText(r)}", ingText(r).contains("rest 10 minutes"))
    }

    // An inline "Note: ..." line inside the directions is a note, not a step.
    @Test
    fun inlineNoteLineBecomesNote() {
        val text = """
            Lemon Bars
            Ingredients
            2 cups flour
            1 cup sugar
            Directions
            1 Press the crust into the pan.
            2 Bake 20 minutes.
            Note: chill 2 hours before serving.
            3 Dust with powdered sugar.
        """.trimIndent()
        val r = OcrParser.parse(text)
        assertTrue("inline note lost: ${r.notes}", (r.notes ?: "").contains("chill 2 hours before serving"))
        assertFalse("note leaked into steps: ${stepText(r)}", stepText(r).contains("chill 2 hours"))
        assertTrue("step 3 missing: ${stepText(r)}", stepText(r).contains("Dust with powdered sugar"))
    }

    // A sauce broken out of the main recipe keeps its group visible in the
    // ingredient list, so its amounts do not blur into the main recipe's.
    @Test
    fun sauceBreakoutGetsLabeled() {
        val text = """
            Gravy over Mash
            Serves 4
            Ingredients
            4 cups mashed potatoes
            Sauce:
            2 cups chicken broth
            2 tbsp butter
            Directions
            1 Make the sauce: melt butter, whisk in broth.
            2 Pour over the potatoes.
        """.trimIndent()
        val r = OcrParser.parse(text)
        val ing = ingText(r)
        assertTrue("sauce marker missing: $ing", ing.contains("For the Sauce"))
        assertTrue("broth missing: $ing", ing.contains("chicken broth"))
        assertTrue("butter missing: $ing", ing.contains("butter"))
        assertTrue("step 1 missing: ${stepText(r)}", stepText(r).contains("whisk in broth"))
    }

    // A Better Homes and Gardens-style two-column cookbook page: title and
    // ingredients down the left (sauce broken out), numbered directions down
    // the right, tips at the bottom. Everything must land in its own bucket.
    @Test
    fun cookbookTwoColumnWithSauceAndTips() {
        val boxes = listOf(
            box("Creamy Tomato Soup", 140, 80, 760, 170),
            box("Makes 4 servings", 140, 200, 430, 250),
            box("Ingredients", 140, 300, 330, 350),
            box("2 tbsp olive oil", 140, 380, 420, 430),
            box("1 onion, chopped", 140, 450, 430, 500),
            box("Sauce:", 140, 530, 260, 580),
            box("1 can (28 oz) crushed tomatoes", 140, 600, 640, 650),
            box("2 cups chicken broth", 140, 670, 460, 720),
            box("Directions", 900, 300, 1080, 350),
            box("1 Cook the onion in oil until soft.", 900, 380, 1480, 430),
            box("2 Add tomatoes and broth.", 900, 450, 1400, 500),
            box("3 Simmer 20 minutes, then blend.", 900, 520, 1500, 570),
            box("TIPS:", 140, 800, 280, 850),
            box("Keeps well for 3 days refrigerated.", 140, 870, 700, 920),
        )
        val text = boxes.joinToString("\n") { it.text }
        val r = OcrParser.parse(text, boxes = boxes)
        val ing = ingText(r)
        val steps = stepText(r)
        assertTrue("title wrong: ${r.title}", r.title.contains("Tomato Soup"))
        assertTrue("sauce marker missing: $ing", ing.contains("For the Sauce"))
        assertTrue("tomatoes missing: $ing", ing.contains("crushed tomatoes"))
        assertFalse("steps leaked into ingredients: $ing", ing.contains("Simmer 20 minutes"))
        assertTrue("step 3 missing: $steps", steps.contains("blend"))
        assertTrue("tips lost: ${r.notes}", (r.notes ?: "").contains("Keeps well for 3 days"))
        assertFalse("tips leaked into steps: $steps", steps.contains("Keeps well"))
    }

    // A handwritten card whose notes live in the corner: the note lines ride
    // into the notes field instead of being dropped or misread as steps.
    @Test
    fun handwrittenCardCornerNotes() {
        val boxes = listOf(
            box("Aunt Dot's Rolls", 180, 70, 700, 160),
            box("2 cups flour", 160, 300, 520, 350),
            box("1 cup warm milk", 160, 380, 540, 430),
            box("2 tbsp butter, softened", 160, 460, 560, 510),
            box("Mix and knead 8 minutes.", 900, 300, 1480, 350),
            box("Let rise 1 hour.", 900, 380, 1300, 430),
            box("Bake 375 for 20 minutes.", 900, 460, 1420, 510),
            box("Note: dough freezes well", 180, 620, 560, 670),
            box("for 1 month.", 180, 690, 420, 740),
        )
        val text = boxes.joinToString("\n") { it.text }
        val r = OcrParser.parse(text, boxes = boxes)
        val notes = r.notes ?: ""
        assertTrue("handwritten note lost: $notes", notes.contains("dough freezes well"))
        assertTrue("steps missing: ${stepText(r)}", stepText(r).contains("knead 8 minutes"))
        assertFalse("note leaked into steps: ${stepText(r)}", stepText(r).contains("freezes well"))
    }
}
