package com.vyorasound.app.data.parser

import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.extractBestThumbnail
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.extractNavEndpoint
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.extractRunsObjects
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.extractRunsText
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.extractSectionContinuation
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.objects
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.optArr
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.optObj
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.parseNavigationButton
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.parsePlaylistPanelVideo
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.parseResponsiveListItem
import com.vyorasound.app.data.parser.InnerTubeJsonHelpers.parseTwoRowItem
import com.vyorasound.app.domain.model.Album
import com.vyorasound.app.domain.model.AlbumDetail
import com.vyorasound.app.domain.model.Artist
import com.vyorasound.app.domain.model.ArtistDetail
import com.vyorasound.app.domain.model.AudioFormatCandidate
import com.vyorasound.app.domain.model.ExploreFeed
import com.vyorasound.app.domain.model.HomeFeed
import com.vyorasound.app.domain.model.HomeSection
import com.vyorasound.app.domain.model.MediaShelfItem
import com.vyorasound.app.domain.model.MoodCategory
import com.vyorasound.app.domain.model.PlaybackStream
import com.vyorasound.app.domain.model.PlayerResponseAudit
import com.vyorasound.app.domain.model.Playlist
import com.vyorasound.app.domain.model.PlaylistDetail
import com.vyorasound.app.domain.model.SearchResults
import com.vyorasound.app.domain.model.SearchSuggestionItem
import com.vyorasound.app.domain.model.Song
import com.vyorasound.app.domain.model.UrlAvailability
import com.vyorasound.app.domain.model.WatchNextData
import org.json.JSONObject

object InnerTubeJsonParser {

    fun parseHomeFeed(jsonString: String): HomeFeed {
        val root = JSONObject(jsonString)
        val sectionList = root.optObj("contents")
            ?.optObj("singleColumnBrowseResultsRenderer")
            ?.optArr("tabs")?.objects()?.firstOrNull()
            ?.optObj("tabRenderer")?.optObj("content")
            ?.optObj("sectionListRenderer")
            ?: root.optObj("continuationContents")?.optObj("sectionListContinuation")

        if (sectionList == null) return HomeFeed(emptyList(), null)

        val sections = parseSectionListContents(sectionList.optArr("contents")?.objects().orEmpty())
        val continuation = extractSectionContinuation(sectionList)
        return HomeFeed(sections = sections, continuation = continuation)
    }

    fun parseBrowseSections(jsonString: String): List<HomeSection> {
        val root = JSONObject(jsonString)
        val sectionList = root.optObj("contents")
            ?.optObj("singleColumnBrowseResultsRenderer")
            ?.optArr("tabs")?.objects()?.firstOrNull()
            ?.optObj("tabRenderer")?.optObj("content")
            ?.optObj("sectionListRenderer")
            ?: root.optObj("continuationContents")?.optObj("sectionListContinuation")
            ?: return emptyList()
        return parseSectionListContents(sectionList.optArr("contents")?.objects().orEmpty())
    }

    fun parseExploreFeed(jsonString: String): ExploreFeed {
        val sections = parseBrowseSections(jsonString)
        val trendingSongs = mutableListOf<Song>()
        val newReleases = mutableListOf<Album>()
        val popularPlaylists = mutableListOf<Playlist>()
        val categories = mutableListOf<MoodCategory>()
        val extraSections = mutableListOf<HomeSection>()

        for (section in sections) {
            val lowerTitle = section.title.lowercase()
            var consumed = false
            val moods = section.items.filterIsInstance<MediaShelfItem.MoodItem>().map { it.mood }
            if (moods.isNotEmpty()) {
                categories.addAll(moods)
                consumed = true
            }
            if (lowerTitle.contains("trending") || lowerTitle.contains("top songs")) {
                trendingSongs.addAll(section.items.filterIsInstance<MediaShelfItem.SongItem>().map { it.song })
                consumed = true
            }
            if (lowerTitle.contains("new release") || lowerTitle.contains("albums")) {
                newReleases.addAll(section.items.filterIsInstance<MediaShelfItem.AlbumItem>().map { it.album })
                consumed = true
            }
            if (lowerTitle.contains("playlist") || lowerTitle.contains("podcast").not() &&
                section.items.any { it is MediaShelfItem.PlaylistItem }
            ) {
                popularPlaylists.addAll(section.items.filterIsInstance<MediaShelfItem.PlaylistItem>().map { it.playlist })
                consumed = true
            }
            if (!consumed) {
                extraSections.add(section)
            }
        }

        // Fill any gaps from remaining section items so Explore stays rich
        if (trendingSongs.isEmpty()) {
            trendingSongs.addAll(
                sections.flatMap { it.items }.filterIsInstance<MediaShelfItem.SongItem>().map { it.song }.take(20)
            )
        }
        if (newReleases.isEmpty()) {
            newReleases.addAll(
                sections.flatMap { it.items }.filterIsInstance<MediaShelfItem.AlbumItem>().map { it.album }.take(15)
            )
        }
        if (popularPlaylists.isEmpty()) {
            popularPlaylists.addAll(
                sections.flatMap { it.items }.filterIsInstance<MediaShelfItem.PlaylistItem>().map { it.playlist }.take(15)
            )
        }

        return ExploreFeed(
            trendingSongs = trendingSongs.distinctBy { it.id },
            newReleases = newReleases.distinctBy { it.browseId },
            popularPlaylists = popularPlaylists.distinctBy { it.browseId },
            categories = categories.distinctBy { "${it.browseId}_${it.params}" },
            chartSongs = emptyList(),
            extraSections = extraSections
        )
    }

