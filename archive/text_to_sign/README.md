# Archived: Text-to-Sign (avatar/translate feature)

Archived on 2026-08-31 at the user's request ("archive the 2-way
communication" -> scoped down to just this half, via a clarifying
question -- Sign-to-Text recognition, which predates this feature and
lives in `ui/recognition`/`recognition/*`, was left live in the app).

This folder holds the complete Text-to-Sign feature exactly as it stood
at archive time: type or speak a word/phrase and watch it animated as a
landmark-driven hand + 2-bone-IK arm over a static character portrait.
It's a straight `mv` out of the app module (not deleted), under the same
relative path it lived at, so restoring is copying it back.

## What's here

```
android/SignTalk/app/src/main/
├── java/com/example/signtalk/
│   ├── ui/translate/TranslateFragment.kt
│   ├── ui/translate/TranslateViewModel.kt
│   ├── ui/components/SignAvatarView.kt      (landmark hand + IK arm renderer)
│   └── recognition/SignLandmarksProvider.kt (loads sign_landmarks.bin)
├── res/layout/fragment_translate.xml
└── assets/
    ├── sign_landmarks.bin   (105 signs x 30 frames x 126 floats, built from
    │                         ai/dataset/raw/*/*.npy -- see the project's
    │                         chat history/notes for the one-off build
    │                         script; it was never committed to the repo)
    ├── avatar_woman.png
    └── avatar_man.png
```

## What was removed from the live app to disable it

- `res/navigation/nav_graph.xml`: the `translateFragment` `<fragment>`
  node and the `homeFragment`'s `action_home_to_translate` `<action>`.
- `res/layout/fragment_home.xml`: the `translateCard` `MaterialCardView`
  ("Text to Sign" 🤟 card).
- `java/com/example/signtalk/ui/home/HomeFragment.kt`: the
  `binding.translateCard.setOnClickListener { ... }` block.

Sign-to-Text (camera recognition + speech output, `RecognitionFragment` /
`recognition/HandLandmarkerHelper.kt`, `RecognitionEngine.kt`,
`SequenceBuffer.kt`, `SignClassifier.kt`, `SpeechOutput.kt`,
`LandmarkNormalizer.kt`) was left untouched and is still live.

## How to restore

1. Copy the four files under `java/` and the layout under `res/layout/`
   back to the same relative path under
   `android/SignTalk/app/src/main/`.
2. Copy the three files under `assets/` back to
   `android/SignTalk/app/src/main/assets/`.
3. In `res/navigation/nav_graph.xml`, re-add (inside `homeFragment`):
   ```xml
   <action
       android:id="@+id/action_home_to_translate"
       app:destination="@id/translateFragment" />
   ```
   and, as a sibling of the other top-level `<fragment>` entries:
   ```xml
   <fragment
       android:id="@+id/translateFragment"
       android:name="com.example.signtalk.ui.translate.TranslateFragment"
       android:label="Text to Sign"
       tools:layout="@layout/fragment_translate" />
   ```
4. In `res/layout/fragment_home.xml`, re-add the `translateCard`
   `MaterialCardView` (see this repo's git history just before the 2026-08-31
   archive commit for the exact block, right after the `recognitionCard`
   card).
5. In `HomeFragment.kt`, re-add:
   ```kotlin
   binding.translateCard.setOnClickListener {
       findNavController().navigate(R.id.action_home_to_translate)
   }
   ```

Not build-verified either as archived or as it stood live before this --
same standing limitation as the rest of this project (no Android
toolchain reachable from the assistant session). See the project's
`mobile-app-scaffold.md` notes for the feature's full history: what it
does, several rounds of bugfixes (animation not showing, hand reaching
into the character's face, invisible arm, restyled away from a
"skeletal" look), and what was still an open follow-up at archive time.
