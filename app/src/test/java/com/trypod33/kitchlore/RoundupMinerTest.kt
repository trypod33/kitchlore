package com.trypod33.kitchlore

import com.trypod33.kitchlore.data.RoundupMiner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundupMinerTest {

    private val pageUrl = "https://www.tasteofhome.com/collection/best-chicken-recipes/"

    @Test
    fun minesDishLinksFromARoundup() {
        val html = """
            <html><body>
            <a href="/recipes/moroccan-apricot-chicken/">Moroccan Apricot Chicken</a>
            <a href="/recipes/easy-creamy-chicken-enchiladas/">White Chicken Enchiladas</a>
            <a href="https://www.tasteofhome.com/recipe-box/">My Recipe Box</a>
            <a href="/about/">About Us</a>
            <a href="https://www.tasteofhome.com/collection/best-chicken-recipes/">Best chicken recipes</a>
            <a href="https://other.com/recipes/soup">One</a>
            </body></html>
        """.trimIndent()
        val dishes = RoundupMiner.mine(html, pageUrl)
        val titles = dishes.map { it.title }
        assertTrue(titles.contains("Moroccan Apricot Chicken"))
        assertTrue(titles.contains("White Chicken Enchiladas"))
        // Nav links, too-short labels, and the page itself stay out.
        assertEquals(2, dishes.size)
        assertTrue(dishes.all { it.url.startsWith("https://www.tasteofhome.com/recipes/") })
    }

    @Test
    fun trimsTrailingDecorationFromTitles() {
        val html = """
            <a href="https://www.delish.com/cooking/recipe-ideas/a19636089/creamy-tuscan-chicken/">Creamy Tuscan Chicken recipe</a>
            <a href="https://www.foodnetwork.com/recipes/a/lemon-chicken">Lemon Chicken | Food Network</a>
        """.trimIndent()
        val titles = RoundupMiner.mine(html, pageUrl).map { it.title }
        assertTrue(titles.contains("Creamy Tuscan Chicken"))
        assertTrue(titles.contains("Lemon Chicken"))
    }

    @Test
    fun resolvesRelativeLinksAgainstThePage() {
        val html = """<a href="/recipes/sesame-chicken-stir-fry/">Sesame Chicken Stir-Fry</a>"""
        val dishes = RoundupMiner.mine(html, pageUrl)
        assertEquals(1, dishes.size)
        assertEquals("https://www.tasteofhome.com/recipes/sesame-chicken-stir-fry/", dishes[0].url)
    }
}