    fun parseChartsSongs(jsonString: String): List<Song> {
        val sections = parseBrowseSections(jsonString)
        return sections.flatMap { it.items }
            .filterIsInstance<MediaShelfItem.SongItem>()
            .map { it.song }
            .distinctBy { it.id }
    }

    fun parseSearchSuggestions(jsonString: String): List<SearchSuggestionItem> {
        val root = JSONObject(jsonString)
        val contents = root.optArr("contents")?.objects().orEmpty()
        val result = mutableListOf<SearchSuggestionItem>()

        for (container in contents) {
            val section = container.optObj("searchSuggestionsSectionRenderer") ?: continue
            val items = section.optArr("contents")?.objects().orEmpty()
            for (item in items) {
                item.optObj("searchSuggestionRenderer")?.let { suggestion ->
                    val queryText = extractRunsText(suggestion.optObj("suggestion"))
                    if (queryText.isNotBlank()) {
                        result.add(SearchSuggestionItem.QuerySuggestion(queryText))
                    }
                }
                item.optObj("musicResponsiveListItemRenderer")?.let { listItem ->
                    parseResponsiveListItem(listItem)?.let { mediaItem ->
                        result.add(SearchSuggestionItem.DirectMediaSuggestion(mediaItem))
                    }
                }
            }
        }
        return result
    }

    fun parseSearchResults(jsonString: String): SearchResults {
        val root = JSONObject(jsonString)
        val songs = mutableListOf<Song>()
        val albums = mutableListOf<Album>()
        val artists = mutableListOf<Artist>()
        val playlists = mutableListOf<Playlist>()
        var continuation: String? = null

        val sectionContents = root.optObj("contents")
            ?.optObj("tabbedSearchResultsRenderer")
            ?.optArr("tabs")?.objects()?.firstOrNull()
            ?.optObj("tabRenderer")?.optObj("content")
            ?.optObj("sectionListRenderer")?.optArr("contents")?.objects()
            ?: root.optObj("continuationContents")?.optObj("musicShelfContinuation")?.let { listOf(JSONObject().put("musicShelfRenderer", it)) }
            ?: emptyList()

        for (sectionObj in sectionContents) {
            sectionObj.optObj("musicCardShelfRenderer")?.let { cardShelf ->
                val title = extractRunsText(cardShelf.optObj("title"))
                val subtitle = extractRunsText(cardShelf.optObj("subtitle"))
                val thumb = extractBestThumbnail(cardShelf.optObj("thumbnail")?.optObj("musicThumbnailRenderer"))
                val nav = extractNavEndpoint(cardShelf.optObj("onTap"))
                if (title.isNotBlank() && nav != null) {
                    when {
                        nav.videoId != null -> songs.add(
                            Song(
                                id = nav.videoId,
                                title = title,
                                artist = subtitle.split("•").lastOrNull()?.trim().orEmpty().ifBlank { "Artist" },
                                thumbnailUrl = thumb
                            )
                        )
                        nav.pageType == "MUSIC_PAGE_TYPE_ARTIST" || nav.browseId?.startsWith("UC") == true -> {
                            nav.browseId?.let { id ->
                                artists.add(Artist(id = id, name = title, subtitle = subtitle, thumbnailUrl = thumb, browseId = id))
                            }
                        }
                        nav.pageType == "MUSIC_PAGE_TYPE_ALBUM" || nav.browseId?.startsWith("MPREb_") == true -> {
                            nav.browseId?.let { id ->
                                albums.add(Album(id = id, title = title, artist = subtitle, thumbnailUrl = thumb, browseId = id))
                            }
                        }
                        nav.browseId != null -> {
                            playlists.add(Playlist(id = nav.browseId, title = title, author = subtitle, thumbnailUrl = thumb, browseId = nav.browseId))
                        }
                    }
                }
                cardShelf.optArr("contents")?.objects().orEmpty().forEach { child ->
                    child.optObj("musicResponsiveListItemRenderer")?.let { listItem ->
                        when (val parsed = parseResponsiveListItem(listItem)) {
                            is MediaShelfItem.SongItem -> songs.add(parsed.song)
                            is MediaShelfItem.AlbumItem -> albums.add(parsed.album)
                            is MediaShelfItem.ArtistItem -> artists.add(parsed.artist)
                            is MediaShelfItem.PlaylistItem -> playlists.add(parsed.playlist)
                            else -> Unit
                        }
                    }
                }
            }

            sectionObj.optObj("musicShelfRenderer")?.let { shelf ->
                if (continuation == null) {
                    continuation = extractSectionContinuation(shelf)
                }
                shelf.optArr("contents")?.objects().orEmpty().forEach { child ->
                    child.optObj("musicResponsiveListItemRenderer")?.let { listItem ->
                        when (val parsed = parseResponsiveListItem(listItem)) {
                            is MediaShelfItem.SongItem -> songs.add(parsed.song)
                            is MediaShelfItem.AlbumItem -> albums.add(parsed.album)
                            is MediaShelfItem.ArtistItem -> artists.add(parsed.artist)
                            is MediaShelfItem.PlaylistItem -> playlists.add(parsed.playlist)
                            else -> Unit
                        }
                    }
                }
            }
        }

        return SearchResults(
            songs = songs.distinctBy { it.id },
            albums = albums.distinctBy { it.browseId },
            artists = artists.distinctBy { it.browseId },
            playlists = playlists.distinctBy { it.browseId },
            continuation = continuation
        )
    }

