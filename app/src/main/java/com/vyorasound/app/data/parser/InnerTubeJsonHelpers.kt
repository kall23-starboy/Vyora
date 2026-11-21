package com.vyorasound.app.data.parser

import com.vyorasound.app.domain.model.Album
import com.vyorasound.app.domain.model.Artist
import com.vyorasound.app.domain.model.MediaShelfItem
import com.vyorasound.app.domain.model.MoodCategory
import com.vyorasound.app.domain.model.Playlist
import com.vyorasound.app.domain.model.Song
import com.vyorasound.app.utils.ImageUrlUtils
import com.vyorasound.app.utils.TimeUtils
import org.json.JSONArray
import org.json.JSONObject

internal data class NavEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null,
    val browseId: String? = null,
    val params: String? = null,
    val pageType: String? = null
)

internal object InnerTubeJsonHelpers {
    private val durationRegex = Regex("""^\d{1,2}(:\d{2}){1,2}$""")
    private val yearRegex = Regex("""^(19|20)\d{2}$""")

    fun JSONObject.optObj(key: String): JSONObject? = optJSONObject(key)

    fun JSONObject.optArr(key: String): JSONArray? = optJSONArray(key)

    fun JSONArray.objects(): List<JSONObject> = buildList {
        for (i in 0 until length()) {
            optJSONObject(i)?.let { add(it) }
        }
    }

