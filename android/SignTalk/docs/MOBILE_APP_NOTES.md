# SignTalk Android app -- integration notes

Generated as a scaffold for the `android/SignTalk` module described in the
capstone proposal notes: Kotlin + Jetpack Compose, MVVM, on-device MediaPipe
+ TFLite recognition, local-first data (no backend/Firebase wired up yet).

## What's here

- **Screens**: splash, login/register, home, live camera recognition,
  FSL dictionary (search/view/add), settings/about.
- **Architecture**: `domain/` (models + repository interfaces) ->
  `data/` (Room for the dictionary, DataStore for settings/session, a mock
  auth repository) -> `ui/` (Compose screens + ViewModels), with `camera/`
  and `recognition/` as separate concerns per the proposal's module layout.
  Everything lives in the single `app` module as packages rather than
  separate Gradle modules, to keep the build simple; splitting into real
  Gradle modules later is a mechanical refactor if you want it.
- **No backend, no Firebase project required to run this.** Login/register
  use an in-memory mock (`data/auth/MockAuthRepository.kt`) behind the
  `AuthRepository` interface.

## Recognition model: now running the 105-class FSL-105 model (2026-08-24)

This app now ships the **105-class** model from the `signtalk-mini`
experiment (see the project's `signtalk-mini-experiment` notes and
`ai/models/metadata.json` in the main SignTalk repo) instead of the original
12-sign capstone-scope set. Both model assets live in
`app/src/main/assets/`:

1. **`sign_lstm.tflite`** -- included. Converted from the trained
   `ai/models/sign_lstm.pt` (105-class BiLSTM, 94.5% val accuracy) via
   `torch.onnx.export` (opset 17, fixed batch size 1) -> `onnx2tf`
   (`-kat input` to stop it from transposing the `(1, 30, 126)` sequence
   input -- onnx2tf's default NCHW-vision heuristic doesn't apply to a
   sequence model and will silently reorder the axes without that flag).
   **Verified**: loaded the exported `.tflite` in the `ai-edge-litert`
   interpreter and compared its output against the original PyTorch model
   on 20 random `(1, 30, 126)` inputs -- max absolute logit difference
   `1.3e-5`, argmax (predicted class) matched on all 20. Shipped the
   float32 export (2.86 MB); a float16 export was also produced by the same
   run if you want a smaller asset (~1.46 MB) at negligible accuracy cost --
   not used here since it wasn't clearly better for a file this small.

2. **`hand_landmarker.task`** -- **still NOT included.** MediaPipe's
   pretrained hand-landmark model is fetched from
   `https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task`
   (confirmed this is the exact URL Google's own official Android sample
   downloads at build time -- see
   `google-ai-edge/mediapipe-samples/examples/hand_landmarker/android`).
   That host is blocked from every sandbox environment available to Claude
   here (cloud sandbox and the device-bridge shell both hit
   `403 blocked-by-allowlist`), and this session couldn't find a mirror of
   it on a reachable host (GitHub's own sample repos fetch it the same way
   rather than committing the binary; Hugging Face, which does host copies,
   was unreachable too). **You'll need to grab this one yourself**: open
   the URL above in a browser (or search "MediaPipe Hand Landmarker task
   file" -- it's the standard file used in every MediaPipe Android sample)
   and drop the downloaded file straight into `app/src/main/assets/`. It's
   a small (~7-8 MB), fixed, pretrained file -- not something you train or
   that changes with the sign-recognition model.

Until `hand_landmarker.task` is added, the recognition screen shows a clear
"model unavailable" state (camera + hand detection need it; the classifier
itself is ready either way). **Settings -> "Use sample predictions"** still
demos the screen with randomized results in the meantime.

No Kotlin code changes were needed to swap in the 105-class model --
`SignClassifier` sizes its output layer from `assets/labels.json` at
runtime, and `assets/labels.json` was regenerated (105 entries, in the
exact index order `ai/models/metadata.json` trained against) from
`ai/dataset/labels_105.json`.

### If you already ran an earlier build of this app

Room only seeds the dictionary **when the table is empty**
(`RoomDictionaryRepository.seedIfEmpty()`), so a device/emulator that
already launched the app once with the old 12-sign seed will keep those 12
rows and never pick up the new 105-entry seed automatically. Clear the
app's storage (or uninstall/reinstall) to get the new seed on such a
device. A fresh install is unaffected.

### Dictionary content is still placeholder copy -- more so than before

`data/dictionary/DictionarySeed.kt` now has all 105 entries (label, display
name, category, emoji), but the **description** field for all 105 is a
generic placeholder (`"Placeholder entry for the FSL sign ... No reviewed
gloss/motion description yet"`), not a real hand-shape/motion gloss. The
original 12-sign seed had hand-authored (if unreviewed) motion
descriptions; for these 105, there wasn't a reliable source to describe
each sign's actual hand shape from, so nothing was invented -- write real,
reviewed descriptions (and ideally reference images/clips) before this
ships. Categories and emoji were assigned automatically from the FSL-105
dataset's own category labels and are a reasonable starting point.

## Swapping mock auth for real Firebase Authentication

`AuthRepository` (in `domain/repository/`) is the seam. Implement it with
real `FirebaseAuth` calls in a new `FirebaseAuthRepository`, add the
Firebase Android SDK + `google-services.json` + the Google Services Gradle
plugin, and change one line in `di/AppContainer.kt`
(`val authRepository: AuthRepository by lazy { ... }`). No screen or
ViewModel needs to change.

## Everything else that's still a placeholder

- No app icon beyond Android Studio's default launcher icon.
- No tests yet.