    fun parseArtistDetail(browseId: String, jsonString: String): ArtistDetail {
        val root = JSONObject(jsonString)
        val header = root.optObj("header")?.optObj("musicImmersiveHeaderRenderer")
            ?: root.optObj("header")?.optObj("musicVisualHeaderRenderer")
            ?: root.optObj("header")?.optObj("musicHeaderRenderer")

        val name = extractRunsText(header?.optObj("title")).ifBlank { "Artist" }
        val description = extractRunsText(header?.optObj("description"))
        val subscriberCount = extractRunsText(
            header?.optObj("subscriptionButton")
                ?.optObj("subscribeButtonRenderer")
                ?.optObj("subscriberCountText")
        )
        val thumbnailUrl = extractBestThumbnail(
            header?.optObj("thumbnail")?.optObj("musicThumbnailRenderer")
                ?: header?.optObj("foregroundThumbnail")?.optObj("musicThumbnailRenderer"),
            targetSize = 720
        )

        val sectionContents = root.optObj("contents")
            ?.optObj("singleColumnBrowseResultsRenderer")
            ?.optArr("tabs")?.objects()?.firstOrNull()
            ?.optObj("tabRenderer")?.optObj("content")
            ?.optObj("sectionListRenderer")?.optArr("contents")?.objects()
            .orEmpty()

        val topSongs = mutableListOf<Song>()
        val albums = mutableListOf<Album>()
        val singles = mutableListOf<Album>()
        val relatedArtists = mutableListOf<Artist>()
        var songsBrowseId: String? = null
        var songsParams: String? = null

        for (sec in sectionContents) {
            sec.optObj("musicShelfRenderer")?.let { shelf ->
                val shelfTitle = extractRunsText(shelf.optObj("title")).lowercase()
                val bottomNav = extractNavEndpoint(
                    shelf.optObj("bottomEndpoint")
                        ?: shelf.optObj("moreContentButton")?.optObj("buttonRenderer")?.optObj("navigationEndpoint")
                )
                if (shelfTitle.contains("song") && bottomNav?.browseId != null) {
                    songsBrowseId = bottomNav.browseId
                    songsParams = bottomNav.params
                }
                shelf.optArr("contents")?.objects().orEmpty().forEach { item ->
                    item.optObj("musicResponsiveListItemRenderer")?.let { renderer ->
                        (parseResponsiveListItem(renderer, fallbackArtist = name) as? MediaShelfItem.SongItem)?.let {
                            topSongs.add(it.song)
                        }
                    }
                }
            }

            sec.optObj("musicCarouselShelfRenderer")?.let { carousel ->
                val title = extractRunsText(
                    carousel.optObj("header")?.optObj("musicCarouselShelfBasicHeaderRenderer")?.optObj("title")
                ).lowercase()
                carousel.optArr("contents")?.objects().orEmpty().forEach { item ->
                    item.optObj("musicTwoRowItemRenderer")?.let { twoRow ->
                        when (val parsed = parseTwoRowItem(twoRow)) {
                            is MediaShelfItem.AlbumItem -> {
                                val enriched = parsed.album.copy(
                                    artist = parsed.album.artist.ifBlank { name },
                                    artistId = parsed.album.artistId ?: browseId
                                )
                                if (title.contains("single") || title.contains("ep")) {
                                    singles.add(enriched)
                                } else {
                                    albums.add(enriched)
                                }
                            }
                            is MediaShelfItem.ArtistItem -> relatedArtists.add(parsed.artist)
                            is MediaShelfItem.SongItem -> topSongs.add(parsed.song)
                            else -> Unit
                        }
                    }
                }
            }
        }

        return ArtistDetail(
            id = browseId,
            name = name,
            description = description,
            subscriberCount = subscriberCount,
            thumbnailUrl = thumbnailUrl,
            topSongs = topSongs.distinctBy { it.id },
            albums = albums.distinctBy { it.browseId },
            singles = singles.distinctBy { it.browseId },
            relatedArtists = relatedArtists.distinctBy { it.browseId },
            songsBrowseId = songsBrowseId,
            songsParams = songsParams
        )
    }

