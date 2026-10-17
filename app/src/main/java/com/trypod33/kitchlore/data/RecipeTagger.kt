package com.trypod33.kitchlore.data

// Auto-tagging: new recipes land tagged from their title and ingredient
// lines, so the library organizes itself. Wrong guesses are one tap to fix
// (long-press a card, or the Tags button on the recipe page), and manual
// tags always win: auto-tagging only fills a blank tags field.
object RecipeTagger {

    // Dish type reads off the title; ingredients only back up the cuisines,
    // proteins and the vegetarian check. "Main" is the fallback when the
    // recipe looks like a meal but nothing more specific fit.
    private val DISH = linkedMapOf(
        "Dessert" to setOf("cake", "cupcake", "cupcakes", "cookie", "cookies", "brownie", "brownies",
            "pie", "tart", "tarts", "cheesecake", "cobbler", "fudge", "candy", "mints", "truffle",
            "truffles", "pudding", "mousse", "shortbread", "macaroon", "macaroons", "meringue",
            "brittle", "baklava", "dessert", "desserts", "parfait", "sorbet", "ganache"),
        "Breakfast" to setOf("breakfast", "pancake", "pancakes", "waffle", "waffles", "omelet",
            "omelette", "quiche", "granola", "oatmeal", "frittata", "muffin", "muffins",
            "french toast", "hash browns"),
        "Soup" to setOf("soup", "stew", "chowder", "chili", "bisque", "gumbo", "broth"),
        "Salad" to setOf("salad", "salads", "coleslaw", "slaw"),
        "Bread" to setOf("bread", "rolls", "roll", "buns", "bun", "biscuit", "biscuits", "scone",
            "scones", "bagel", "bagels", "croissant", "croissants", "loaf", "flatbread",
            "tortillas", "cornbread", "focaccia", "baguette"),
        "Pasta" to setOf("pasta", "spaghetti", "lasagna", "macaroni", "noodle", "noodles", "ravioli",
            "penne", "fettuccine", "linguine", "ziti", "rigatoni", "linguini", "gnocchi", "casserole"),
        "Appetizer" to setOf("appetizer", "appetizers", "bruschetta", "crostini", "sliders", "wings",
            "nachos", "canapes", "skewers", "popper", "poppers", "deviled"),
        "Drink" to setOf("punch", "smoothie", "smoothies", "lemonade", "cocktail", "cider", "cocoa",
            "tea", "coffee", "slush", "frappe", "shake", "margarita", "sangria"),
        "Sauce" to setOf("sauce", "salsa", "dressing", "gravy", "dip", "hummus", "spread", "marinade",
            "pesto", "guacamole", "syrup", "glaze", "buttercream"),
        "Snack" to setOf("snack", "snacks", "popcorn", "crackers", "trail mix", "chex"),
        "Main" to setOf("taco", "tacos", "enchilada", "enchiladas", "burrito", "burritos",
            "quesadilla", "pizza", "sandwich", "sandwiches", "burger", "burgers", "meatloaf",
            "potpie", "curry", "stir fry", "roast", "pilaf", "paella", "risotto", "hash",
            "skillet", "parmesan", "parmigiana", "masala", "alfredo", "carbonara", "wings"),
    )

