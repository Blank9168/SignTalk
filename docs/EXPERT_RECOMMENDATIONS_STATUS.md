# Expert recommendations: implementation status

Source: *SignTalk_Expert_Recommendations_updated.docx*. Updated 2026-10-10.

**Legend**
- ✅ **Done in code.** Build and test it on a phone.
- 🧪 **Tool ready.** Run it and put the output in the manuscript.
- 👥 **Team action.** Needs people, data or a decision; code can't do it.
- 📄 **Manuscript only.**

> The Android changes were checked by reading the code only; nothing was compiled, because the workspace that made these changes can't download the Android SDK. Build in Android Studio first (**Build > Rebuild Project**) and fix anything it flags. The backend tests pass: 116 of 116.

---

## Before the defense: the five things that matter most

1. 👥 **FSL vs ASL.** The 50 signs are **25 FSL + 25 ASL**, not "25 English and 25 Filipino words". `docs/sign_table.md` states this.
   - Have an FSL validator mark each ASL sign as *same in FSL* or *different*.
   - Record the result in `ai/dataset/sign_metadata.json` (`validationStatus`, `validatedBy`), then run `python ai/dataset/export_sign_metadata.py`.
2. 📄 **CNN vs LSTM.** The deployed model is a **bidirectional LSTM on MediaPipe hand landmarks** (30 frames × 126 features). `ai/evaluation/reports/model_card.md` has the wording for the manuscript.
3. 👥🧪 **Signer-independent test.** Record 2–3 people who are not in any training data, then run `ai/evaluation/prepare_signer_independent.py` (protocol: `ai/evaluation/signer_independent/README.md`).
4. ✅ **Unknown / Not sure handling.** Implemented in the app (see the recognition screen below). For a real `none` class, record non-sign clips and train with `--include-none`.
5. 👥 **`AUTH_DEV_BYPASS` on Render.** Open the Render dashboard → service → Environment.
   - It must be absent or `false`, and `NODE_ENV=production`.
   - While it's on, anyone can edit the live dictionary.

---

## Interface and overall UI/UX

| Recommendation | Status | Where / what |
|---|---|---|
| Consistent design system | ✅ | Status colors and confidence colors are now color resources (`values/colors.xml`, night variants in `values-night/colors.xml`). New shared styles: `Widget.SignTalk.Button.Control`, `Widget.SignTalk.Button.Overlay`, `SignTalk.StatusPill`. |
| Large buttons and text | ✅ | Recognition controls are 64dp tall, overlay buttons 48dp. The recognized word is 36–72sp. |
| Action-oriented Home | ✅ | Four equal cards: **Recognize Sign, Text / Speech to Sign, Dictionary, Add a Sign**. |
| Icons always with text labels | ✅ | Transcript **Speak / Clear**; recognition **Try again / Replay / Sound on–Muted / Rear–Front cam**; Home **Settings** and **How to use**. |
| Helpful empty and error states | ✅ | "Move your hand into the frame", "Too dark. Improve the lighting.", "Move your hand closer", "Keep your whole hand inside the frame", "Please repeat the sign". Unknown words in Text to Sign get "Did you mean" suggestions. |
| Strong contrast for results | ✅ | Big result word, plus a **High contrast** theme (black, white and yellow). |
| Loading / processing / success / warning / error states | ✅ | Status pill: Starting, Ready, Detecting (info), Recognized (green), Not sure (amber), Unknown sign / Error (red). Each state has its own icon, color and vibration pattern. |
| First-time tutorial | ✅ | `ui/onboarding/OnboardingFragment.kt`: 5 pages (what it does, camera position and distance, light and background, reading the result, features). Shown once; reopen from Home > How to use or from Settings. |
| Less text per screen | ✅ | Shorter Home and Translate copy. Tagline changed from "Two-way Filipino Sign Language communication" to "Recognizes selected FSL and ASL signs". |

## Gesture recognition screen

