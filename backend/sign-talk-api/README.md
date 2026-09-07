# sign-talk-api

Backend for SignTalk: dictionary CRUD, user profiles, recognition logs/stats,
and trained-model version info -- the "Backend (Node.js)" + "Database
(MongoDB)" pieces from the project's tech stack. Firebase stays
authentication-only; this API verifies the ID tokens Firebase issues and
owns everything else (dictionary content, user profile/role, logs, model
metadata).

## Stack

- **Express** -- HTTP layer
- **MongoDB + Mongoose** -- dictionary entries, user profiles, recognition
  logs, model version history
- **firebase-admin** -- verifies ID tokens the Android app gets from
  Firebase Auth (this API never handles passwords itself)

## Setup

1. **Install dependencies**
   ```
   npm install
   ```

2. **Start MongoDB.** Either run it locally with Docker:
   ```
   docker compose up -d
   ```
   or point `MONGODB_URI` (next step) at a free-tier MongoDB Atlas cluster instead.

3. **Configure environment**
   ```
   cp .env.example .env
   ```
   For local development without a Firebase project set up yet, the default
   `.env.example` already has `AUTH_DEV_BYPASS=true` -- every request is
   treated as a fixed admin dev user, no real token needed. **Set this to
   `false` and fill in the three `FIREBASE_*` values (from your Firebase
   project's service account JSON) before this touches real users** -- the
   server refuses to start with bypass on if `NODE_ENV=production`.

4. **Seed real data** (both scripts are safe to re-run any time)
   ```
   npm run seed:dictionary       # loads the actual 50-word vocabulary
   npm run seed:model-version    # registers ai/models/metadata.json as the active model
   ```
   `seed:model-version` expects `AI_MODELS_METADATA_PATH` (in `.env`) to
   point at `ai/models/metadata.json` -- the default assumes the standard
   repo layout (`backend/sign-talk-api` next to `ai/`). Run this again after
   every `python train.py` so `/api/model/version` reflects the latest model.

5. **Run it**
   ```
   npm run dev     # nodemon, restarts on file changes
   npm start       # plain node
   ```
   Then check `http://localhost:4000/api/health`.

## API overview

All routes are under `/api`. Auth is a `Authorization: Bearer <Firebase ID token>` header.

| Method | Route | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | none | liveness check |
| GET | `/dictionary` | none | list entries; `?language=FSL\|ASL`, `?category=`, `?q=` (text search) |
| GET | `/dictionary/:slug` | none | one entry |
| POST | `/dictionary` | admin | add an entry |
| PATCH | `/dictionary/:slug` | admin | edit an entry |
| DELETE | `/dictionary/:slug` | admin | remove an entry |
| POST | `/users/sync` | logged in | create/update the caller's profile (call right after Firebase login) |
| GET | `/users/me` | logged in | caller's profile |
| PATCH | `/users/me` | logged in | update `displayName` |
| POST | `/logs/recognition` | optional | record a recognition event (`predictedSlug`, `confidence`, ...) |
| PATCH | `/logs/recognition/:id` | optional | attach user feedback (`wasCorrect`) to a logged prediction |
| GET | `/logs/recognition` | admin | paginated raw log list |
| GET | `/logs/stats` | admin | aggregate usage stats (top predicted signs, self-reported accuracy) |
| GET | `/model/version` | none | the currently-active trained model's metadata |
| GET | `/model/versions` | none | full training-run history |
| POST | `/model/versions` | admin | register a training run manually (`seed:model-version` is the CLI equivalent) |

Every write to `/dictionary` and `/model/versions`, plus reading logs/stats,
requires `role: "admin"` on the caller's User document. Bootstrap an admin by
listing their email in `ADMIN_EMAILS` before their first `POST /users/sync`.

## Testing

```
npm test
```

Runs Jest + Supertest against an in-memory MongoDB (`mongodb-memory-server`)
with `AUTH_DEV_BYPASS=true`, so it needs no real database or Firebase
project. See `test/` for the covered cases.

## Project layout

```
src/
├── config/       env loading, MongoDB connection, Firebase Admin init
├── models/       Mongoose schemas (User, DictionaryEntry, RecognitionLog, ModelVersion)
├── middleware/   auth (Firebase token verification + dev bypass), validation, error handling
├── controllers/  request handlers per resource
├── routes/       Express routers wiring controllers to paths
├── scripts/      one-off/CLI scripts (seeding, model version registration)
├── app.js        Express app assembly (middleware + routes)
└── server.js     entry point: connects to Mongo, starts listening
```

## Notes for the Android app integration

- Sign in with Firebase Auth as usual, then call `POST /users/sync` with the
  ID token once so this API has a profile for the user (and picks up
  `ADMIN_EMAILS` bootstrapping if applicable).
- `GET /model/version` tells the app which label order / class count the
  currently-deployed `sign_lstm.pt` uses -- useful for keeping an on-device
  exported model in sync with what training last produced.
- `POST /logs/recognition` is best-effort telemetry, not a blocking call --
  don't wait on it before showing a recognition result to the user.
