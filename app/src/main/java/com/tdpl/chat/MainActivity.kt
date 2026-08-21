package com.tdpl.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.tdpl.chat.data.ModelState
import com.tdpl.chat.ui.character.CharacterEditorScreen
import com.tdpl.chat.ui.character.CharacterListScreen
import com.tdpl.chat.ui.chat.ChatScreen
import com.tdpl.chat.ui.chat.SessionSidebar
import com.tdpl.chat.ui.download.DownloadScreen
import com.tdpl.chat.ui.theme.InkSurface
import com.tdpl.chat.ui.theme.TDPLChatTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.bootstrap(BuildConfig.HF_MANIFEST_URL)

        setContent {
            TDPLChatTheme {
                val modelState by viewModel.modelState.collectAsState()
                val loadedInMemory by viewModel.isModelLoadedInMemory.collectAsState()
                val messages by viewModel.messages.collectAsState()
                val isGenerating by viewModel.isGenerating.collectAsState()
                val sessions by viewModel.sessions.collectAsState()
                val currentSessionId by viewModel.currentSessionId.collectAsState()
                val characters by viewModel.characters.collectAsState()
                val screen by viewModel.screen.collectAsState()
                val characterName by viewModel.currentCharacterName.collectAsState()

                if (modelState is ModelState.Ready && loadedInMemory) {
                    val drawerState = rememberDrawerState(DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet(drawerContainerColor = InkSurface) {
                                SessionSidebar(
                                    sessions = sessions,
                                    characters = characters,
                                    currentSessionId = currentSessionId,
                                    onNewChat = {
                                        viewModel.openCharacterList()
                                        scope.launch { drawerState.close() }
                                    },
                                    onSelect = {
                                        viewModel.selectSession(it)
                                        scope.launch { drawerState.close() }
                                    },
                                    onPin = viewModel::togglePin,
                                    onRename = viewModel::renameSession,
                                    onDelete = viewModel::deleteSession
                                )
                            }
                        }
                    ) {
                        when (val s = screen) {
                            is Screen.CharacterList -> CharacterListScreen(
                                characters = characters,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onCreateNew = { viewModel.openCharacterEditor(null) },
                                onSelect = { viewModel.startSessionWithCharacter(it) },
                                onEdit = { viewModel.openCharacterEditor(it) },
                                onDelete = { viewModel.deleteCharacter(it) }
                            )

                            is Screen.CharacterEditor -> CharacterEditorScreen(
                                existing = characters.find { it.id == s.characterId },
                                onBack = { viewModel.openCharacterList() },
                                onSave = { viewModel.saveCharacter(it) }
                            )

                            is Screen.Chat -> ChatScreen(
                                messages = messages,
                                characterName = characterName,
                                isGenerating = isGenerating,
                                onSend = viewModel::sendMessage,
                                onStop = viewModel::stopGenerating,
                                onMenuClick = { scope.launch { drawerState.open() } }
                            )
                        }
                    }
                } else {
                    DownloadScreen(state = modelState)
                }
            }
        }
    }
}
