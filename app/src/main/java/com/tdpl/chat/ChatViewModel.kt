package com.tdpl.chat

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tdpl.chat.data.CharacterRepository
import com.tdpl.chat.data.ModelState
import com.tdpl.chat.data.SessionRepository
import com.tdpl.chat.jni.LlamaBridge
import com.tdpl.chat.model.Character
import com.tdpl.chat.model.ChatSession
import com.tdpl.chat.model.Message
import com.tdpl.chat.model.Role
import com.tdpl.chat.model.renderSystemPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    data object CharacterList : Screen()
    data class CharacterEditor(val characterId: String?) : Screen()
    data object Chat : Screen()
}

class ChatViewModel(app: android.app.Application) : AndroidViewModel(app) {

    private val modelManager get() = (getApplication<TDPLApp>()).modelManager
    private val sessionRepo = SessionRepository(app)
    private val characterRepo = CharacterRepository(app)

    val modelState: StateFlow<ModelState> = modelManager.state

    private val _characters = MutableStateFlow<List<Character>>(emptyList())
    val characters: StateFlow<List<Character>> = _characters.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.CharacterList)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _isModelLoadedInMemory = MutableStateFlow(false)
    val isModelLoadedInMemory: StateFlow<Boolean> = _isModelLoadedInMemory.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val currentCharacterName: StateFlow<String> =
        combine(_sessions, _currentSessionId, _characters) { sessions, curId, chars ->
            val session = sessions.find { it.id == curId }
            chars.find { it.id == session?.characterId }?.name ?: "Nyra"
        }.stateIn(viewModelScope, SharingStarted.Eagerly, "Nyra")

    init {
        _characters.value = characterRepo.loadAll()

        val loaded = sessionRepo.loadAll().sortedWith(sessionOrder())
        _sessions.value = loaded
        if (loaded.isNotEmpty()) {
            _currentSessionId.value = loaded.first().id
            _messages.value = loaded.first().messages
        }

        // Land on Chat if there's an existing conversation to resume; otherwise
        // start at the character library (spec: character selection is the
        // entry point, not a blank generic chat).
        _screen.value = if (loaded.isNotEmpty() && loaded.first().messages.isNotEmpty()) {
            Screen.Chat
        } else {
            Screen.CharacterList
        }
    }

    /**
     * Offline-first: if the model is already on internal storage, becomes
     * Ready immediately with zero network calls. HF is only ever contacted
     * when there's genuinely no usable model on disk yet.
     */
    fun bootstrap(manifestUrl: String) {
        viewModelScope.launch {
            if (modelManager.hasLocalModel()) {
                modelManager.markLocalModelReady()
            } else {
                com.tdpl.chat.data.ModelDownloadService.start(getApplication(), manifestUrl)
            }
            modelState.collect { state ->
                if (state is ModelState.Ready && !_isModelLoadedInMemory.value) {
                    loadIntoMemory()
                }
            }
        }
    }

    private fun loadIntoMemory() {
        viewModelScope.launch {
            val path = modelManager.localModelPath() ?: return@launch
            val ok = LlamaBridge.loadModel(
                path,
                nThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            )
            _isModelLoadedInMemory.value = ok
        }
    }

    // ---- navigation ----

    fun openCharacterList() { _screen.value = Screen.CharacterList }
    fun openCharacterEditor(characterId: String?) { _screen.value = Screen.CharacterEditor(characterId) }
    fun openChat() { _screen.value = Screen.Chat }

    // ---- character management ----

    fun saveCharacter(character: Character) {
        val exists = _characters.value.any { it.id == character.id }
        _characters.value = if (exists) {
            _characters.value.map { if (it.id == character.id) character.copy(updatedAt = System.currentTimeMillis()) else it }
        } else {
            _characters.value + character
        }
        characterRepo.saveAll(_characters.value)
        _screen.value = Screen.CharacterList
    }

    fun deleteCharacter(characterId: String) {
        _characters.value = _characters.value.filterNot { it.id == characterId }
        characterRepo.saveAll(_characters.value)
        // Cascade: sessions belonging to a deleted character no longer make sense.
        val remainingSessions = _sessions.value.filterNot { it.characterId == characterId }
        _sessions.value = remainingSessions
        sessionRepo.saveAll(remainingSessions)
        if (_sessions.value.none { it.id == _currentSessionId.value }) {
            _currentSessionId.value = remainingSessions.firstOrNull()?.id ?: ""
            _messages.value = remainingSessions.firstOrNull()?.messages ?: emptyList()
        }
    }

    /** Starts a brand-new session for [characterId]: system prompt + the character's opening line. */
    fun startSessionWithCharacter(characterId: String) {
        val character = _characters.value.find { it.id == characterId } ?: return
        val systemMsg = Message(role = Role.SYSTEM, text = character.renderSystemPrompt())
        val firstMsg = Message(role = Role.ASSISTANT, text = character.firstMessage)
        val session = ChatSession(
            characterId = characterId,
            title = character.name,
            messages = listOf(systemMsg, firstMsg)
        )
        _sessions.value = (listOf(session) + _sessions.value).sortedWith(sessionOrder())
        _currentSessionId.value = session.id
        _messages.value = session.messages
        persist()
        _screen.value = Screen.Chat
    }

    // ---- session management ----

    fun selectSession(id: String) {
        val target = _sessions.value.find { it.id == id } ?: return
        _currentSessionId.value = id
        _messages.value = target.messages
        _screen.value = Screen.Chat
    }

    fun renameSession(id: String, newTitle: String) {
        _sessions.value = _sessions.value.map {
            if (it.id == id) it.copy(title = newTitle.trim().ifBlank { it.title }) else it
        }
        persist()
    }

    fun togglePin(id: String) {
        _sessions.value = _sessions.value
            .map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
            .sortedWith(sessionOrder())
        persist()
    }

    fun deleteSession(id: String) {
        val remaining = _sessions.value.filterNot { it.id == id }
        _sessions.value = remaining
        if (_currentSessionId.value == id) {
            if (remaining.isNotEmpty()) {
                selectSession(remaining.first().id)
            } else {
                _currentSessionId.value = ""
                _messages.value = emptyList()
                _screen.value = Screen.CharacterList
            }
        }
        persist()
    }

    private fun sessionOrder(): Comparator<ChatSession> =
        compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updatedAt }

    private fun persist() = sessionRepo.saveAll(_sessions.value)

    private fun syncCurrentSessionMessages() {
        val id = _currentSessionId.value
        _sessions.value = _sessions.value.map { s ->
            if (s.id != id) return@map s
            s.copy(messages = _messages.value, updatedAt = System.currentTimeMillis())
        }.sortedWith(sessionOrder())
        persist()
    }

    // ---- chat ----

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isGenerating.value || !_isModelLoadedInMemory.value) return

        val userMsg = Message(role = Role.USER, text = userText)
        val assistantMsg = Message(role = Role.ASSISTANT, text = "", isStreaming = true)
        _messages.value = _messages.value + userMsg + assistantMsg
        syncCurrentSessionMessages()

        val prompt = buildChatMlPrompt(_messages.value.dropLast(1))

        viewModelScope.launch {
            _isGenerating.value = true
            val builder = StringBuilder()
            LlamaBridge.generateStream(prompt).collect { token ->
                builder.append(token)
                updateLastAssistant(builder.toString(), streaming = true)
            }
            updateLastAssistant(builder.toString(), streaming = false)
            _isGenerating.value = false
            syncCurrentSessionMessages()
        }
    }

    fun stopGenerating() {
        LlamaBridge.cancel()
    }

    private fun updateLastAssistant(text: String, streaming: Boolean) {
        val current = _messages.value.toMutableList()
        val idx = current.indexOfLast { it.role == Role.ASSISTANT }
        if (idx >= 0) {
            current[idx] = current[idx].copy(text = text, isStreaming = streaming)
            _messages.value = current
        }
    }

    /**
     * ChatML format — matches Qwen2.5-Instruct's actual chat template
     * (<|im_start|>role\n...<|im_end|>\n), which is what the character
     * fine-tune was trained against via tokenizer.apply_chat_template().
     * The system turn (character's rendered persona) is included here but
     * never rendered in the UI — see ChatScreen, which filters Role.SYSTEM.
     */
    private fun buildChatMlPrompt(history: List<Message>): String {
        val sb = StringBuilder()
        history.forEach { m ->
            val role = when (m.role) {
                Role.SYSTEM -> "system"
                Role.USER -> "user"
                Role.ASSISTANT -> "assistant"
            }
            sb.append("<|im_start|>").append(role).append('\n')
                .append(m.text)
                .append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        LlamaBridge.unload()
    }
}
