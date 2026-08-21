package com.tdpl.chat.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tdpl.chat.model.Character
import com.tdpl.chat.model.CharacterPersonality
import com.tdpl.chat.ui.theme.*

@Composable
fun CharacterEditorScreen(
    existing: Character?,
    onBack: () -> Unit,
    onSave: (Character) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var avatarUrl by remember { mutableStateOf(existing?.avatarUrl ?: "") }
    var shortDescription by remember { mutableStateOf(existing?.shortDescription ?: "") }
    var scenario by remember { mutableStateOf(existing?.scenario ?: "") }
    var firstMessage by remember { mutableStateOf(existing?.firstMessage ?: "") }

    var traits by remember { mutableStateOf(existing?.personality?.traits?.joinToString(", ") ?: "") }
    var background by remember { mutableStateOf(existing?.personality?.background ?: "") }
    var relationships by remember { mutableStateOf(existing?.personality?.relationships ?: "") }
    var speechStyle by remember { mutableStateOf(existing?.personality?.speechStyle ?: "") }
    var advancedOpen by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank() && shortDescription.isNotBlank() &&
        scenario.isNotBlank() && firstMessage.isNotBlank()

    Column(modifier = Modifier.fillMaxSize().background(InkVoid)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = TextSecondary)
            }
            Text(
                if (existing == null) "Karakter baru" else "Edit karakter",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    onSave(
                        Character(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            avatarUrl = avatarUrl.trim().ifBlank { null },
                            shortDescription = shortDescription.trim(),
                            personality = CharacterPersonality(
                                traits = traits.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                relationships = relationships.trim(),
                                background = background.trim(),
                                speechStyle = speechStyle.trim()
                            ),
                            scenario = scenario.trim(),
                            firstMessage = firstMessage.trim(),
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                enabled = isValid
            ) {
                Text("Simpan", color = if (isValid) EmberCore else TextTertiary)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FieldLabel("Nama *")
            EditorField(name, { name = it }, placeholder = "Aria")

            FieldLabel("Foto/avatar (URL, opsional)")
            EditorField(avatarUrl, { avatarUrl = it }, placeholder = "https://...")

            FieldLabel("Deskripsi singkat *")
            EditorField(
                shortDescription, { shortDescription = it },
                placeholder = "Penjaga hutan misterius yang tinggal sendirian di pondok tua.",
                minLines = 2
            )

            FieldLabel("Situasi/adegan default *")
            EditorField(
                scenario, { scenario = it },
                placeholder = "Senja mulai turun, seorang pengembara baru tiba di depan pondoknya.",
                minLines = 2
            )

            FieldLabel("Pesan pembuka AI *")
            EditorField(
                firstMessage, { firstMessage = it },
                placeholder = "*Suasana berubah ketika ia menyadari kehadiranmu...* \"Kamu datang juga.\"",
                minLines = 3
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(InkSurface)
                    .clickable { advancedOpen = !advancedOpen }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lanjutan: Kepribadian", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (advancedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            if (advancedOpen) {
                FieldLabel("Sifat (pisahkan dengan koma)")
                EditorField(traits, { traits = it }, placeholder = "tenang, waspada, hangat pada orang yang dipercaya")

                FieldLabel("Latar belakang")
                EditorField(background, { background = it }, placeholder = "Menjaga hutan selama 10 tahun...", minLines = 2)

                FieldLabel("Hubungan")
                EditorField(relationships, { relationships = it }, placeholder = "Dulu murid seorang penyihir hutan...", minLines = 2)

                FieldLabel("Gaya bicara")
                EditorField(speechStyle, { speechStyle = it }, placeholder = "Singkat, puitis, jarang basa-basi.")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(placeholder, color = TextTertiary, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic))
        },
        minLines = minLines,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = EmberCore,
            unfocusedBorderColor = InkBorder,
            cursorColor = EmberCore
        )
    )
}
