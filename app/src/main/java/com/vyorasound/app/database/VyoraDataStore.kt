package com.vyorasound.app.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vyorasound.app.domain.model.Album
import com.vyorasound.app.domain.model.Artist
import com.vyorasound.app.domain.model.Playlist
import com.vyorasound.app.domain.model.RepeatMode
import com.vyorasound.app.domain.model.Song
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vyoraDataStore: DataStore<Preferences> by preferencesDataStore(name = "vyora_music_prefs")

class VyoraDataStore(
    private val context: Context,
    private val moshi: Moshi
) {
    private object Keys {
        val LIKED_SONGS = stringPreferencesKey("liked_songs_json")
        val RECENTLY_PLAYED = stringPreferencesKey("recently_played_json")
        val SAVED_ALBUMS = stringPreferencesKey("saved_albums_json")
        val SAVED_ARTISTS = stringPreferencesKey("saved_artists_json")
        val SAVED_PLAYLISTS = stringPreferencesKey("saved_playlists_json")
        val SEARCH_HISTORY = stringPreferencesKey("search_history_json")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
    }

    private val songListAdapter = moshi.adapter<List<Song>>(
        Types.newParameterizedType(List::class.java, Song::class.java)
    )
    private val albumListAdapter = moshi.adapter<List<Album>>(
        Types.newParameterizedType(List::class.java, Album::class.java)
    )
    private val artistListAdapter = moshi.adapter<List<Artist>>(
        Types.newParameterizedType(List::class.java, Artist::class.java)
    )
    private val playlistListAdapter = moshi.adapter<List<Playlist>>(
        Types.newParameterizedType(List::class.java, Playlist::class.java)
    )
    private val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    val likedSongsFlow: Flow<List<Song>> = context.vyoraDataStore.data.map { prefs ->
        decodeList(prefs[Keys.LIKED_SONGS], songListAdapter)
    }

    val recentlyPlayedFlow: Flow<List<Song>> = context.vyoraDataStore.data.map { prefs ->
        decodeList(prefs[Keys.RECENTLY_PLAYED], songListAdapter)
    }

    val savedAlbumsFlow: Flow<List<Album>> = context.vyoraDataStore.data.map { prefs ->
        decodeList(prefs[Keys.SAVED_ALBUMS], albumListAdapter)
    }

    val savedArtistsFlow: Flow<List<Artist>> = context.vyoraDataStore.data.map { prefs ->
        decodeList(prefs[Keys.SAVED_ARTISTS], artistListAdapter)
    }

    val savedPlaylistsFlow: Flow<List<Playlist>> = context.vyoraDataStore.data.map { prefs ->
        decodeList(prefs[Keys.SAVED_PLAYLISTS], playlistListAdapter)
    }

    val searchHistoryFlow: Flow<List<String>> = context.vyoraDataStore.data.map { prefs ->
        decodeList(prefs[Keys.SEARCH_HISTORY], stringListAdapter)
    }

    val shuffleEnabledFlow: Flow<Boolean> = context.vyoraDataStore.data.map { prefs ->
        prefs[Keys.SHUFFLE_ENABLED] ?: false
    }

    val repeatModeFlow: Flow<RepeatMode> = context.vyoraDataStore.data.map { prefs ->
        val raw = prefs[Keys.REPEAT_MODE]
        runCatching { if (raw != null) RepeatMode.valueOf(raw) else RepeatMode.OFF }
            .getOrDefault(RepeatMode.OFF)
    }

    suspend fun updateLikedSongs(transform: (List<Song>) -> List<Song>) {
        context.vyoraDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.LIKED_SONGS], songListAdapter)
            prefs[Keys.LIKED_SONGS] = songListAdapter.toJson(transform(current))
        }
    }

    suspend fun updateRecentlyPlayed(transform: (List<Song>) -> List<Song>) {
        context.vyoraDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.RECENTLY_PLAYED], songListAdapter)
            prefs[Keys.RECENTLY_PLAYED] = songListAdapter.toJson(transform(current).take(50))
        }
    }

    suspend fun updateSavedAlbums(transform: (List<Album>) -> List<Album>) {
        context.vyoraDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.SAVED_ALBUMS], albumListAdapter)
            prefs[Keys.SAVED_ALBUMS] = albumListAdapter.toJson(transform(current))
        }
    }

    suspend fun updateSavedArtists(transform: (List<Artist>) -> List<Artist>) {
        context.vyoraDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.SAVED_ARTISTS], artistListAdapter)
            prefs[Keys.SAVED_ARTISTS] = artistListAdapter.toJson(transform(current))
        }
    }

    suspend fun updateSavedPlaylists(transform: (List<Playlist>) -> List<Playlist>) {
        context.vyoraDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.SAVED_PLAYLISTS], playlistListAdapter)
            prefs[Keys.SAVED_PLAYLISTS] = playlistListAdapter.toJson(transform(current))
        }
    }

    suspend fun updateSearchHistory(transform: (List<String>) -> List<String>) {
        context.vyoraDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.SEARCH_HISTORY], stringListAdapter)
            prefs[Keys.SEARCH_HISTORY] = stringListAdapter.toJson(transform(current).take(20))
        }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.vyoraDataStore.edit { prefs ->
            prefs[Keys.SHUFFLE_ENABLED] = enabled
        }
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        context.vyoraDataStore.edit { prefs ->
            prefs[Keys.REPEAT_MODE] = mode.name
        }
    }

    private fun <T> decodeList(
        json: String?,
        adapter: com.squareup.moshi.JsonAdapter<List<T>>
    ): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { adapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }
}
