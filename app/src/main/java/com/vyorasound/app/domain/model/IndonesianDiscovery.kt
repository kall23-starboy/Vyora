package com.vyorasound.app.domain.model

object IndonesianDiscovery {
    const val CATEGORY_BROWSE_PREFIX = "vyora_cat:"

    fun buildCategoryBrowseId(searchQuery: String): String = "$CATEGORY_BROWSE_PREFIX$searchQuery"

    fun extractCategoryQuery(browseId: String): String? =
        if (browseId.startsWith(CATEGORY_BROWSE_PREFIX)) {
            browseId.removePrefix(CATEGORY_BROWSE_PREFIX)
        } else {
            null
        }

    val categories: List<MoodCategory> = listOf(
        MoodCategory(
            title = "Pop Indonesia",
            browseId = buildCategoryBrowseId("Pop Indonesia Hits"),
            accentColor = 0xFFB69CFFL
        ),
        MoodCategory(
            title = "Indie Indonesia",
            browseId = buildCategoryBrowseId("Indie Indonesia Hindia Nadin Amizah"),
            accentColor = 0xFF8D75E8L
        ),
        MoodCategory(
            title = "Rock Indonesia",
            browseId = buildCategoryBrowseId("Rock Indonesia Dewa 19 Sheila On 7 NOAH"),
            accentColor = 0xFFFF6B8BL
        ),
        MoodCategory(
            title = "Dangdut",
            browseId = buildCategoryBrowseId("Dangdut Koplo Hits Indonesia"),
            accentColor = 0xFFF59E0BL
        ),
        MoodCategory(
            title = "Pop Jawa",
            browseId = buildCategoryBrowseId("Pop Jawa Hits Denny Caknan Guyon Waton"),
            accentColor = 0xFF38BDF8L
        ),
        MoodCategory(
            title = "Lagu Galau",
            browseId = buildCategoryBrowseId("Lagu Galau Indonesia Bernadya Mahalini Juicy Luicy"),
            accentColor = 0xFF818CF8L
        ),
        MoodCategory(
            title = "Lagu Viral Indonesia",
            browseId = buildCategoryBrowseId("Lagu Viral Indonesia Terbaru"),
            accentColor = 0xFFEC4899L
        ),
        MoodCategory(
            title = "Lagu Nostalgia",
            browseId = buildCategoryBrowseId("Lagu Nostalgia Indonesia 90an 2000an"),
            accentColor = 0xFF34D399L
        ),
        MoodCategory(
            title = "Hip-Hop Indonesia",
            browseId = buildCategoryBrowseId("Hip Hop Rap Indonesia"),
            accentColor = 0xFFA78BFAL
        ),
        MoodCategory(
            title = "R&B Indonesia",
            browseId = buildCategoryBrowseId("R&B Soul Indonesia"),
            accentColor = 0xFF60A5FAL
        ),
        MoodCategory(
            title = "Lagu Romantis",
            browseId = buildCategoryBrowseId("Lagu Romantis Indonesia Tulus Tiara Andini"),
            accentColor = 0xFFFB7185L
        ),
        MoodCategory(
            title = "Soundtrack Indonesia",
            browseId = buildCategoryBrowseId("OST Soundtrack Film Indonesia Populer"),
            accentColor = 0xFF2DD4BFL
        )
    )

    val featuredArtistsQueries: List<String> = listOf(
        "Hindia",
        "Nadin Amizah",
        "Tulus",
        "Bernadya",
        "Mahalini",
        "Juicy Luicy",
        "Dewa 19",
        "Sheila On 7",
        "NOAH",
        "Fourtwnty",
        "Pamungkas",
        "Sal Priadi",
        "Fiersa Besari",
        "Lyodra",
        "Tiara Andini",
        "Feby Putri"
    )

    val searchQuickChips: List<String> = listOf(
        "Hindia",
        "Bernadya",
        "Nadin Amizah",
        "Tulus",
        "Mahalini",
        "Juicy Luicy",
        "Dewa 19",
        "Sheila On 7",
        "Pamungkas",
        "Lagu Galau",
        "Pop Jawa",
        "Top Global"
    )
}