    fun parseAlbumDetail(browseId: String, jsonString: String): AlbumDetail {
        val root = JSONObject(jsonString)

        // Support both twoColumnBrowseResultsRenderer (newer) and singleColumnBrowseResultsRenderer
        val twoCol = root.optObj("contents")?.optObj("twoColumnBrowseResultsRenderer")
        val responsiveHeader = twoCol
            ?.optArr("tabs")?.objects()?.firstOrNull()
            ?.optObj("tabRenderer")?.optObj("content")
            ?.optObj("sectionListRenderer")?.optArr("contents")?.objects()?.firstOrNull()
            ?.optObj("musicResponsiveHeaderRenderer")

        val detailHeader = root.optObj("header")?.optObj("musicDetailHeaderRenderer")

        val headerObj = responsiveHeader ?: detailHeader
        val title = extractRunsText(headerObj?.optObj("title")).ifBlank { "Album" }
        val thumbnailUrl = extractBestThumbnail(
            headerObj?.optObj("thumbnail")?.optObj("musicThumbnailRenderer")
                ?: headerObj?.optObj("thumbnail")?.optObj("croppedSquareThumbnailRenderer"),
            targetSize = 720
        )
        val description = extractRunsText(
            headerObj?.optObj("description")?.optObj("musicDescriptionShelfRenderer")?.optObj("description")
                ?: headerObj?.optObj("description")
        )

        val subtitleRuns = extractRunsObjects(headerObj?.optObj("subtitle")) +
            extractRunsObjects(headerObj?.optObj("straplineTextOne"))
        var artist = ""
        var artistId: String? = null
        var year: String? = null
        for (run in subtitleRuns) {
            val text = run.optString("text").trim()
            if (text.isBlank() || text == "•" || text == "·") continue
            val nav = extractNavEndpoint(run.optObj("navigationEndpoint"))
            if (nav?.browseId?.startsWith("UC") == true || nav?.pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                if (artist.isBlank()) {
                    artist = text
                    artistId = nav.browseId
                }
            } else if (Regex("""^(19|20)\d{2}$""").matches(text)) {
                year = text
            } else if (artist.isBlank() &&
                !text.equals("Album", true) &&
                !text.equals("EP", true) &&
                !text.equals("Single", true)
            ) {
                artist = text
            }
        }
        val durationText = extractRunsText(headerObj?.optObj("secondSubtitle"))

        val trackShelfContents = twoCol
            ?.optObj("secondaryContents")
            ?.optObj("sectionListRenderer")
            ?.optArr("contents")?.objects()?.firstOrNull()
            ?.let { it.optObj("musicShelfRenderer") ?: it.optObj("musicPlaylistShelfRenderer") }
            ?.optArr("contents")?.objects()
            ?: root.optObj("contents")
                ?.optObj("singleColumnBrowseResultsRenderer")
                ?.optArr("tabs")?.objects()?.firstOrNull()
                ?.optObj("tabRenderer")?.optObj("content")
                ?.optObj("sectionListRenderer")?.optArr("contents")?.objects()?.firstOrNull()
                ?.optObj("musicShelfRenderer")
                ?.optArr("contents")?.objects()
            ?: emptyList()

        val songs = trackShelfContents.mapNotNull { item ->
            item.optObj("musicResponsiveListItemRenderer")?.let { renderer ->
                (parseResponsiveListItem(
                    renderer = renderer,
                    fallbackThumbnail = thumbnailUrl,
                    fallbackAlbum = title,
                    fallbackAlbumId = browseId,
                    fallbackArtist = artist
                ) as? MediaShelfItem.SongItem)?.song
            }
        }

        return AlbumDetail(
            id = browseId,
            title = title,
            artist = artist.ifBlank { "Various Artists" },
            artistId = artistId,
            year = year,
            description = description,
            thumbnailUrl = thumbnailUrl,
            songs = songs,
            durationText = durationText
        )
    }

