package com.vyorasound.app.domain.repository

import com.vyorasound.app.domain.model.AlbumDetail
import com.vyorasound.app.domain.model.ArtistDetail
import com.vyorasound.app.domain.model.ExploreFeed
import com.vyorasound.app.domain.model.HomeFeed
import com.vyorasound.app.domain.model.HomeSection
import com.vyorasound.app.domain.model.PlaybackStream
import com.vyorasound.app.domain.model.PlaylistDetail
import com.vyorasound.app.domain.model.SearchFilter
import com.vyorasound.app.domain.model.SearchResults
import com.vyorasound.app.domain.model.SearchSuggestionItem
import com.vyorasound.app.domain.model.Song
import com.vyorasound.app.domain.model.WatchNextData

interface MusicRepository {
    suspend fun getHomeFeed(continuation: String? = null): Result<HomeFeed>
    suspend fun getExploreFeed(): Result<ExploreFeed>
    suspend fun getBrowseCategory(browseId: String, params: String? = null): Result<List<HomeSection>>
    suspend fun getSearchSuggestions(query: String): Result<List<SearchSuggestionItem>>
    suspend fun search(query: String, filter: SearchFilter, continuation: String? = null): Result<SearchResults>
    suspend fun getArtistDetail(browseId: String): Result<ArtistDetail>
    suspend fun getAlbumDetail(browseId: String): Result<AlbumDetail>
    suspend fun getPlaylistDetail(browseId: String, continuation: String? = null): Result<PlaylistDetail>
    suspend fun getWatchNext(videoId: String): Result<WatchNextData>
    suspend fun getRelatedSongs(relatedBrowseId: String): Result<List<Song>>
    suspend fun getLyrics(lyricsBrowseId: String): Result<String?>
    suspend fun getPlaybackStream(videoId: String): Result<PlaybackStream>
    fun registerSongTitle(videoId: String, title: String) {}
    fun invalidateStreamCache(videoId: String) {}
    fun getCachedStream(videoId: String): PlaybackStream? = null
}
