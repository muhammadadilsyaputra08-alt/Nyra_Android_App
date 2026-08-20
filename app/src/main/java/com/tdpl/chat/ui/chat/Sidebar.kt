package com.tdpl.chat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tdpl.chat.model.ChatSession
import com.tdpl.chat.ui.theme.*

private enum class RowAction { PIN, RENAME, DELETE }

@Composable
fun SessionSidebar(
    sessions: List<ChatSession>,
    currentSessionId: String,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onPin: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(InkSurface)
            .padding(vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(EmberCore))
            Spacer(Modifier.width(8.dp))
            Text("Riwayat", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(InkSurfaceRaised)
                .clickable { onNewChat() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = EmberCore, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Percakapan baru", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(12.dp))

        val pinned = sessions.filter { it.pinned }
        val others = sessions.filterNot { it.pinned }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
            if (pinned.isNotEmpty()) {
                item { SectionLabel("Disematkan") }
                items(pinned, key = { it.id }) { s ->
                    SessionRow(
                        session = s,
                        selected = s.id == currentSessionId,
                        onClick = { onSelect(s.id) },
                        onMenuAction = { action ->
                            when (action) {
                                RowAction.PIN -> onPin(s.id)
                                RowAction.RENAME -> { renameTargetId = s.id; renameText = s.title }
                                RowAction.DELETE -> onDelete(s.id)
                            }
                        }
                    )
                }
            }
            if (others.isNotEmpty()) {
                item { SectionLabel(if (pinned.isNotEmpty()) "Lainnya" else "Percakapan") }
                items(others, key = { it.id }) { s ->
                    SessionRow(
                        session = s,
                        selected = s.id == currentSessionId,
                        onClick = { onSelect(s.id) },
                        onMenuAction = { action ->
                            when (action) {
                                RowAction.PIN -> onPin(s.id)
                                RowAction.RENAME -> { renameTargetId = s.id; renameText = s.title }
                                RowAction.DELETE -> onDelete(s.id)
                            }
                        }
                    )
                }
            }
        }
    }

    val targetId = renameTargetId
    if (targetId != null) {
        AlertDialog(
            onDismissRequest = { renameTargetId = null },
            containerColor = InkSurfaceRaised,
            title = { Text("Ubah nama", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = EmberCore,
                        unfocusedBorderColor = InkBorder,
                        cursorColor = EmberCore
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(targetId, renameText)
                    renameTargetId = null
                }) { Text("Simpan", color = EmberCore) }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) { Text("Batal", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun SessionRow(
    session: ChatSession,
    selected: Boolean,
    onClick: () -> Unit,
    onMenuAction: (RowAction) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) InkSurfaceRaised else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            session.title,
            color = if (selected) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Opsi",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = InkSurfaceRaised
            ) {
                DropdownMenuItem(
                    text = { Text(if (session.pinned) "Lepas sematan" else "Sematkan", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null, tint = TextSecondary) },
                    onClick = { menuOpen = false; onMenuAction(RowAction.PIN) }
                )
                DropdownMenuItem(
                    text = { Text("Ubah nama", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = TextSecondary) },
                    onClick = { menuOpen = false; onMenuAction(RowAction.RENAME) }
                )
                DropdownMenuItem(
                    text = { Text("Hapus", color = DangerCore) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = DangerCore) },
                    onClick = { menuOpen = false; onMenuAction(RowAction.DELETE) }
                )
            }
        }
    }
}