    fun parsePlaylistDetail(browseId: String, jsonString: String): PlaylistDetail {
        val root = JSONObject(jsonString)
        val twoCol = root.optObj("contents")?.optObj("twoColumnBrowseResultsRenderer")
        val responsiveHeader = twoCol
            ?.optArr("tabs")?.objects()?.firstOrNull()
            ?.optObj("tabRenderer")?.optObj("content")
            ?.optObj("sectionListRenderer")?.optArr("contents")?.objects()?.firstOrNull()
            ?.optObj("musicResponsiveHeaderRenderer")

        val detailHeader = root.optObj("header")?.optObj("musicDetailHeaderRenderer")
            ?: root.optObj("header")?.optObj("musicEditablePlaylistDetailHeaderRenderer")
                ?.optObj("header")?.optObj("musicDetailHeaderRenderer")

        val headerObj = responsiveHeader ?: detailHeader
        val title = extractRunsText(headerObj?.optObj("title")).ifBlank { "Playlist" }
        val thumbnailUrl = extractBestThumbnail(
            headerObj?.optObj("thumbnail")?.optObj("musicThumbnailRenderer")
                ?: headerObj?.optObj("thumbnail")?.optObj("croppedSquareThumbnailRenderer"),
            targetSize = 720
        )
        val description = extractRunsText(
            headerObj?.optObj("description")?.optObj("musicDescriptionShelfRenderer")?.optObj("description")
                ?: headerObj?.optObj("description")
        )
        val author = extractRunsText(headerObj?.optObj("straplineTextOne"))
            .ifBlank { extractRunsText(headerObj?.optObj("subtitle")) }
        val trackCountText = extractRunsText(headerObj?.optObj("secondSubtitle"))

        val shelf = twoCol
            ?.optObj("secondaryContents")
            ?.optObj("sectionListRenderer")
            ?.optArr("contents")?.objects()?.firstOrNull()
            ?.let { it.optObj("musicPlaylistShelfRenderer") ?: it.optObj("musicShelfRenderer") }
            ?: root.optObj("contents")
                ?.optObj("singleColumnBrowseResultsRenderer")
                ?.optArr("tabs")?.objects()?.firstOrNull()
                ?.optObj("tabRenderer")?.optObj("content")
                ?.optObj("sectionListRenderer")?.optArr("contents")?.objects()?.firstOrNull()
                ?.let { it.optObj("musicPlaylistShelfRenderer") ?: it.optObj("musicShelfRenderer") }
            ?: root.optObj("continuationContents")?.optObj("musicPlaylistShelfContinuation")

        val songs = shelf?.optArr("contents")?.objects().orEmpty().mapNotNull { item ->
            item.optObj("musicResponsiveListItemRenderer")?.let { renderer ->
                (parseResponsiveListItem(
                    renderer = renderer,
                    fallbackThumbnail = thumbnailUrl
                ) as? MediaShelfItem.SongItem)?.song
            }
        }
        val continuation = shelf?.let { extractSectionContinuation(it) }

        return PlaylistDetail(
            id = browseId,
            title = title,
            author = author,
            description = description,
            trackCountText = trackCountText,
            thumbnailUrl = thumbnailUrl,
            songs = songs,
            continuation = continuation
        )
    }

