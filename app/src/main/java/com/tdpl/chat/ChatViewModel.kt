package com.tdpl.chat

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tdpl.chat.data.ModelState
import com.tdpl.chat.data.SessionRepository
import com.tdpl.chat.jni.LlamaBridge
import com.tdpl.chat.model.ChatSession
import com.tdpl.chat.model.Message
import com.tdpl.chat.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(app: android.app.Application) : AndroidViewModel(app) {

    private val modelManager get() = (getApplication<TDPLApp>()).modelManager
    private val sessionRepo = SessionRepository(app)

    val modelState: StateFlow<ModelState> = modelManager.state

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isModelLoadedInMemory = MutableStateFlow(false)
    val isModelLoadedInMemory: StateFlow<Boolean> = _isModelLoadedInMemory.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        val loaded = sessionRepo.loadAll().sortedWith(sessionOrder())
        if (loaded.isEmpty()) {
            val fresh = ChatSession()
            _sessions.value = listOf(fresh)
            _currentSessionId.value = fresh.id
        } else {
            _sessions.value = loaded
            _currentSessionId.value = loaded.first().id
            _messages.value = loaded.first().messages
        }
    }

    /**
     * Offline-first: if the model is already on internal storage, becomes
     * Ready immediately with zero network calls. The manifest/HF is only
     * ever contacted when there's genuinely no usable model on disk yet
     * (handled inside ModelDownloadService -> ensureModelReady).
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

    // ---- session management ----

    fun newChat() {
        val fresh = ChatSession()
        _sessions.value = listOf(fresh) + _sessions.value
        _currentSessionId.value = fresh.id
        _messages.value = emptyList()
        persist()
    }

    fun selectSession(id: String) {
        val target = _sessions.value.find { it.id == id } ?: return
        _currentSessionId.value = id
        _messages.value = target.messages
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
                newChat()
                return
            }
        }
        persist()
    }

    private fun sessionOrder(): Comparator<ChatSession> =
        compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updatedAt }

    private fun persist() = sessionRepo.saveAll(_sessions.value)

    /** Mirrors the active in-memory transcript back into its session record and saves it. */
    private fun syncCurrentSessionMessages() {
        val id = _currentSessionId.value
        _sessions.value = _sessions.value.map { s ->
            if (s.id != id) return@map s
            val autoTitle = if (s.title == "Percakapan baru") {
                _messages.value.firstOrNull { it.role == Role.USER }
                    ?.text?.take(48)?.trim()?.ifBlank { null } ?: s.title
            } else s.title
            s.copy(messages = _messages.value, title = autoTitle, updatedAt = System.currentTimeMillis())
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

        val prompt = buildPrompt(_messages.value.dropLast(1))

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

    private fun buildPrompt(history: List<Message>): String {
        val sb = StringBuilder()
        sb.append("<|system|>\nYou are a helpful, concise assistant.\n")
        history.filter { it.role != Role.SYSTEM }.forEach {
            val tag = if (it.role == Role.USER) "user" else "assistant"
            sb.append("<|$tag|>\n${it.text}\n")
        }
        sb.append("<|assistant|>\n")
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        LlamaBridge.unload()
    }
}
