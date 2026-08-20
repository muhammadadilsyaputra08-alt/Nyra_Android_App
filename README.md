# Nyra — on-device chat app for the TDPL model

Custom Kotlin/Compose Android app. Model runs fully on-device via llama.cpp (JNI),
GGUF format. No ADB steps required for end users — model is fetched over HTTP
from Hugging Face on first launch and cached permanently in app-internal storage.

## How the "install once" flow works

1. `TDPL_Merge_Quantize_GGUF.ipynb` merges the LoRA adapter, converts to GGUF,
   quantizes (Q4_K_M / Q5_K_M), and uploads both files plus a `model_manifest.json`
   (URLs + sha256 + sizes) to a Hugging Face model repo.
2. CI (`.github/workflows/build.yml`) builds the APK with `HF_MANIFEST_URL`
   baked in as a `BuildConfig` field (from the `HF_MANIFEST_URL` repo secret).
   The model file itself is **not** bundled — this keeps the APK small and lets
   you update the model on HF without rebuilding the app.
3. On first launch, `ModelDownloadManager` fetches the manifest, downloads the
   chosen GGUF variant to `<filesDir>/models/model.gguf`, and writes its sha256
   to `<filesDir>/models/model.sha256`.
4. On every later launch, the manifest is re-fetched (cheap, small JSON) and
   compared against the local sha256. If unchanged, **no re-download happens**
   — the cached file in internal storage is used directly. If you publish an
   updated GGUF (new sha256), the app fetches only the new version.
5. `<filesDir>` is app-private internal storage: it survives app **updates**
   (new APK installs over the old one) and is only cleared on uninstall or an
   explicit "Clear data" by the user. That satisfies "setup once unless
   uninstalled."
6. After download, the model is loaded directly into the native llama.cpp
   context in-process (`LlamaBridge.loadModel`) — no app restart needed, the UI
   transitions from the setup screen straight into chat.

## One-time repo setup

```bash
git submodule add https://github.com/ggerganov/llama.cpp app/src/main/cpp/llama.cpp
```

Pin the submodule to a specific commit and re-check `llama-bridge.cpp` against
that version's `llama.h` — llama.cpp's C API changes between releases.

## Required GitHub secrets

| Secret | Purpose |
|---|---|
| `HF_MANIFEST_URL` | `https://huggingface.co/<repo>/resolve/main/model_manifest.json` |
| `SIGNING_KEY_BASE64` | base64 of your release keystore |
| `SIGNING_KEY_ALIAS`, `SIGNING_KEY_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD` | keystore credentials |

## Local build

```bash
./gradlew assembleDebug -PHF_MANIFEST_URL="https://huggingface.co/<repo>/resolve/main/model_manifest.json"
```

## Updating the model later

Re-run the merge/quantize notebook against a new adapter and re-upload — the
manifest's sha256 changes, existing installs pick up the new model
automatically on next launch, no app update required.