    fun parseWatchNext(jsonString: String): WatchNextData {
        val root = JSONObject(jsonString)
        val tabs = root.optObj("contents")
            ?.optObj("singleColumnMusicWatchNextResultsRenderer")
            ?.optObj("tabbedRenderer")
            ?.optObj("watchNextTabbedResultsRenderer")
            ?.optArr("tabs")?.objects().orEmpty()

        val upNextSongs = mutableListOf<Song>()
        var lyricsBrowseId: String? = null
        var relatedBrowseId: String? = null

        tabs.forEachIndexed { index, tabObj ->
            val tab = tabObj.optObj("tabRenderer") ?: return@forEachIndexed
            val tabTitle = tab.optString("title").lowercase()
            val endpointBrowseId = extractNavEndpoint(tab.optObj("endpoint"))?.browseId
            when {
                index == 0 || tabTitle.contains("up next") -> {
                    val panelContents = tab.optObj("content")
                        ?.optObj("musicQueueRenderer")
                        ?.optObj("content")
                        ?.optObj("playlistPanelRenderer")
                        ?.optArr("contents")?.objects().orEmpty()
                    panelContents.forEach { item ->
                        parsePlaylistPanelVideo(item)?.let { upNextSongs.add(it) }
                    }
                }
                tabTitle.contains("lyric") -> lyricsBrowseId = endpointBrowseId
                tabTitle.contains("related") -> relatedBrowseId = endpointBrowseId
            }
        }

        return WatchNextData(
            upNextSongs = upNextSongs.distinctBy { it.id },
            lyricsBrowseId = lyricsBrowseId,
            relatedBrowseId = relatedBrowseId
        )
    }

    fun parseLyrics(jsonString: String): String? {
        val root = JSONObject(jsonString)
        val contents = root.optObj("contents")
            ?.optObj("sectionListRenderer")
            ?.optArr("contents")?.objects().orEmpty()
        for (item in contents) {
            val lyrics = item.optObj("musicDescriptionShelfRenderer")
                ?.optObj("description")
                ?.let { extractRunsText(it) }
                ?.takeIf { it.isNotBlank() }
            if (lyrics != null) return lyrics
        }
        return null
    }

