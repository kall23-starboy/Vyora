package com.vyorasound.app.domain.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val durationText: String = "",
    val durationMs: Long = 0L,
    val thumbnailUrl: String = "",
    val isExplicit: Boolean = false,
    val playCountText: String? = null
)

@JsonClass(generateAdapter = true)
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val year: String? = null,
    val thumbnailUrl: String = "",
    val isExplicit: Boolean = false,
    val browseId: String = id
)

@JsonClass(generateAdapter = true)
data class Artist(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val thumbnailUrl: String = "",
    val browseId: String = id
)

@JsonClass(generateAdapter = true)
data class Playlist(
    val id: String,
    val title: String,
    val author: String = "",
    val trackCountText: String = "",
    val thumbnailUrl: String = "",
    val browseId: String = id,
    val isUserCreated: Boolean = false,
    val songs: List<Song> = emptyList()
)

data class MoodCategory(
    val title: String,
    val browseId: String,
    val params: String? = null,
    val accentColor: Long = 0xFF8D75E8L
)

sealed interface MediaShelfItem {
    val stableId: String

    data class SongItem(val song: Song) : MediaShelfItem {
        override val stableId: String = "song_${song.id}"
    }

    data class AlbumItem(val album: Album) : MediaShelfItem {
        override val stableId: String = "album_${album.browseId}"
    }

    data class ArtistItem(val artist: Artist) : MediaShelfItem {
        override val stableId: String = "artist_${artist.browseId}"
    }

    data class PlaylistItem(val playlist: Playlist) : MediaShelfItem {
        override val stableId: String = "playlist_${playlist.browseId}"
    }

    data class MoodItem(val mood: MoodCategory) : MediaShelfItem {
        override val stableId: String = "mood_${mood.browseId}_${mood.params ?: mood.title}"
    }
}

data class HomeSection(
    val title: String,
    val subtitle: String? = null,
    val items: List<MediaShelfItem>,
    val moreBrowseId: String? = null,
    val moreParams: String? = null
)

data class HomeFeed(
    val sections: List<HomeSection>,
    val continuation: String? = null
)

data class ExploreFeed(
    val trendingSongs: List<Song>,
    val newReleases: List<Album>,
    val popularPlaylists: List<Playlist>,
    val categories: List<MoodCategory>,
    val chartSongs: List<Song>,
    val extraSections: List<HomeSection> = emptyList()
)

enum class SearchFilter(val label: String, val params: String?) {
    ALL("Semua", null),
    SONGS("Lagu", "EgWKAQIIAWoKEAMQBBAJEAoQBQ=="),
    ALBUMS("Album", "EgWKAQIYAWoKEAMQBBAJEAoQBQ=="),
    ARTISTS("Artis", "EgWKAQIgAWoKEAMQBBAJEAoQBQ=="),
    PLAYLISTS("Playlist", "EgWKAQIoAWoKEAMQBBAJEAoQBQ==")
}

sealed interface SearchSuggestionItem {
    data class QuerySuggestion(val query: String) : SearchSuggestionItem
    data class DirectMediaSuggestion(val item: MediaShelfItem) : SearchSuggestionItem
}

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val continuation: String? = null
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

data class ArtistDetail(
    val id: String,
    val name: String,
    val description: String = "",
    val subscriberCount: String = "",
    val thumbnailUrl: String = "",
    val topSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val singles: List<Album> = emptyList(),
    val relatedArtists: List<Artist> = emptyList(),
    val songsBrowseId: String? = null,
    val songsParams: String? = null
)

data class AlbumDetail(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val year: String? = null,
    val description: String = "",
    val thumbnailUrl: String = "",
    val songs: List<Song> = emptyList(),
    val durationText: String = ""
)

data class PlaylistDetail(
    val id: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val trackCountText: String = "",
    val thumbnailUrl: String = "",
    val songs: List<Song> = emptyList(),
    val continuation: String? = null
)

data class WatchNextData(
    val upNextSongs: List<Song> = emptyList(),
    val lyricsBrowseId: String? = null,
    val relatedBrowseId: String? = null
)

data class PlaybackStream(
    val videoId: String,
    val streamUrl: String,
    val mimeType: String,
    val bitrate: Int = 0,
    val userAgent: String,
    val expiresAtMs: Long,
    val isHls: Boolean = false,
    val itag: Int = 0,
    val codecs: String? = null,
    val audioQuality: String? = null,
    val contentLength: Long? = null,
    val clientName: String = "UNKNOWN"
)

enum class UrlAvailability {
    DIRECT_URL,
    SIGNATURE_CIPHER,
    MISSING
}

data class AudioFormatCandidate(
    val itag: Int,
    val mimeType: String,
    val codecs: String?,
    val bitrate: Int,
    val audioQuality: String?,
    val contentLength: Long?,
    val urlAvailability: UrlAvailability,
    val isDrmProtected: Boolean,
    val isDefaultAudioTrack: Boolean,
    val isMuxedVideoAudio: Boolean = false
)

data class PlayerResponseAudit(
    val playabilityStatus: String?,
    val playabilityReason: String?,
    val hasStreamingData: Boolean,
    val expiresInSeconds: Long,
    val hasServerAbrStreamingUrl: Boolean,
    val hasHlsManifestUrl: Boolean,
    val audioCandidates: List<AudioFormatCandidate>,
    val rankedPlayableStreams: List<PlaybackStream>,
    val rootCauseHint: String? = null
)

enum class RepeatMode {
    OFF, ALL, ONE
}
