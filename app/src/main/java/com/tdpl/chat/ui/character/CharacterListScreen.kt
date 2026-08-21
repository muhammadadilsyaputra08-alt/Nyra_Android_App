package com.tdpl.chat.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tdpl.chat.model.Character
import com.tdpl.chat.ui.theme.*

@Composable
fun CharacterListScreen(
    characters: List<Character>,
    onMenuClick: () -> Unit,
    onCreateNew: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(InkVoid)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(InkSurfaceRaised, InkVoid)))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Riwayat percakapan", tint = TextSecondary)
            }
            Text("Karakter", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCreateNew) {
                Icon(Icons.Filled.Add, contentDescription = "Buat karakter", tint = EmberCore)
            }
            Spacer(Modifier.width(4.dp))
        }

        if (characters.isEmpty()) {
            EmptyState(onCreateNew)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(characters, key = { it.id }) { c ->
                    CharacterCard(
                        character = c,
                        onClick = { onSelect(c.id) },
                        onEdit = { onEdit(c.id) },
                        onDelete = { onDelete(c.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onCreateNew: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Belum ada karakter",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Buat karakter pertamamu — nama, kepribadian, dan situasi awal cerita.",
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = TextSecondary
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(EmberCore)
                .clickable { onCreateNew() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = InkVoid, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Buat karakter baru", color = InkVoid, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CharacterCard(
    character: Character,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(InkSurface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(character)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(character.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                character.shortDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Opsi", tint = TextTertiary)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(InkSurfaceRaised)
            ) {
                DropdownMenuItem(
                    text = { Text("Edit", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = TextSecondary) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Hapus", color = DangerCore) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = DangerCore) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
fun Avatar(character: Character, size: Int = 52) {
    val sizeDp = size.dp
    if (!character.avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = character.avatarUrl,
            contentDescription = character.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp).clip(CircleShape).background(InkSurfaceRaised)
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(EmberCore, SignalCore))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                character.name.trim().firstOrNull()?.uppercase() ?: "?",
                color = InkVoid,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
