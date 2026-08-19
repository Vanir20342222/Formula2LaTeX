# Formula2LaTeX

Formula2LaTeX is a native Android app that transcribes mathematical expressions into editable LaTeX. Input can be a natural-language description, a CameraX photo, an image selected with Android Photo Picker, or finger/stylus ink drawn on an in-app canvas.

The app is bring-your-own-key and has no developer backend. Requests travel directly from the Android device to the provider the user selects. There is no login, analytics, crash reporting, cloud database, formula history, or provider key bundled in the application.

## Features

- Kotlin, Jetpack Compose, and Material 3 single-activity UI
- Gemini, OpenRouter, and Custom OpenAI-Compatible providers
- Dynamic model discovery, search, and manual full model-ID entry
- Description, camera, gallery, and drawing input through one conversion state machine
- Responsive tablet layout with larger drawing workspace and side-by-side results in landscape
- S Pen-aware drawing with temporary barrel-button erasing, reliable dots and small strokes, two-finger ink suppression, and strict canvas clipping
- Image orientation correction, metadata-free resizing/compression, and bounded drawing export
- Background drawing export and request preparation to keep the UI responsive
- Strict structured-output requests with one schema-free fallback and tolerant response parsing
- High-contrast locally bundled KaTeX preview with network-disabled WebView behavior
- Editable raw LaTeX and exact clipboard copying without delimiters
- System, light, dark, and AMOLED themes
- Keystore-backed AES/GCM encryption for keys saved on-device; session-only keys remain in memory
- Redacted, actionable provider/network errors and cancellable requests

## Requirements

- Android Studio with JDK 17, or a command-line JDK 17 installation
- Android SDK Platform 37 and Build Tools 37
- An Android device or emulator running Android 8.0 (API 26) or newer
- A user-owned Gemini or OpenRouter key, or a compatible HTTPS endpoint

## Build and test

From a clean checkout:

```bash
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Instrumentation tests require a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

On ARM64 Termux, Android's standard SDK build-tool executables are x86-64. Place a native ARM64 `aapt2` at `.android-tools/current-aapt2/aapt2`, make it executable, and install a native ARM64 `zipalign` for the selected SDK Build Tools version. This workspace was verified with the binaries published by [android-arm-build-tools](https://github.com/Commit451/android-arm-build-tools). Then run, for example:

```bash
scripts/termux-build.sh testDebugUnitTest lintDebug assembleDebug
```

The helper passes the local `aapt2` path to Gradle. Downloaded SDK/tool archives and binaries are intentionally ignored by Git; they are not part of the application or APK.

## Provider setup

On first launch, select a provider and configure it:

- **Gemini:** obtain a key from [Google AI Studio](https://aistudio.google.com/app/apikey). The app discovers models from the Gemini Models API and uses `generateContent`.
- **OpenRouter:** obtain a key from [OpenRouter](https://openrouter.ai/settings/keys). Full model IDs are preserved, including organization prefixes and suffixes such as `:free`.
- **Custom OpenAI-Compatible:** enter an HTTPS base URL, optional key, and model ID. The app tries `<base>/v1/models` and calls `<base>/v1/chat/completions`. Manual model entry always remains available.

Choose **Save on this device** to encrypt the key with an AES/GCM key whose key material remains in Android Keystore. If disabled, the key stays only in memory until the app process is killed. **Delete key and configuration** removes stored ciphertext, IV, and provider settings.

Model metadata is advisory. Gemini discovery does not currently expose image or structured-output capability fields, so these remain unknown and the request is allowed. OpenRouter image capability uses `architecture.input_modalities`; structured-output capability uses `supported_parameters`. Unsupported capability responses are surfaced without silently changing the selected model.

Custom endpoints must use HTTPS. Cleartext HTTP is deliberately disabled, including for keyless local endpoints, in this first release.

## Privacy and security

- Formulas, descriptions, drawings, and photos are not stored as history.
- Camera temporary files are app-cache files and are deleted after import. Upload bytes are re-encoded without EXIF metadata.
- Only `INTERNET` and `CAMERA` are requested. Gallery access uses Photo Picker, not broad media/storage permission.
- The activity sets `FLAG_SECURE` to keep keys and formulas out of screenshots and recent-app previews.
- Provider keys are never put in logs, clipboard content, saved instance state, exception text, resources, Gradle configuration, or the APK.
- No HTTP logging interceptor exists. Error diagnostics contain only provider/model context, HTTP status, a safe request ID when supplied, and a short redacted message.
- KaTeX JavaScript, CSS, fonts, and license are local assets. The preview blocks external navigation and network resources and passes LaTeX to JavaScript through JSON encoding.
- Android backup is disabled.

Cloud transcription still requires internet access, and the selected provider receives the supplied description or processed image under that provider's terms and privacy policy.

## Architecture

The project intentionally uses one small application module:

```text
domain/model       provider-independent inputs, results, capabilities, configuration
data/provider      Gemini/OpenRouter/custom adapters, prompt/schema, parser, errors
data/security      Android Keystore AES/GCM cipher
data/settings      Preferences DataStore and session-secret handling
data/image         photo normalization and drawing bitmap export
ui/main            tabs, camera, drawing, conversion state, results
ui/settings        BYOK setup, model refresh/search/manual selection
ui/components      restricted local KaTeX preview
```

The transcription instruction explicitly prohibits solving, simplifying, evaluating, proving, or explaining. The result parser tries strict JSON, one outer Markdown fence, the first complete JSON object, then raw LaTeX. It strips only outer math delimiters or fences. A render failure preserves the editable raw result.

## Tests

Local tests cover response parsing, delimiter handling, request serialization, provider error mapping, Gemini/OpenRouter/custom discovery and response behavior with TLS MockWebServer fixtures, structured-output fallback, raw-LaTeX fallback, and drawing bounds. Compose instrumentation tests cover setup plus result editing, alternatives, warnings, copy, and retry controls. `DebugFakeFormulaProvider` provides deterministic no-network fixtures for debug/test smoke work and is not presented as a production provider.

Live provider tests are intentionally not automatic. If added locally, pass credentials only through non-committed environment variables or `local-secrets.properties`; never place them in test fixtures or Gradle source and never print them.

## OpenCode

OpenCode is not listed as a free inference provider. It is a coding-agent client/server that still needs a configured model provider, and its server API is agent-oriented rather than a drop-in transcription-model endpoint. A future dedicated adapter could be designed separately. For compatible inference gateways today, use the Custom OpenAI-Compatible option.

## Limitations

- Custom OpenAI-compatible services vary in image and JSON Schema support. Unknown capabilities are attempted and failures explain what to change.
- Crop/rotate editing is not included; EXIF rotation and automatic resize are applied during import.
- Cleartext LAN endpoints are not supported in this release.
- There is no offline recognition model. Local drawing, image preparation, key storage, editing, preview, and copying work on-device; provider conversion needs connectivity.

## Drawing and tablet controls

The drawing canvas accepts finger and stylus input. A tap produces a dot, and historical S Pen samples are retained so tight loops and small symbols are not discarded. Hold the S Pen side button while the tip is on the canvas to erase temporarily; releasing it returns to the selected pen/eraser tool. Two-finger touch gestures do not create ink, and strokes are clipped to the white canvas. Drawing export and provider request preparation run away from the UI thread.

Selected photos can be tapped to open a large review dialog before conversion. Converted output can be closed from the result-card close button. Appearance options are available in Provider Settings and are stored locally as a non-secret preference.

## Third-party notices

KaTeX 0.18.1 is bundled under the MIT License. Its license text is included at `app/src/main/assets/katex/LICENSE.txt`. Other dependency license information is available from their respective upstream projects and Gradle metadata.
