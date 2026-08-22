// JNI bridge between Kotlin (LlamaBridge.kt) and llama.cpp's public C API.
// Expects llama.cpp checked out at app/src/main/cpp/llama.cpp (git submodule
// of https://github.com/ggml-org/llama.cpp — the whole repo; this is Cara 2,
// a valid/standard way to vendor llama.cpp into a third-party Android app,
// distinct from the repo's own examples/llama.android sample app).

#include <jni.h>
#include <string>
#include <thread>
#include <vector>
#include <atomic>
#include <android/log.h>

#include "llama.h"

// NOTE: llama.cpp's C API changes between versions. This file targets the API
// shape current as of late 2025/early 2026 (llama_vocab, llama_sampler_*,
// llama_batch with parallel arrays). Pin the llama.cpp submodule commit used
// during development and re-check symbol names (`llama.h`) if you bump it —
// function signatures here (llama_decode, llama_batch_init, sampler chain
// calls) are the first place a version bump will break compilation.

#define LOG_TAG "TDPLLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
llama_model *g_model = nullptr;
llama_context *g_ctx = nullptr;
std::atomic<bool> g_cancel{false};
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tdpl_chat_jni_LlamaBridge_nativeLoadModel(
        JNIEnv *env, jobject /* this */, jstring jModelPath, jint nThreads, jint nCtx) {

    const char *modelPath = env->GetStringUTFChars(jModelPath, nullptr);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU-only for broad device compatibility

    g_model = llama_model_load_from_file(modelPath, mparams);
    env->ReleaseStringUTFChars(jModelPath, modelPath);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded, ctx=%d threads=%d", nCtx, nThreads);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tdpl_chat_jni_LlamaBridge_nativeFreeModel(JNIEnv *env, jobject) {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    llama_backend_free();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tdpl_chat_jni_LlamaBridge_nativeCancel(JNIEnv *env, jobject) {
    g_cancel.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tdpl_chat_jni_LlamaBridge_nativeGenerate(
        JNIEnv *env, jobject /* this */, jstring jPrompt, jint maxTokens,
        jfloat temperature, jfloat topP, jobject callback) {

    if (!g_ctx || !g_model) {
        LOGE("Generate called before model load");
        return;
    }
    g_cancel.store(false);

    // Each call resends the ENTIRE conversation from scratch (see
    // ChatViewModel.buildPrompt — it's stateless per turn, not incremental),
    // but g_ctx is reused across calls to keep the model resident in memory.
    // Without clearing the memory/KV cache here, the second turn's tokens
    // collide with position/KV-cache entries left over from the first turn's
    // decode, causing llama_decode to fail silently — the exact "first reply
    // works, every reply after is empty" symptom.
    llama_memory_t mem = llama_get_memory(g_ctx);
    llama_memory_clear(mem, true);

    const char *promptChars = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(promptChars);
    env->ReleaseStringUTFChars(jPrompt, promptChars);

    const llama_vocab *vocab = llama_model_get_vocab(g_model);

    std::vector<llama_token> tokens(prompt.size() + 8);
    int nTokens = llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(),
                                  tokens.data(), (int32_t) tokens.size(), true, true);
    tokens.resize(nTokens);

    llama_batch batch = llama_batch_init(std::max<int>(nTokens, 512), 0, 1);
    for (int i = 0; i < nTokens; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == nTokens - 1);
    }
    batch.n_tokens = nTokens;

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Initial decode failed");
        llama_batch_free(batch);
        return;
    }

    // Sampler chain, in llama.cpp's canonical order: penalties -> top_k ->
    // top_p -> temperature -> dist. The previous chain had no repetition
    // penalty at all, which — combined with a lightly fine-tuned model — let
    // generation loop on identical phrases and drift into raw pretraining
    // artifacts (e.g. "KHTML" from web-crawl user-agent strings) once it lost
    // the thread of the conversation instead of stopping cleanly.
    //
    // Confirmed via CI debug step against this exact llama.cpp checkout's
    // llama.h:1445 — this version's signature is
    //   llama_sampler_init_penalties(int32_t n_vocab, int32_t penalty_last_n,
    //                                 float penalty_repeat, float penalty_freq,
    //                                 float penalty_present)
    // i.e. the first arg is the vocab *size* (an integer), not a vocab pointer.
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
        llama_vocab_n_tokens(vocab),
        /* penalty_last_n */ 256,
        /* penalty_repeat */ 1.15f,
        /* penalty_freq   */ 0.05f,
        /* penalty_present*/ 0.05f
    ));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)Z");

    // Explicit stop-string safety net, independent of the native EOG check.
    // If the GGUF's stop-token metadata is wrong or incomplete (a known
    // Unsloth-export gotcha), llama_vocab_is_eog() below can simply never
    // fire, and the model keeps generating straight through where it should
    // stop — drifting into a hallucinated next turn (literal "assistant",
    // "User:", chat-template markers leaking into the text). This scans the
    // running output for those markers and cuts generation the moment one
    // starts to appear, even if it's split across multiple token pieces.
    static const char *STOP_STRINGS[] = {
        "<|im_start|>", "<|im_end|>", "\nUser:", "\nAssistant:", "\nuser:", "\nassistant:"
    };

    std::string fullOutput;
    int nCur = nTokens;
    for (int i = 0; i < maxTokens && !g_cancel.load(); i++) {
        llama_token newToken = llama_sampler_sample(sampler, g_ctx, -1);

        if (llama_vocab_is_eog(vocab, newToken)) break;

        char buf[256];
        int len = llama_token_to_piece(vocab, newToken, buf, sizeof(buf), 0, true);
        std::string piece(buf, len);
        fullOutput += piece;

        // Check whether any stop marker has started appearing in the output
        // so far. If found, only forward the clean portion before it.
        size_t stopPos = std::string::npos;
        for (const char *stopStr : STOP_STRINGS) {
            size_t pos = fullOutput.find(stopStr);
            if (pos != std::string::npos && (stopPos == std::string::npos || pos < stopPos)) {
                stopPos = pos;
            }
        }

        if (stopPos != std::string::npos) {
            // Only send the clean prefix of *this* piece, then stop for good.
            size_t alreadySentLen = fullOutput.size() - piece.size();
            if (stopPos > alreadySentLen) {
                std::string cleanTail = fullOutput.substr(alreadySentLen, stopPos - alreadySentLen);
                if (!cleanTail.empty()) {
                    jstring jClean = env->NewStringUTF(cleanTail.c_str());
                    env->CallBooleanMethod(callback, onToken, jClean);
                    env->DeleteLocalRef(jClean);
                }
            }
            break;
        }

        jstring jPiece = env->NewStringUTF(piece.c_str());
        jboolean keepGoing = env->CallBooleanMethod(callback, onToken, jPiece);
        env->DeleteLocalRef(jPiece);
        if (!keepGoing) break;

        batch.n_tokens = 1;
        batch.token[0] = newToken;
        batch.pos[0] = nCur;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        nCur++;
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Decode failed mid-generation");
            break;
        }
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);
}
