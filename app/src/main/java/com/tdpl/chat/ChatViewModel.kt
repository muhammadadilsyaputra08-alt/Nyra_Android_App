package com.tdpl.chat

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tdpl.chat.data.ModelState
import com.tdpl.chat.jni.LlamaBridge
import com.tdpl.chat.model.Message
import com.tdpl.chat.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(app: android.app.Application) : AndroidViewModel(app) {

    private val modelManager get() = (getApplication<TDPLApp>()).modelManager

    val modelState: StateFlow<ModelState> = modelManager.state

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isModelLoadedInMemory = MutableStateFlow(false)
    val isModelLoadedInMemory: StateFlow<Boolean> = _isModelLoadedInMemory.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun bootstrap(manifestUrl: String) {
        viewModelScope.launch {
            if (!modelManager.hasLocalModel()) {
                android.content.Intent(getApplication(), com.tdpl.chat.data.ModelDownloadService::class.java)
                com.tdpl.chat.data.ModelDownloadService.start(getApplication(), manifestUrl)
            } else {
                modelManager.ensureModelReady(manifestUrl) // cheap manifest check, reuses cached file
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
            val ok = LlamaBridge.loadModel(path, nThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
            _isModelLoadedInMemory.value = ok
            if (ok) {
                _messages.value = listOf(
                    Message(role = Role.SYSTEM, text = "Model loaded and ready.")
                )
            }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isGenerating.value || !_isModelLoadedInMemory.value) return

        val userMsg = Message(role = Role.USER, text = userText)
        val assistantMsg = Message(role = Role.ASSISTANT, text = "", isStreaming = true)
        _messages.value = _messages.value + userMsg + assistantMsg

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
