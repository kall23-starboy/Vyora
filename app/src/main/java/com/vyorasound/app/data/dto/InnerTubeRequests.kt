package com.vyorasound.app.data.dto

import com.vyorasound.app.network.InnerTubeClientConfig
import com.vyorasound.app.network.InnerTubeClientProfile
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClientContextDto(
    val clientName: String,
    val clientVersion: String,
    val hl: String,
    val gl: String,
    val visitorData: String? = null,
    val clientScreen: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val androidSdkVersion: Int? = null,
    val platform: String? = null,
    val utcOffsetMinutes: Int = 0
)

@JsonClass(generateAdapter = true)
data class UserContextDto(
    val lockedSafetyMode: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ThirdPartyContextDto(
    val embedUrl: String
)

@JsonClass(generateAdapter = true)
data class InnerTubeContextDto(
    val client: ClientContextDto,
    val user: UserContextDto = UserContextDto(),
    val thirdParty: ThirdPartyContextDto? = null
)

@JsonClass(generateAdapter = true)
data class VisitorIdRequestDto(
    val context: InnerTubeContextDto
)

@JsonClass(generateAdapter = true)
data class BrowseRequestDto(
    val context: InnerTubeContextDto,
    val browseId: String? = null,
    val params: String? = null
)

@JsonClass(generateAdapter = true)
data class SearchRequestDto(
    val context: InnerTubeContextDto,
    val query: String? = null,
    val params: String? = null
)

@JsonClass(generateAdapter = true)
data class SearchSuggestionsRequestDto(
    val context: InnerTubeContextDto,
    val input: String
)

@JsonClass(generateAdapter = true)
data class NextRequestDto(
    val context: InnerTubeContextDto,
    val videoId: String,
    val isAudioOnly: Boolean = true
)

@JsonClass(generateAdapter = true)
data class PlaybackContextDto(
    val contentPlaybackContext: ContentPlaybackContextDto = ContentPlaybackContextDto()
)

@JsonClass(generateAdapter = true)
data class ContentPlaybackContextDto(
    val html5Preference: String = "HTML5_PREF_WANTS"
)

@JsonClass(generateAdapter = true)
data class PlayerRequestDto(
    val context: InnerTubeContextDto,
    val videoId: String,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
    val params: String? = null,
    val playbackContext: PlaybackContextDto? = null
)

fun InnerTubeClientProfile.toContextDto(visitorData: String? = null): InnerTubeContextDto = InnerTubeContextDto(
    client = ClientContextDto(
        clientName = clientName,
        clientVersion = clientVersion,
        hl = InnerTubeClientConfig.currentHl,
        gl = InnerTubeClientConfig.currentGl,
        visitorData = visitorData?.takeIf { it.isNotBlank() },
        clientScreen = clientScreen,
        deviceMake = deviceMake,
        deviceModel = deviceModel,
        osName = osName,
        osVersion = osVersion,
        androidSdkVersion = androidSdkVersion,
        platform = platform
    ),
    thirdParty = thirdPartyEmbedUrl?.let { ThirdPartyContextDto(embedUrl = it) }
)