    fun extractVisitorData(jsonString: String): String? {
        return runCatching {
            val root = JSONObject(jsonString)
            root.optObj("responseContext")
                ?.optString("visitorData")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun parsePlaybackStream(
        videoId: String,
        userAgent: String,
        jsonString: String
    ): PlaybackStream? {
        return auditAndSelectPlaybackStreams(
            videoId = videoId,
            userAgent = userAgent,
            jsonString = jsonString
        ).rankedPlayableStreams.firstOrNull()
    }

    fun auditAndSelectPlaybackStreams(
        videoId: String,
        userAgent: String,
        jsonString: String,
        clientName: String = "UNKNOWN"
    ): PlayerResponseAudit {
        val root = JSONObject(jsonString)
        val playabilityObj = root.optObj("playabilityStatus")
        val status = playabilityObj?.optString("status")?.takeIf { it.isNotBlank() }
        val reason = playabilityObj?.optString("reason")?.takeIf { it.isNotBlank() }
            ?: playabilityObj?.optObj("errorScreen")
                ?.optObj("playerErrorMessageRenderer")
                ?.let { renderer ->
                    extractRunsText(renderer.optObj("reason")).ifBlank {
                        extractRunsText(renderer.optObj("subreason"))
                    }
                }
                ?.takeIf { it.isNotBlank() }

        val streamingData = root.optObj("streamingData")
        val expiresInSeconds = streamingData?.optString("expiresInSeconds")?.toLongOrNull() ?: 21540L
        val expiresAtMs = System.currentTimeMillis() + ((expiresInSeconds - 120L).coerceAtLeast(300L)) * 1000L
        val hasServerAbr = streamingData?.optString("serverAbrStreamingUrl")?.isNotBlank() == true
        val hlsUrl = streamingData?.optString("hlsManifestUrl")?.takeIf { isValidStreamHttpUrl(it) }

        val adaptiveFormats = streamingData?.optArr("adaptiveFormats")?.objects().orEmpty()
        val muxedFormats = streamingData?.optArr("formats")?.objects().orEmpty()

        data class RankedCandidate(
            val candidate: AudioFormatCandidate,
            val stream: PlaybackStream?
        )

        val analyzed = mutableListOf<RankedCandidate>()

        fun inspectFormat(format: JSONObject, fromMuxed: Boolean) {
            val rawMime = format.optString("mimeType")
            val isAudioOnly = rawMime.startsWith("audio/", ignoreCase = true)
            val hasMuxedAudio = fromMuxed && (
                format.optInt("audioChannels", 0) > 0 ||
                    rawMime.contains("mp4a", ignoreCase = true) ||
                    rawMime.contains("opus", ignoreCase = true)
                )
            if (!isAudioOnly && !hasMuxedAudio) return

            val itag = format.optInt("itag", 0)
            val baseMime = rawMime.substringBefore(";").trim()
            val codecs = Regex("""codecs="([^"]+)"""").find(rawMime)?.groupValues?.getOrNull(1)
            val bitrate = format.optInt("bitrate", 0)
            val audioQuality = format.optString("audioQuality").takeIf { it.isNotBlank() }
            val contentLength = format.optString("contentLength").toLongOrNull()
            val isDrmProtected = (format.optArr("drmFamilies")?.length() ?: 0) > 0
            val isDefaultTrack = format.optObj("audioTrack")?.optBoolean("audioIsDefault", true) ?: true

            val directUrl = format.optString("url").takeIf { isValidStreamHttpUrl(it) }
            val hasCipher = format.optString("signatureCipher").isNotBlank() ||
                format.optString("cipher").isNotBlank()
            val urlAvailability = when {
                directUrl != null -> UrlAvailability.DIRECT_URL
                hasCipher -> UrlAvailability.SIGNATURE_CIPHER
                else -> UrlAvailability.MISSING
            }

            val candidate = AudioFormatCandidate(
                itag = itag,
                mimeType = baseMime,
                codecs = codecs,
                bitrate = bitrate,
                audioQuality = audioQuality,
                contentLength = contentLength,
                urlAvailability = urlAvailability,
                isDrmProtected = isDrmProtected,
                isDefaultAudioTrack = isDefaultTrack,
                isMuxedVideoAudio = fromMuxed && !isAudioOnly
            )

            val supportedContainer = baseMime.equals("audio/mp4", ignoreCase = true) ||
                baseMime.equals("audio/webm", ignoreCase = true) ||
                baseMime.equals("audio/mpeg", ignoreCase = true) ||
                baseMime.equals("audio/ogg", ignoreCase = true) ||
                (hasMuxedAudio && baseMime.equals("video/mp4", ignoreCase = true))

            val stream = if (!isDrmProtected && directUrl != null && supportedContainer) {
                PlaybackStream(
                    videoId = videoId,
                    streamUrl = directUrl,
                    mimeType = baseMime,
                    bitrate = bitrate,
                    userAgent = userAgent,
                    expiresAtMs = expiresAtMs,
                    isHls = false,
                    itag = itag,
                    codecs = codecs,
                    audioQuality = audioQuality,
                    contentLength = contentLength,
                    clientName = clientName
                )
            } else {
                null
            }

            analyzed.add(RankedCandidate(candidate, stream))
        }

        adaptiveFormats.forEach { inspectFormat(it, fromMuxed = false) }
        muxedFormats.forEach { inspectFormat(it, fromMuxed = true) }

        if (status != null && status != "OK") {
            return PlayerResponseAudit(
                playabilityStatus = status,
                playabilityReason = reason,
                hasStreamingData = streamingData != null,
                expiresInSeconds = expiresInSeconds,
                hasServerAbrStreamingUrl = hasServerAbr,
                hasHlsManifestUrl = hlsUrl != null,
                audioCandidates = analyzed.map { it.candidate },
                rankedPlayableStreams = emptyList(),
                rootCauseHint = "playabilityStatus=$status${reason?.let { " ($it)" } ?: ""}"
            )
        }

        if (streamingData == null) {
            return PlayerResponseAudit(
                playabilityStatus = status,
                playabilityReason = reason,
                hasStreamingData = false,
                expiresInSeconds = expiresInSeconds,
                hasServerAbrStreamingUrl = false,
                hasHlsManifestUrl = false,
                audioCandidates = emptyList(),
                rankedPlayableStreams = emptyList(),
                rootCauseHint = "streamingData missing in InnerTube player response"
            )
        }

        val rankedDirectStreams = analyzed
            .filter { it.stream != null }
            .sortedWith(
                compareByDescending<RankedCandidate> { !it.candidate.isMuxedVideoAudio }
                    .thenByDescending { it.candidate.isDefaultAudioTrack }
                    .thenByDescending { !it.candidate.mimeType.contains("drc", ignoreCase = true) }
                    .thenByDescending { it.candidate.mimeType.equals("audio/mp4", ignoreCase = true) }
                    .thenByDescending { it.candidate.mimeType.equals("audio/webm", ignoreCase = true) }
                    .thenByDescending { it.candidate.bitrate }
            )
            .mapNotNull { it.stream }
            .toMutableList()

        if (hlsUrl != null) {
            rankedDirectStreams.add(
                PlaybackStream(
                    videoId = videoId,
                    streamUrl = hlsUrl,
                    mimeType = "application/x-mpegURL",
                    bitrate = 0,
                    userAgent = userAgent,
                    expiresAtMs = expiresAtMs,
                    isHls = true,
                    itag = 0,
                    codecs = null,
                    audioQuality = "HLS",
                    contentLength = null,
                    clientName = clientName
                )
            )
        }

        val rootCauseHint = if (rankedDirectStreams.isEmpty()) {
            val candidates = analyzed.map { it.candidate }
            when {
                candidates.isNotEmpty() && candidates.all { it.isDrmProtected } ->
                    "Only DRM-protected streams returned (DRM bypass prohibited)"
                candidates.any { it.urlAvailability == UrlAvailability.SIGNATURE_CIPHER } ->
                    "Only signatureCipher formats returned (no direct playable URL)"
                hasServerAbr ->
                    "Only SABR serverAbrStreamingUrl returned without progressive/HLS URL"
                else ->
                    "No ExoPlayer-compatible audio format with playable URL"
            }
        } else {
            null
        }

        return PlayerResponseAudit(
            playabilityStatus = status,
            playabilityReason = reason,
            hasStreamingData = true,
            expiresInSeconds = expiresInSeconds,
            hasServerAbrStreamingUrl = hasServerAbr,
            hasHlsManifestUrl = hlsUrl != null,
            audioCandidates = analyzed.map { it.candidate },
            rankedPlayableStreams = rankedDirectStreams,
            rootCauseHint = rootCauseHint
        )
    }

    private fun isValidStreamHttpUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false
        // Guard against accidentally treating artwork/thumbnail URLs as audio streams
        if (lower.contains("ytimg.com") || lower.contains("googleusercontent.com") || lower.contains("ggpht.com")) {
            return false
        }
        return true
    }

    private fun parseSectionListContents(contents: List<JSONObject>): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        for (obj in contents) {
            obj.optObj("musicCarouselShelfRenderer")?.let { carousel ->
                val header = carousel.optObj("header")?.optObj("musicCarouselShelfBasicHeaderRenderer")
                val title = extractRunsText(header?.optObj("title")).ifBlank { "Featured" }
                val subtitle = extractRunsText(header?.optObj("strapline")).takeIf { it.isNotBlank() }
                val moreNav = extractNavEndpoint(
                    header?.optObj("moreContentButton")?.optObj("buttonRenderer")?.optObj("navigationEndpoint")
                )
                val items = carousel.optArr("contents")?.objects().orEmpty().mapNotNull { item ->
                    item.optObj("musicTwoRowItemRenderer")?.let { parseTwoRowItem(it) }
                        ?: item.optObj("musicResponsiveListItemRenderer")?.let { parseResponsiveListItem(it) }
                        ?: item.optObj("musicNavigationButtonRenderer")?.let { parseNavigationButton(it)?.let { mood -> MediaShelfItem.MoodItem(mood) } }
                }
                if (items.isNotEmpty()) {
                    sections.add(
                        HomeSection(
                            title = title,
                            subtitle = subtitle,
                            items = items,
                            moreBrowseId = moreNav?.browseId,
                            moreParams = moreNav?.params
                        )
                    )
                }
            }

            obj.optObj("musicShelfRenderer")?.let { shelf ->
                val title = extractRunsText(shelf.optObj("title")).ifBlank { "Tracks" }
                val items = shelf.optArr("contents")?.objects().orEmpty().mapNotNull { item ->
                    item.optObj("musicResponsiveListItemRenderer")?.let { parseResponsiveListItem(it) }
                        ?: item.optObj("musicTwoRowItemRenderer")?.let { parseTwoRowItem(it) }
                }
                if (items.isNotEmpty()) {
                    sections.add(HomeSection(title = title, items = items))
                }
            }

            obj.optObj("gridRenderer")?.let { grid ->
                val title = extractRunsText(grid.optObj("header")?.optObj("gridHeaderRenderer")?.optObj("title"))
                    .ifBlank { "Categories" }
                val items = grid.optArr("items")?.objects().orEmpty().mapNotNull { item ->
                    item.optObj("musicNavigationButtonRenderer")?.let { parseNavigationButton(it)?.let { mood -> MediaShelfItem.MoodItem(mood) } }
                        ?: item.optObj("musicTwoRowItemRenderer")?.let { parseTwoRowItem(it) }
                        ?: item.optObj("musicResponsiveListItemRenderer")?.let { parseResponsiveListItem(it) }
                }
                if (items.isNotEmpty()) {
                    sections.add(HomeSection(title = title, items = items))
                }
            }
        }
        return sections
    }
}