| Recommendation | Status | Where / what |
|---|---|---|
| Hand guide / frame | ✅ | Dashed frame over the camera with "Keep your hands inside the frame". |
| Large status (Detecting / Recognized / Not sure) | ✅ | Status pill above the word. |
| Don't accept low confidence; "Not sure – repeat" | ✅ | `RecognitionEngine.decide()`. Below the 0.6 threshold and at or above 0.35 → **Not sure, please repeat the sign**. Below 0.35, or the `none` class → **Unknown sign**. |
| Big word, smaller confidence | ✅ | Unchanged. The confidence row is now smaller. |
| Stability period | ✅ | **3 confident predictions of the same class in a row** before a sign is accepted. One frame is no longer enough. |
| No repeated speech while holding a sign | ✅ | Spoken once per accepted sign. Signing the same word again after a pause counts as a new sign. |
| Try Again and Clear buttons | ✅ | **Try again** resets the attempt; **Clear** on the transcript bar. |
| Camera-switch button | ✅ | On screen, shown only if the phone has both cameras. |
| Left hand, two hands, mirroring, occlusion, distance, backgrounds | ✅🧪 | The code puts a single hand in one canonical slot (left- or right-handed works) and slots two hands by handedness or position. Testing uses the template `ai/evaluation/robustness_matrix_template.csv` (96 conditions, 8 marked core). |
| Unknown / Unsupported result | ✅ | **Unknown sign** state. Optional `none` class supported end-to-end (see ML). |
| Example after repeated failures | ✅ | After 3 failed attempts in a row, a **"Did you mean X?"** card plays the top guess's reference video. |

## Gesture-to-text and speech output

| Recommendation | Status | Where / what |
|---|---|---|
| Text always prominent | ✅ | Speech is never the only output. |
| Replay speech | ✅ | **Replay** button (works even when muted). |
| Mute and speech rate | ✅ | **Sound on / Muted** on the recognition screen; rate slider in Settings. |
| Correct TTS language | ✅ | `SpeechOutput`: FSL signs use **fil-PH**, ASL signs use **en-US**. Falls back to the device default, then English, if a voice is missing. Mixed sentences switch language word by word. |
| Don't speak uncertain predictions | ✅ | Speech only follows an accepted (stable) sign. |
| Measure latency in ms | ✅🧪 | Settings > **Performance logging**. The phone writes `latency.csv` (hand → accepted → text shown → speech started, in ms) to `Android/data/com.example.signtalk/files/performance/`. Do 50+ trials and report the mean and 95th percentile. |

## Text/Speech-to-Sign

