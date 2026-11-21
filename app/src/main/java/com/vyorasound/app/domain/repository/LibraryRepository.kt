package com.vyorasound.app.domain.repository

import com.vyorasound.app.domain.model.Album
import com.vyorasound.app.domain.model.Artist
import com.vyorasound.app.domain.model.Playlist
import com.vyorasound.app.domain.model.RepeatMode
import com.vyorasound.app.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    val likedSongs: Flow<List<Song>>
    val recentlyPlayed: Flow<List<Song>>
    val savedAlbums: Flow<List<Album>>
    val savedArtists: Flow<List<Artist>>
    val savedPlaylists: Flow<List<Playlist>>
    val searchHistory: Flow<List<String>>
    val shuffleEnabled: Flow<Boolean>
    val repeatMode: Flow<RepeatMode>

    suspend fun toggleLikeSong(song: Song): Boolean
    suspend fun isSongLiked(songId: String): Boolean
    suspend fun recordRecentlyPlayed(song: Song)
    suspend fun toggleSaveAlbum(album: Album): Boolean
    suspend fun toggleSaveArtist(artist: Artist): Boolean
    suspend fun toggleSavePlaylist(playlist: Playlist): Boolean
    suspend fun createPlaylist(name: String, initialSong: Song? = null): Playlist
    suspend fun addSongToPlaylist(playlistId: String, song: Song)
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addSearchHistory(query: String)
    suspend fun removeSearchHistoryItem(query: String)
    suspend fun clearSearchHistory()
    suspend fun setShuffleEnabled(enabled: Boolean)
    suspend fun setRepeatMode(mode: RepeatMode)
}