    private val CUISINE = linkedMapOf(
        "Italian" to setOf("italian", "marinara", "parmesan", "mozzarella", "ricotta", "tiramisu",
            "risotto", "carbonara", "alfredo", "pesto", "marsala", "biscotti", "bruschetta",
            "gnocchi", "romano", "cannelloni", "manicotti", "oregano", "basil", "ciabatta"),
        "Mexican" to setOf("mexican", "tortilla", "tortillas", "jalapeno", "cumin", "cilantro",
            "fajita", "fajitas", "tostada", "chimichanga", "mole", "poblano", "queso", "carnitas",
            "enchilada", "enchiladas", "salsa", "taco", "tacos"),
        "Chinese" to setOf("chinese", "soy sauce", "sesame", "wonton", "wontons", "dumpling",
            "dumplings", "wok", "hoisin", "egg roll", "fried rice", "lo mein", "kung pao",
            "potsticker", "char siu"),
        "Japanese" to setOf("japanese", "teriyaki", "sushi", "miso", "tempura", "ramen", "yakisoba",
            "ponzu", "wasabi", "panko"),
        "Thai" to setOf("thai", "fish sauce", "lemongrass", "pad thai", "satay", "green curry",
            "red curry", "coconut milk"),
        "Indian" to setOf("indian", "curry", "masala", "garam", "turmeric", "lentil", "lentils",
            "dal", "naan", "tandoori", "samosa", "samosas", "tikka", "paneer", "basmati", "raita"),
        "Greek" to setOf("greek", "feta", "tzatziki", "gyro", "gyros", "spanakopita", "moussaka"),
        "French" to setOf("french", "bechamel", "ratatouille", "coq au vin", "bouillabaisse",
            "cassoulet", "gruyere", "bernaise", "dijon", "crepe", "crepes", "madeleine"),
        "Cajun" to setOf("cajun", "creole", "gumbo", "jambalaya", "etouffee", "andouille", "roux"),
        "German" to setOf("german", "sauerkraut", "bratwurst", "schnitzel", "spaetzle", "strudel"),
        "Korean" to setOf("korean", "kimchi", "gochujang", "bulgogi", "bibimbap"),
        "BBQ" to setOf("bbq", "barbecue", "barbeque", "ribs", "brisket", "pulled pork", "rub"),
    )

    private val PROTEIN = linkedMapOf(
        "Chicken" to setOf("chicken", "poultry", "chicken breast", "chicken thigh"),
        "Beef" to setOf("beef", "steak", "ground beef", "sirloin", "brisket", "hamburger",
            "roast beef", "pot roast", "corned beef"),
        "Pork" to setOf("pork", "bacon", "ham", "prosciutto", "pancetta", "sausage", "chorizo",
            "pork chop", "spare ribs", "pulled pork", "pork tenderloin"),
        "Turkey" to setOf("turkey", "ground turkey", "turkey breast"),
        "Seafood" to setOf("shrimp", "crab", "lobster", "clam", "clams", "mussel", "mussels",
            "scallop", "scallops", "oyster", "oysters", "crawfish", "calamari", "seafood"),
        "Fish" to setOf("fish", "salmon", "tuna", "cod", "tilapia", "halibut", "trout", "catfish",
            "anchovy", "anchovies", "sardine", "sardines", "whitefish", "mahi"),
        "Lamb" to setOf("lamb", "mutton"),
    )

    private val MEAT_WORDS = PROTEIN.values.flatten().toSet()

    private val STAPLES = setOf("rice", "beans", "bean", "potato", "potatoes", "tofu",
        "lentil", "lentils", "quinoa", "couscous", "barley")

    fun autoTags(title: String, ingredientLines: List<String>): List<String> {
        // "fish sauce" would otherwise read as fish; drop the phrase before
        // any matching happens.
        val hay = ("$title ${ingredientLines.joinToString(" ")}")
            .lowercase().replace("fish sauce", " ")
        if (hay.isBlank()) return emptyList()
        val tokens = hay.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.toSet()
        val titleTokens = title.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.toSet()
        val out = LinkedHashSet<String>()

        fun matches(words: Set<String>): Boolean = words.any { w ->
            if (w.contains(' ')) hay.contains(w) else w in tokens
        }
        fun titleMatches(words: Set<String>): Boolean = words.any { w ->
            if (w.contains(' ')) title.lowercase().contains(w) else w in titleTokens
        }

        val dish = DISH.entries.firstOrNull { titleMatches(it.value) }?.key
        if (dish != null) {
            out.add(dish)
        } else if (PROTEIN.values.any { matches(it) } || STAPLES.any { it in tokens }) {
            out.add("Main")
        }

        CUISINE.entries.firstOrNull { titleMatches(it.value) || matches(it.value) }
            ?.let { out.add(it.key) }
        PROTEIN.entries.firstOrNull { titleMatches(it.value) || matches(it.value) }
            ?.let { out.add(it.key) }

        if (out.none { it in setOf("Dessert", "Drink", "Snack") } &&
            MEAT_WORDS.none { it in tokens }
        ) {
            out.add("Vegetarian")
        }
        return out.take(4).toList()
    }
}
