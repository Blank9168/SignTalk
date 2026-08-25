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
  `AuthRepository` interface, and the dictionary uses an on-device Room
  database seeded with the 12 signs from `ai/dataset/collect_data.py`.

## Two model files this scaffold does NOT include

The build will compile and run without these, but the recognition screen
will show "Recognition model unavailable" until they're added to
`app/src/main/assets/`:

1. **`hand_landmarker.task`** -- MediaPipe's pretrained hand-landmark model.
   Download from Google's MediaPipe model host (search "MediaPipe Hand
   Landmarker task file" -- it's the standard `hand_landmarker.task` used in
   every MediaPipe Android sample, not something you train). Was not
   bundled here because that host wasn't reachable from the sandbox this
   was generated in.

2. **`sign_lstm.tflite`** -- a TFLite export of *your own* trained
   `ai/models/sign_lstm.pt` (the BiLSTM described in the proposal notes).
   Convert with something like:
   ```python
   import torch
   model.eval()
   dummy_input = torch.randn(1, 30, 126)  # (batch, sequence, features)
   torch.onnx.export(model, dummy_input, "sign_lstm.onnx", opset_version=17,
                      input_names=["input"], output_names=["output"])
   # then use onnx2tf or onnx-tf to get a TFLite file, or use
   # ai-edge-torch (torch -> tflite directly) if available in your env.
   ```
   The exact conversion path depends on your PyTorch/TF toolchain -- there
   isn't a single canonical command. Whatever you use, the exported model
   must accept a `(1, 30, 126)` float32 input and return `(1, 12)` float32
   logits/probabilities in the same class order as `assets/labels.json`.

Until both are present, flip **Settings -> "Use sample predictions"** to
demo the recognition screen end-to-end (camera + hand detection still need
`hand_landmarker.task`; only the classifier is mocked).

## Swapping in the 105-class experimental model

The separate "signtalk-mini" sandbox experiment (see the project's other
doc) trained a 105-class model on all of FSL-105, not just the 12 signs in
the capstone's official scope. If you want the mobile app to use that
model instead:

1. Replace `app/src/main/assets/labels.json` with a version built from
   `labels_105.json` (same `{"labels":[{"label","displayName","emoji"}]}`
   shape, index order must match the model's output order).
2. Convert that experiment's `sign_lstm.pt` (105 classes) to
   `sign_lstm.tflite` the same way as above, and drop it into `assets/`.
3. No Kotlin code changes needed -- `SignClassifier` sizes its output layer
   from `labels.json` at runtime.

## Swapping mock auth for real Firebase Authentication

`AuthRepository` (in `domain/repository/`) is the seam. Implement it with
real `FirebaseAuth` calls in a new `FirebaseAuthRepository`, add the
Firebase Android SDK + `google-services.json` + the Google Services Gradle
plugin, and change one line in `di/AppContainer.kt`
(`val authRepository: AuthRepository by lazy { ... }`). No screen or
ViewModel needs to change.

## Before your first Gradle sync

This was written without a live connection to Maven Central / Google's
Maven repo (the sandbox it was built in couldn't reach either), so library
versions in `gradle/libs.versions.toml` were chosen from what's normally
stable rather than confirmed against the registry at write time. If Gradle
sync flags a missing version for any of the newly added libraries
(Compose, Navigation, Room, CameraX, MediaPipe Tasks Vision, TensorFlow
Lite, DataStore, KSP), bump just that one `[versions]` entry to the nearest
available release -- Android Studio's sync error usually suggests one
directly. The pre-existing `agp`/Gradle versions in this project were left
untouched.

A few specific spots worth a second look on first build, since their exact
API surface can shift between library minor versions:
- `ImageProxy.toBitmap()` in `camera/CameraPreview.kt` (added in CameraX
  1.3; needs `ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888`, which is set).
- The `HandLandmarker.HandLandmarkerOptions` builder chain in
  `recognition/HandLandmarkerHelper.kt`.
- `viewModelFactory { initializer { ... } }` in `ui/navigation/SignTalkNavGraph.kt`.

None of these are exotic APIs -- they're the standard patterns from each
library's own samples -- but this scaffold couldn't be compiled in the
sandbox it was written in, so treat the first local build as the real
correctness check.

## Everything else that's still a placeholder

- Dictionary seed content (`data/dictionary/DictionarySeed.kt`) is
  plain-language gloss text, not reviewed FSL reference material -- swap
  in real descriptions/images before this ships.
- No app icon beyond Android Studio's default launcher icon.
- No tests yet.
