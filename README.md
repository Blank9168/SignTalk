# SignTalk

**AI-based recognition of selected Filipino Sign Language (FSL) and American
Sign Language (ASL) gestures, with text/speech output and a bilingual sign
dictionary.**

A BSIT capstone project. SignTalk recognizes a defined vocabulary of 50 signs
(25 FSL + 25 ASL) via camera and outputs the recognized sign as text and
speech, alongside a searchable sign-language dictionary with real reference
video for every entry.

**Proponents:** Farre, Angelica C. · Sapnu, Joshua M. · Tolentino, John Luis E.
**Adviser:** Erl Xander G. Labung

## What's actually built (vs. the original proposal)

The original capstone proposal described a raw-image CNN pipeline, a
Jetpack Compose UI, and a login/register screen. The implementation below
reflects what's actually running today, which diverged from that proposal
during development:

- **Recognition pipeline:** MediaPipe hand-landmark extraction (126 features
  — 2 hands × 21 landmarks × xyz) per frame → 30-frame sequences → a
  bidirectional LSTM classifier. No raw-image CNN, no background
  subtraction — MediaPipe's landmarker handles hand localization.
- **Mobile UI:** Kotlin with Fragments + XML Views + Jetpack Navigation
  Component (not Compose) — a deliberate pivot made before this repo's
  current state.
- **Auth:** no login gate currently exists in the app (`SplashFragment`
  routes straight to Home). The backend supports real Firebase-token auth,
  but currently runs in a dev-bypass mode since no Firebase project has
  been created yet.

## Repo structure

```
SignTalk/
├── android/SignTalk/       Android app (Kotlin, Views + Navigation Component)
│   └── app/src/main/
│       ├── java/.../ui/dictionary/   FSL/ASL dictionary (50 signs, video playback)
│       ├── java/.../recognition/     Camera + MediaPipe + LSTM inference (Sign-to-Text)
│       ├── java/.../data/remote/     Retrofit client for the backend API
│       └── assets/sign_videos/       Bundled reference clips, one per dictionary entry
├── backend/sign-talk-api/  Node.js/Express + MongoDB API
│   └── src/                Dictionary CRUD, user sync, recognition logs, model version info
├── ai/                     Python training/inference pipeline
│   ├── dataset/             Data collection, import, and extracted landmark samples
│   ├── training/            SignLSTM training (dataset.py, train.py)
│   ├── evaluation/          Live webcam inference / video prediction
│   └── models/              Trained model artifacts (sign_lstm.pt, metadata.json, etc.)
└── archive/                Retired features (kept, not deleted — see each folder's own README)
```

## The 50-sign vocabulary

25 FSL signs (10 Numbers, 12 Colors, 3 Greetings) + 25 ASL signs (Greetings,
Basic Responses, Family, People & Relationships). All 50 have real training
footage and a real bundled reference video in the dictionary — see
`android/SignTalk/app/src/main/java/com/example/signtalk/data/dictionary/DictionarySeed.kt`
for the exact list.

## Getting started

### Android app

1. Open `android/SignTalk` in Android Studio.
2. Gradle sync will pull all dependencies (Room, CameraX, MediaPipe Tasks
   Vision, Retrofit/OkHttp).
3. Run on an emulator or device. The dictionary and Sign-to-Text recognition
   work fully offline using the bundled model and video assets. Backend sync
   (Settings > About's model-version line) requires the API below to be
   running and reachable — see `data/remote/NetworkConfig.kt` for how the
   base URL is configured for emulator vs. a real device vs. a deployed API.

### Backend API

```
cd backend/sign-talk-api
cp .env.example .env      # fill in real values, see comments in the file
docker compose up -d      # local MongoDB
npm install
npm run dev
npm run seed:dictionary
npm run seed:model-version
```

Full endpoint reference and auth details are in
`backend/sign-talk-api/README.md`.

### AI pipeline (training / retraining the recognition model)

```
cd ai/training
python train.py
```

Requires a Python environment with `torch`, `scikit-learn`, and the rest of
`ai/`'s dependencies. See each script's own docstring for details — the
pipeline is organized as `dataset/` (data prep) → `training/` (train.py) →
`evaluation/` (predict.py) → `models/` (artifacts).

## Deployment

The backend can be hosted on any standard Node.js host (e.g. Render,
Railway) backed by MongoDB Atlas; point the Android app's
`NetworkConfig.BASE_URL` at the deployed API's HTTPS URL and rebuild. For
distributing the app itself, build a signed release APK via Android
Studio's Build > Generate Signed Bundle / APK.