| Recommendation | Status | Where / what |
|---|---|---|
| Say it is dictionary lookup, not translation | ✅ | Notice on the screen; the tab is renamed "Text to Sign". |
| Suggest supported words | ✅ | Spelling-distance suggestions as tappable chips ("thnk" → Thank You). |
| Search by English and Filipino meanings | ✅ | Every sign has synonyms and meanings ("blue" → Asul, "salamat" → Thank You, "hindi ko alam" → Don't Know). |
| Internet and privacy notice for voice input | ✅ | Next to the Speak button. |
| Replay sign video | ✅ | **Replay** on Text to Sign and on each dictionary entry. |
| Slow playback | ✅ | **Slow (0.5x)** toggle on both screens. |
| Gloss shown separately from meanings | ✅ | "FSL sign · Gloss: ONE" / "English: One" / "Filipino: Isa". |

## Sign dictionary

| Recommendation | Status | Where / what |
|---|---|---|
| Every sign validated by an FSL expert | 👥 | All 50 are `unverified` until a validator signs off; the app shows that status on each entry. |
| Show source and validation status | ✅ | "About this sign" card on each entry; status also appears in the list ("Personal, not verified", "In review", "Verified"). |
| Categories | ✅ | FSL/ASL, Numbers, Colors, Greetings, Responses, People. Add a Sign also offers Needs, Emergency, School, Food and Common Questions. |
| Tags and synonyms | ✅ | `ai/dataset/sign_metadata.json` → Room column `synonyms`, used by dictionary search and Text to Sign. |
| Static/dynamic and one/two hands | ✅👥 | Filled in for all 50 from the reference videos. **The validator must confirm them.** |
| Offline core dictionary | ✅ | Unchanged: bundled Room seed and videos. Room schema v3 has a migration, so existing installs keep the user's entries. |
| Clear media | 👥 | Re-record the 6 joined ASL phrase clips (Good Morning, Good Afternoon, Good Evening, How Are You, Nice To Meet You, See You Tomorrow) with one signer. |

## Add a Sign

| Recommendation | Status | Where / what |
|---|---|---|
| Review flow Submitted → For Review → Verified → Published | ✅ | Backend `/api/submissions`. The app saves the entry locally as **Personal, not verified** and, if the user agrees, submits it. It shows **In review** once the server accepts it. |
| Only admin/validator can approve | ✅ | `requireRole('validator','admin')` on review routes. **Turn off `AUTH_DEV_BYPASS` and wire Firebase login for this to protect anything.** |
| Notice that it doesn't teach the recognizer | ✅ | At the top of the dialog. |
| Required fields | ✅ | Name, English meaning, Filipino meaning, category, language, sign type, hands, source, and a demonstration video (file and/or link). Enforced in the app and on the server. |
| Duplicate check | ✅ | In the app (label, name, synonym, same meaning) and on the server (409). |
| Submitter, dates, status, validator, last update | ✅ | `Submission` model: `submittedBy`, `submitterName`, `statusHistory`, `reviewedBy`, timestamps. |
| Edit / Archive / Reject instead of delete | ✅ | Backend: `DELETE` now archives; `/archive`, `/restore`, `/verify`, and review action `reject`. In the app only the user's own personal entries can be deleted, after a confirmation. |

## Accessibility

| Recommendation | Status | Where / what |
|---|---|---|
| Visual feedback for everything | ✅ | No audio-only cues. |
| Optional vibration | ✅ | Settings > Vibration (on by default): one buzz when a sign is accepted, two for Not sure / Unknown. |
| Large touch targets away from edges | ✅ | Controls sit in a row under the output panel, at least 48dp. |
| High-contrast option | ✅ | Settings > High contrast. |
| Simple language | ✅ | Shorter copy throughout. |
| Respectful terms | ✅📄 | "deaf/mute" removed from code comments. Use **Deaf/signing users** and **hearing/non-signing users** in the manuscript; search it for "normal users". |
| Separate on/off for feedback types | ✅ | Speech, Sound effects, Vibration are separate settings. |

## Background music and sound effects

| Recommendation | Status | Where / what |
|---|---|---|
| No background music by default | ✅📄 | There is no background music at all (a design decision; state it in the manuscript). |
| Short success/error sounds with visuals | ✅ | Settings > Sound effects (off by default). Uses Android's built-in `ToneGenerator` tones. |
| Duck audio during TTS | n/a | No background audio exists. |
| Separate controls | ✅ | See Accessibility. |
| Licensed sounds + source record | ✅📄 | System tones only; no third-party audio files. |

## Machine learning and recognition quality

| Recommendation | Status | Where / what |
|---|---|---|
| Fix CNN vs LSTM | 📄 | `ai/evaluation/reports/model_card.md`. |
| Dataset profile | 🧪 | `ai/evaluation/reports/dataset_profile.md`: 1,324 clips, 50 classes, 9–37 per class. **Fill in signers and sessions** in `ai/evaluation/signer_sessions_template.csv`. |
| Signer-independent test | 👥🧪 | See the top five, item 3. |
| Accuracy, precision, recall, F1, confusion matrix | 🧪 | `ai/evaluation/reports/deployed_v1.1_orig_val/`: 96.6% validation accuracy, macro F1 0.936. This split was also used for early stopping, so call it *validation* accuracy, not *test* accuracy. |
| Most-confused pairs and why | 🧪📄 | `most_confused_pairs.csv`. Likely reasons are in `ai/evaluation/README.md`. |
| Document confidence threshold | 🧪📄 | `ai/evaluation/reports/deployed_v1.1_threshold_sweep/`. Keep **0.6** with the 3-in-a-row rule. Revisit once real non-sign clips exist (synthetic non-signs: 36% accepted at 0.6, 18% at 0.8). |
| Lighting, background, angle, distance, device test | 👥🧪 | `robustness_matrix_template.csv`. |
| FPS and inference time on low/mid-range phones | ✅🧪 | Turn on Performance logging. FPS and ms appear on the camera screen. |
| Battery and temperature | ✅🧪 | Same setting: `sessions.csv` records battery % and temperature at the start and end of each session. Run 15 minutes per phone. |

## Backend, security and administration

| Recommendation | Status | Where / what |
|---|---|---|
| Use Case: Administrator role | 📄 | Actors: User, **Validator** (review, verify, edit), **Administrator** (all of that plus archive, restore and roles). |
| Expanded ERD | 📄 | Entities that now exist: User (role), DictionaryEntry (+ metadata, validation, archive), Submission (+ statusHistory), AuditLog, RecognitionLog, ModelVersion. |
| Role-based access control | ✅ | `user`, `validator`, `admin`; `PATCH /api/users/:id/role`. |
| HTTPS | ✅ | Unchanged (Render). |
| Input validation and rate limiting | ✅ | All write bodies are validated. Limits: 60 writes per 15 min, and 10 submissions per hour per IP. |
| Audit trail | ✅ | `AuditLog`, viewable at `GET /api/audit` (admin). |
| Offline behavior documented | ✅ | `backend/sign-talk-api/README.md`, "Offline behavior". |
| Backup and recovery | ✅ | `npm run backup` / `npm run restore -- --yes` (needs the MongoDB Database Tools). |

## Research and documentation (manuscript)

| Recommendation | Where to get it |
|---|---|
| Table of all 50 signs | `docs/sign_table.md` (generated). |
| Clarify "25 English + 25 Filipino" | First paragraph of `docs/sign_table.md`. |
| Dataset, model config, training, results | `dataset_profile.md`, `model_card.md`, `deployed_v1.1_orig_val/summary.md`. |
| Appendix A source code | MediaPipe: `recognition/HandLandmarkerHelper.kt`, `LandmarkNormalizer.kt`. TFLite: `SignClassifier.kt`. Confidence and stability: `RecognitionEngine.decide()`. TTS: `SpeechOutput.kt`. API: `RetrofitClient.kt`, `SignTalkApiService.kt`. Storage: `DictionaryDao.kt`, `RoomDictionaryRepository.kt`. |
| Questionnaire scale, separate means, direct Text→Sign and Add a Sign tests, FSL specialist evaluation | 👥📄 Survey design; not code. |
| Measured response time | `latency.csv` (see above). |
| Scope claim | "Recognition of selected FSL and ASL signs," not "FSL translation". The app wording now matches. |

---

## New settings (for testing and the manual)

| Setting | Default |
|---|---|
| Speak recognized signs | On |
| Speech rate | 1.0x |
| Confidence threshold | 60% |
| Vibration | On |
| Sound effects | Off |
| High contrast | Off |
| Performance logging | Off |

## Recognition rules (put these numbers in the manuscript)

| Rule | Value |
|---|---|
| Confidence threshold | 0.60 |
| Stability | 3 consecutive confident predictions of the same class |
| "Not sure" band | 0.35 ≤ confidence < 0.60 |
| Unknown | confidence < 0.35, or `none` class |
| Hold after acceptance | up to 6 low-confidence frames before the word clears |
| Failed attempt | 15 frames of continuous doubt |
| "Did you mean" example | after 3 failed attempts in a row |
| Earliest prediction | 12 hand frames (resampled to 30) |
