package com.vyorasound.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vyorasound.app.domain.model.Playlist
import com.vyorasound.app.domain.model.Song
import com.vyorasound.app.presentation.theme.GlassCard
import com.vyorasound.app.presentation.theme.NexaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: Song,
    playlists: List<Playlist>,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    val userPlaylists = remember(playlists) { playlists.filter { it.isUserCreated } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NexaColors.Surface,
        contentColor = NexaColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Tambah ke playlist",
                style = MaterialTheme.typography.headlineSmall,
                color = NexaColors.TextPrimary
            )
            Text(
                text = "${song.title} • ${song.artist}",
                style = MaterialTheme.typography.bodyMedium,
                color = NexaColors.TextSecondary,
                maxLines = 1
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Nama playlist baru", color = NexaColors.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NexaColors.Primary,
                        unfocusedBorderColor = NexaColors.GlassBorder,
                        focusedTextColor = NexaColors.TextPrimary,
                        unfocusedTextColor = NexaColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            onDismiss()
                        }
                    },
                    enabled = newPlaylistName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexaColors.Primary,
                        contentColor = NexaColors.OnPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Buat")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Buat")
                }
            }

            if (userPlaylists.isEmpty()) {
                Text(
                    text = "Belum ada playlist buatanmu. Buat playlist baru di atas!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexaColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(userPlaylists, key = { it.id }) { playlist ->
                        GlassCard(
                            onClick = {
                                onSelectPlaylist(playlist)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(16.dp),
                            backgroundAlpha = 0.5f
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlaylistPlay,
                                    contentDescription = null,
                                    tint = NexaColors.Primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = NexaColors.TextPrimary
                                    )
                                    Text(
                                        text = playlist.trackCountText.ifBlank { "${playlist.songs.size} songs" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NexaColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