    fun extractRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        obj.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = obj.optArr("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            sb.append(run.optString("text"))
        }
        return sb.toString().trim()
    }

    fun extractRunsObjects(obj: JSONObject?): List<JSONObject> =
        obj?.optArr("runs")?.objects().orEmpty()

    fun extractBestThumbnail(obj: JSONObject?, targetSize: Int = 544): String {
        if (obj == null) return ""
        val thumbnails = obj.optArr("thumbnails")
            ?: obj.optObj("musicThumbnailRenderer")?.optObj("thumbnail")?.optArr("thumbnails")
            ?: obj.optObj("thumbnail")?.optArr("thumbnails")
            ?: obj.optObj("croppedSquareThumbnailRenderer")?.optObj("thumbnail")?.optArr("thumbnails")
            ?: return ""

        var bestUrl = ""
        var bestWidth = -1
        for (i in 0 until thumbnails.length()) {
            val item = thumbnails.optJSONObject(i) ?: continue
            val url = item.optString("url")
            val width = item.optInt("width", 0)
            if (url.isNotBlank() && width >= bestWidth) {
                bestWidth = width
                bestUrl = url
            }
        }
        return ImageUrlUtils.enhanceThumbnailUrl(bestUrl, targetSize)
    }

    fun extractNavEndpoint(endpoint: JSONObject?): NavEndpoint? {
        if (endpoint == null) return null
        val watch = endpoint.optObj("watchEndpoint")
        if (watch != null) {
            val videoId = watch.optString("videoId").takeIf { it.isNotBlank() }
            val playlistId = watch.optString("playlistId").takeIf { it.isNotBlank() }
            if (videoId != null || playlistId != null) {
                return NavEndpoint(videoId = videoId, playlistId = playlistId)
            }
        }
        val watchPlaylist = endpoint.optObj("watchPlaylistEndpoint")
        if (watchPlaylist != null) {
            val playlistId = watchPlaylist.optString("playlistId").takeIf { it.isNotBlank() }
            if (playlistId != null) {
                return NavEndpoint(playlistId = playlistId, browseId = "VL$playlistId")
            }
        }
        val browse = endpoint.optObj("browseEndpoint")
        if (browse != null) {
            val browseId = browse.optString("browseId").takeIf { it.isNotBlank() }
            val params = browse.optString("params").takeIf { it.isNotBlank() }
            val pageType = browse
                .optObj("browseEndpointContextSupportedConfigs")
                ?.optObj("browseEndpointContextMusicConfig")
                ?.optString("pageType")
                ?.takeIf { it.isNotBlank() }
            if (browseId != null) {
                return NavEndpoint(browseId = browseId, params = params, pageType = pageType)
            }
        }
        return null
    }

    fun hasExplicitBadge(badgesArr: JSONArray?): Boolean {
        if (badgesArr == null) return false
        for (i in 0 until badgesArr.length()) {
            val iconType = badgesArr.optJSONObject(i)
                ?.optObj("musicInlineBadgeRenderer")
                ?.optObj("icon")
                ?.optString("iconType")
            if (iconType == "MUSIC_EXPLICIT_BADGE") return true
        }
        return false
    }

    fun extractSectionContinuation(sectionOrShelf: JSONObject): String? {
        val continuations = sectionOrShelf.optArr("continuations") ?: return null
        for (i in 0 until continuations.length()) {
            val token = continuations.optJSONObject(i)
                ?.optObj("nextContinuationData")
                ?.optString("continuation")
                ?.takeIf { it.isNotBlank() }
            if (token != null) return token
        }
        return null
    }

    fun parseTwoRowItem(renderer: JSONObject): MediaShelfItem? {
        val titleObj = renderer.optObj("title")
        val title = extractRunsText(titleObj)
        if (title.isBlank()) return null

        val subtitleRuns = extractRunsObjects(renderer.optObj("subtitle"))
        val subtitleFull = subtitleRuns.joinToString("") { it.optString("text") }.trim()
        val thumb = extractBestThumbnail(
            renderer.optObj("thumbnailRenderer")?.optObj("musicThumbnailRenderer")
                ?: renderer.optObj("thumbnailRenderer")
        )
        val isExplicit = hasExplicitBadge(renderer.optArr("subtitleBadges"))

        val nav = extractNavEndpoint(renderer.optObj("navigationEndpoint"))
            ?: extractNavEndpoint(
                renderer.optObj("thumbnailOverlay")
                    ?.optObj("musicItemThumbnailOverlayRenderer")
                    ?.optObj("content")
                    ?.optObj("musicPlayButtonRenderer")
                    ?.optObj("playNavigationEndpoint")
            )
            ?: return null

        // Determine artist/album metadata from subtitle runs
        var artistName = ""
        var artistId: String? = null
        var year: String? = null
        for (run in subtitleRuns) {
            val text = run.optString("text").trim()
            if (text.isBlank() || text == "•" || text == "·" || text == ",") continue
            val runNav = extractNavEndpoint(run.optObj("navigationEndpoint"))
            if (runNav?.browseId?.startsWith("UC") == true && artistId == null) {
                artistName = text
                artistId = runNav.browseId
            } else if (yearRegex.matches(text)) {
                year = text
            }
        }
        if (artistName.isBlank()) {
            val cleanedParts = subtitleFull.split("•", "·")
                .map { it.trim() }
                .filter {
                    it.isNotBlank() &&
                        !it.equals("Song", true) &&
                        !it.equals("Album", true) &&
                        !it.equals("EP", true) &&
                        !it.equals("Single", true) &&
                        !it.equals("Playlist", true) &&
                        !it.equals("Video", true) &&
                        !yearRegex.matches(it)
                }
            artistName = cleanedParts.firstOrNull().orEmpty()
        }

        if (nav.videoId != null) {
            return MediaShelfItem.SongItem(
                Song(
                    id = nav.videoId,
                    title = title,
                    artist = artistName.ifBlank { subtitleFull.ifBlank { "Unknown Artist" } },
                    artistId = artistId,
                    thumbnailUrl = thumb,
                    isExplicit = isExplicit
                )
            )
        }

        val browseId = nav.browseId ?: return null
        val pageType = nav.pageType
        return when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                browseId.startsWith("UC") -> {
                MediaShelfItem.ArtistItem(
                    Artist(
                        id = browseId,
                        name = title,
                        subtitle = subtitleFull,
                        thumbnailUrl = thumb,
                        browseId = browseId
                    )
                )
            }

            pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb_") -> {
                MediaShelfItem.AlbumItem(
                    Album(
                        id = browseId,
                        title = title,
                        artist = artistName.ifBlank { subtitleFull },
                        artistId = artistId,
                        year = year,
                        thumbnailUrl = thumb,
                        isExplicit = isExplicit,
                        browseId = browseId
                    )
                )
            }

            else -> {
                MediaShelfItem.PlaylistItem(
                    Playlist(
                        id = browseId,
                        title = title,
                        author = artistName.ifBlank { subtitleFull },
                        thumbnailUrl = thumb,
                        browseId = browseId
                    )
                )
            }
        }
    }

    fun parseResponsiveListItem(
        renderer: JSONObject,
        fallbackThumbnail: String = "",
        fallbackAlbum: String? = null,
        fallbackAlbumId: String? = null,
        fallbackArtist: String? = null
    ): MediaShelfItem? {
        val flexColumns = renderer.optArr("flexColumns")?.objects().orEmpty()
        if (flexColumns.isEmpty()) return null

        val firstColText = flexColumns.getOrNull(0)
            ?.optObj("musicResponsiveListItemFlexColumnRenderer")
            ?.optObj("text")
        val title = extractRunsText(firstColText)
        if (title.isBlank()) return null

        val thumb = extractBestThumbnail(renderer.optObj("thumbnail"))
            .ifBlank { fallbackThumbnail }
        val isExplicit = hasExplicitBadge(renderer.optArr("badges"))

        val videoId = renderer.optObj("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: extractNavEndpoint(
                renderer.optObj("overlay")
                    ?.optObj("musicItemThumbnailOverlayRenderer")
                    ?.optObj("content")
                    ?.optObj("musicPlayButtonRenderer")
                    ?.optObj("playNavigationEndpoint")
            )?.videoId
            ?: extractRunsObjects(firstColText).firstNotNullOfOrNull {
                extractNavEndpoint(it.optObj("navigationEndpoint"))?.videoId
            }

        val itemNav = extractNavEndpoint(renderer.optObj("navigationEndpoint"))
            ?: extractRunsObjects(firstColText).firstNotNullOfOrNull {
                extractNavEndpoint(it.optObj("navigationEndpoint"))
            }

        val secondaryRuns = flexColumns.drop(1).flatMap { col ->
            extractRunsObjects(col.optObj("musicResponsiveListItemFlexColumnRenderer")?.optObj("text"))
        }
        val fixedDuration = renderer.optArr("fixedColumns")?.objects()?.firstOrNull()
            ?.optObj("musicResponsiveListItemFixedColumnRenderer")
            ?.optObj("text")
            ?.let { extractRunsText(it) }
            .orEmpty()

        var artistName = ""
        var artistId: String? = null
        var albumName: String? = fallbackAlbum
        var albumId: String? = fallbackAlbumId
        var durationText = fixedDuration
        var playCount: String? = null

        val plainTokens = mutableListOf<String>()
        for (run in secondaryRuns) {
            val text = run.optString("text").trim()
            if (text.isBlank() || text == "•" || text == "·" || text == ",") continue
            val runNav = extractNavEndpoint(run.optObj("navigationEndpoint"))
            when {
                runNav?.pageType == "MUSIC_PAGE_TYPE_ARTIST" || runNav?.browseId?.startsWith("UC") == true -> {
                    if (artistName.isBlank()) {
                        artistName = text
                        artistId = runNav.browseId
                    } else {
                        artistName = "$artistName, $text"
                    }
                }

                runNav?.pageType == "MUSIC_PAGE_TYPE_ALBUM" || runNav?.browseId?.startsWith("MPREb_") == true -> {
                    albumName = text
                    albumId = runNav.browseId
                }

                durationRegex.matches(text) -> {
                    durationText = text
                }

                text.contains("plays", ignoreCase = true) || text.contains("views", ignoreCase = true) -> {
                    playCount = text
                }

                !text.equals("Song", true) &&
                    !text.equals("Video", true) &&
                    !text.equals("Album", true) &&
                    !text.equals("Artist", true) &&
                    !text.equals("Playlist", true) &&
                    !text.equals("Single", true) &&
                    !text.equals("EP", true) -> {
                    plainTokens.add(text)
                }
            }
        }

        if (artistName.isBlank()) {
            artistName = plainTokens.firstOrNull { !yearRegex.matches(it) }
                ?: fallbackArtist.orEmpty()
        }

        if (videoId != null) {
            return MediaShelfItem.SongItem(
                Song(
                    id = videoId,
                    title = title,
                    artist = artistName.ifBlank { "Unknown Artist" },
                    artistId = artistId,
                    album = albumName,
                    albumId = albumId,
                    durationText = durationText,
                    durationMs = TimeUtils.parseDurationTextToMs(durationText),
                    thumbnailUrl = thumb,
                    isExplicit = isExplicit,
                    playCountText = playCount
                )
            )
        }

        val browseId = itemNav?.browseId ?: return null
        val pageType = itemNav.pageType
        return when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                browseId.startsWith("UC") -> {
                MediaShelfItem.ArtistItem(
                    Artist(
                        id = browseId,
                        name = title,
                        subtitle = plainTokens.joinToString(" • "),
                        thumbnailUrl = thumb,
                        browseId = browseId
                    )
                )
            }

            pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb_") -> {
                val year = plainTokens.firstOrNull { yearRegex.matches(it) }
                MediaShelfItem.AlbumItem(
                    Album(
                        id = browseId,
                        title = title,
                        artist = artistName.ifBlank { plainTokens.firstOrNull().orEmpty() },
                        artistId = artistId,
                        year = year,
                        thumbnailUrl = thumb,
                        isExplicit = isExplicit,
                        browseId = browseId
                    )
                )
            }

            else -> {
                MediaShelfItem.PlaylistItem(
                    Playlist(
                        id = browseId,
                        title = title,
                        author = artistName.ifBlank { plainTokens.firstOrNull().orEmpty() },
                        trackCountText = plainTokens.firstOrNull { it.contains("song", true) || it.contains("track", true) }.orEmpty(),
                        thumbnailUrl = thumb,
                        browseId = browseId
                    )
                )
            }
        }
    }

    fun parseNavigationButton(renderer: JSONObject): MoodCategory? {
        val title = extractRunsText(renderer.optObj("buttonText"))
        if (title.isBlank()) return null
        val nav = extractNavEndpoint(renderer.optObj("clickCommand")) ?: return null
        val browseId = nav.browseId ?: return null
        val stripeColor = renderer.optObj("solid")?.optLong("leftStripeColor", 0L) ?: 0L
        val accent = if (stripeColor != 0L) stripeColor else 0xFF8D75E8L
        return MoodCategory(
            title = title,
            browseId = browseId,
            params = nav.params,
            accentColor = accent
        )
    }

    fun parsePlaylistPanelVideo(renderer: JSONObject): Song? {
        val actual = renderer.optObj("playlistPanelVideoWrapperRenderer")
            ?.optObj("primaryRenderer")
            ?.optObj("playlistPanelVideoRenderer")
            ?: renderer.optObj("playlistPanelVideoRenderer")
            ?: return null

        val videoId = actual.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val title = extractRunsText(actual.optObj("title")).ifBlank { return null }
        val durationText = extractRunsText(actual.optObj("lengthText"))
        val thumb = extractBestThumbnail(actual.optObj("thumbnail"))
        val isExplicit = hasExplicitBadge(actual.optArr("badges"))

        var artistName = ""
        var artistId: String? = null
        var albumName: String? = null
        var albumId: String? = null
        val longBylineRuns = extractRunsObjects(actual.optObj("longBylineText"))
        for (run in longBylineRuns) {
            val text = run.optString("text").trim()
            if (text.isBlank() || text == "•" || text == "·") continue
            val nav = extractNavEndpoint(run.optObj("navigationEndpoint"))
            when {
                nav?.pageType == "MUSIC_PAGE_TYPE_ARTIST" || nav?.browseId?.startsWith("UC") == true -> {
                    if (artistName.isBlank()) {
                        artistName = text
                        artistId = nav.browseId
                    } else {
                        artistName = "$artistName, $text"
                    }
                }
                nav?.pageType == "MUSIC_PAGE_TYPE_ALBUM" || nav?.browseId?.startsWith("MPREb_") == true -> {
                    albumName = text
                    albumId = nav.browseId
                }
            }
        }
        if (artistName.isBlank()) {
            artistName = extractRunsText(actual.optObj("shortBylineText")).ifBlank { "Unknown Artist" }
        }

        return Song(
            id = videoId,
            title = title,
            artist = artistName,
            artistId = artistId,
            album = albumName,
            albumId = albumId,
            durationText = durationText,
            durationMs = TimeUtils.parseDurationTextToMs(durationText),
            thumbnailUrl = thumb,
            isExplicit = isExplicit
        )
    }
}
