package com.trypod33.kitchlore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun LegalPara(text: String) = Text(
    text,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(bottom = 10.dp),
)

@Composable
fun LegalRoute(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                "Legal",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionLabel("Privacy")
            LegalPara("Kitchlore keeps everything on your device. Recipes, photos, pantry items, settings and your library live in the app's private storage. There are no accounts, no sign-ins, no analytics and no data collection of any kind.")
            LegalPara("Camera captures save only inside the app and never to your gallery. Importing a recipe fetches the recipe page straight from the site it lives on. Keyless web search sends your query to public search endpoints and stores nothing about you. Uninstalling the app removes everything, permanently.")
            SectionLabel("Terms of use")
            LegalPara("Kitchlore is provided as is, with no warranty of any kind. It is a cooking organizer, and it does not give food-safety, nutrition, medical or dietary advice. Safe cooking temperatures, allergens and ingredient freshness are your responsibility; follow the guidance on packaging and from your local food-safety authority.")
            LegalPara("Recipes you import belong to their authors and are brought in for your personal use. Kitchlore is not affiliated with and does not endorse the sites, products or appliances that appear in search results. To the fullest extent permitted by law, the app's developer is not liable for any loss or damage arising from use of the app or its content.")
            SectionLabel("AI disclosure")
            LegalPara("Kitchlore uses a small AI language model that runs entirely on your device to help read photos of handwritten recipes and tidy measurements. It can misread words and amounts, so every scan and import is shown for your review before it saves, and you can edit anything. Nothing is sent to a server for processing.")
            SectionLabel("Open source and content credits")
            LegalPara("Kitchlore is built with open-source software including Kotlin, Jetpack Compose, Room, Hilt and LiteRT, all under the Apache License 2.0 or compatible licenses.")
            LegalPara("The built-in starter catalog comes from TheMealDB (themealdb.com). Thanks to the community behind it.")
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}
