package com.vyorasound.app.network

data class InnerTubeClientProfile(
    val clientName: String,
    val clientVersion: String,
    val clientIdHeader: String,
    val userAgent: String,
    val playerEndpointUrl: String = "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false",
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val androidSdkVersion: Int? = null,
    val platform: String? = null,
    val clientScreen: String? = null,
    val supportsHtml5Preference: Boolean = false,
    val requiresOriginReferer: Boolean = false,
    val thirdPartyEmbedUrl: String? = null,
    val isNativeClient: Boolean = true
) {
    fun buildHeaders(
        visitorData: String? = null,
        authHeaders: Map<String, String> = emptyMap()
    ): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        put("Content-Type", "application/json")
        put("Accept", "*/*")
        put("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        put("X-YouTube-Client-Name", clientIdHeader)
        put("X-YouTube-Client-Version", clientVersion)
        put("X-Goog-Api-Format-Version", "2")
        if (!visitorData.isNullOrBlank()) {
            put("X-Goog-Visitor-Id", visitorData)
        }
        if (requiresOriginReferer) {
            put("Origin", "https://music.youtube.com")
            put("Referer", "https://music.youtube.com/")
        }
        if (authHeaders.isNotEmpty() && !isNativeClient) {
            putAll(authHeaders)
        }
    }
}

object InnerTubeClientConfig {
    // Prioritize Indonesian music charts, home feed, and search relevance
    // while still supporting international artists and songs.
    val currentHl: String = "id"
    val currentGl: String = "ID"

    val webRemix = InnerTubeClientProfile(
        clientName = "WEB_REMIX",
        clientVersion = "1.20250317.01.00",
        clientIdHeader = "67",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
        playerEndpointUrl = "https://music.youtube.com/youtubei/v1/player?prettyPrint=false",
        platform = "DESKTOP",
        supportsHtml5Preference = true,
        requiresOriginReferer = true,
        isNativeClient = false
    )

    val androidVr = InnerTubeClientProfile(
        clientName = "ANDROID_VR",
        clientVersion = "1.65.10",
        clientIdHeader = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; Quest 3 Build/SQ3A.220605.009.A1) gzip",
        playerEndpointUrl = "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        osName = "Android",
        osVersion = "12L",
        androidSdkVersion = 32,
        supportsHtml5Preference = false,
        isNativeClient = true
    )

    val tvHtml5 = InnerTubeClientProfile(
        clientName = "TVHTML5",
        clientVersion = "7.20250319.10.00",
        clientIdHeader = "7",
        userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
        playerEndpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
        platform = "TV",
        supportsHtml5Preference = true,
        requiresOriginReferer = false,
        isNativeClient = false
    )

    val android = InnerTubeClientProfile(
        clientName = "ANDROID",
        clientVersion = "20.10.38",
        clientIdHeader = "3",
        userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip",
        playerEndpointUrl = "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false",
        deviceMake = "Google",
        deviceModel = "Pixel 8",
        osName = "Android",
        osVersion = "14",
        androidSdkVersion = 34,
        supportsHtml5Preference = false,
        isNativeClient = true
    )

    val ios = InnerTubeClientProfile(
        clientName = "IOS",
        clientVersion = "20.10.4",
        clientIdHeader = "5",
        userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
        playerEndpointUrl = "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false",
        deviceMake = "Apple",
        deviceModel = "iPhone16,2",
        osName = "iPhone",
        osVersion = "18.3.2.22D82",
        supportsHtml5Preference = false,
        isNativeClient = true
    )

    val androidMusic = InnerTubeClientProfile(
        clientName = "ANDROID_MUSIC",
        clientVersion = "7.29.52",
        clientIdHeader = "21",
        userAgent = "com.google.android.apps.youtube.music/7.29.52 (Linux; U; Android 14) gzip",
        playerEndpointUrl = "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false",
        deviceMake = "Google",
        deviceModel = "Pixel 8",
        osName = "Android",
        osVersion = "14",
        androidSdkVersion = 34,
        supportsHtml5Preference = false,
        isNativeClient = true
    )

    val webEmbedded = InnerTubeClientProfile(
        clientName = "WEB_EMBEDDED_PLAYER",
        clientVersion = "1.20250310.01.00",
        clientIdHeader = "56",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
        playerEndpointUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
        platform = "DESKTOP",
        clientScreen = "EMBED",
        supportsHtml5Preference = true,
        thirdPartyEmbedUrl = "https://www.youtube.com/",
        isNativeClient = false
    )

    val playerFallbackOrder: List<InnerTubeClientProfile> = listOf(
        androidVr,
        tvHtml5,
        android,
        ios,
        androidMusic,
        webEmbedded,
        webRemix
    )

    fun playerFallbackOrder(isAuthenticated: Boolean): List<InnerTubeClientProfile> {
        return if (isAuthenticated) {
            listOf(webRemix, tvHtml5, androidVr, android, ios, androidMusic, webEmbedded)
        } else {
            playerFallbackOrder
        }
    }
}
