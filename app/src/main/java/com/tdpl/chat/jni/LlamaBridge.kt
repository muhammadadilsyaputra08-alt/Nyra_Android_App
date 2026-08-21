package com.tdpl.chat.jni

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Thin Kotlin wrapper around llama.cpp (see src/main/cpp/llama-bridge.cpp).
 * A single native context is held for the process lifetime once loaded, so the
 * model stays resident across screen navigation — no reload / no app restart
 * needed after the first successful load.
 *
 * IMPORTANT: every native call here is a long-running, blocking JNI call
 * (model load can take seconds, generation can take many seconds). Both
 * loadModel() and generateStream() are pinned to Dispatchers.Default so they
 * never run on the caller's dispatcher directly — if a caller launches on
 * Dispatchers.Main (e.g. viewModelScope.launch default), calling these
 * without that protection blocks the UI thread and triggers an ANR/crash.
 */
object LlamaBridge {

    init {
        System.loadLibrary("tdpl_llama")
    }

    @Volatile
    var isLoaded: Boolean = false
        private set

    private external fun nativeLoadModel(modelPath: String, nThreads: Int, nCtx: Int): Boolean
    private external fun nativeFreeModel()
    private external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        callback: TokenCallback
    )
    private external fun nativeCancel()

    fun interface TokenCallback {
        /** Return false from the callback to stop generation early. */
        fun onToken(token: String): Boolean
    }

    suspend fun loadModel(modelPath: String, nThreads: Int = 4, nCtx: Int = 2048): Boolean =
        withContext(Dispatchers.Default) {
            val ok = nativeLoadModel(modelPath, nThreads, nCtx)
            isLoaded = ok
            ok
        }

    fun unload() {
        if (isLoaded) {
            nativeFreeModel()
            isLoaded = false
        }
    }

    fun cancel() = nativeCancel()

    /** Streams generated tokens as a cold Flow; cancel via flow collection cancellation. */
    fun generateStream(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): Flow<String> = callbackFlow {
        val callback = TokenCallback { token ->
            val sent = trySend(token).isSuccess
            sent && !isClosedForSend
        }
        nativeGenerate(prompt, maxTokens, temperature, topP, callback)
        close()
        awaitClose { nativeCancel() }
    }.flowOn(Dispatchers.Default)
}
