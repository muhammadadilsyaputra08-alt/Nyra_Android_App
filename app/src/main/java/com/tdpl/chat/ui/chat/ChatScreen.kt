package com.tdpl.chat.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tdpl.chat.model.Message
import com.tdpl.chat.model.Role
import com.tdpl.chat.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<Message>,
    isGenerating: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onMenuClick: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkVoid)
    ) {
        TopBar(onMenuClick = onMenuClick)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(messages, key = { _, m -> m.id }) { _, msg ->
                when (msg.role) {
                    Role.SYSTEM -> SystemNote(msg.text)
                    Role.USER -> UserBubble(msg.text)
                    Role.ASSISTANT -> AssistantBubble(msg.text, msg.isStreaming)
                }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it },
            isGenerating = isGenerating,
            onSend = {
                if (input.isNotBlank()) {
                    onSend(input.trim())
                    input = ""
                    scope.launch { }
                }
            },
            onStop = onStop
        )
    }
}

@Composable
private fun TopBar(onMenuClick: () -> Unit) {
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
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(EmberCore)
        )
        Spacer(Modifier.width(10.dp))
        Text("Nyra", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Text("on-device", style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.padding(end = 12.dp))
    }
}

@Composable
private fun SystemNote(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(InkSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp
                    )
                )
                .background(Brush.horizontalGradient(listOf(EmberCore, EmberSoft)))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(text, color = InkVoid, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AssistantBubble(text: String, isStreaming: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp
                    )
                )
                .background(InkSurfaceRaised)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (text.isEmpty() && isStreaming) {
                TypingIndicator()
            } else {
                Text(text, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val delayMs = i * 140
            val alpha by transition.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(700, delayMillis = delayMs, easing = LinearOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SignalCore.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(InkSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp)),
            placeholder = {
                Text(
                    "Say something…",
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = TextTertiary
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = InkSurfaceRaised,
                unfocusedContainerColor = InkSurfaceRaised,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                cursorColor = EmberCore,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            maxLines = 5
        )
        Spacer(Modifier.width(8.dp))

        val fabColor = if (isGenerating) InkBorder else EmberCore
        IconButton(
            onClick = { if (isGenerating) onStop() else onSend() },
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(50))
                .background(fabColor)
        ) {
            Icon(
                imageVector = if (isGenerating) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                contentDescription = if (isGenerating) "Stop" else "Send",
                tint = InkVoid
            )
        }
    }
}
