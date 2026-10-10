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

   `seed:dictionary` upserts by slug and passes through every field present
   in `src/scripts/dictionaryData.js` (unknown field names are warned about
   and ignored). A bad entry is logged and skipped; the summary line reports
   created / updated / failed counts and the script exits non-zero if any
   failed. Re-seeding overwrites in-app edits to the fields the data file
   sets, so run `npm run backup` first.

5. **Run it**
   ```
   npm run dev     # nodemon, restarts on file changes
   npm start       # plain node
   ```
   Then check `http://localhost:4000/api/health`.

## API overview

All routes are under `/api`. Auth is a `Authorization: Bearer <Firebase ID token>` header.

| Method | Route | Role | Purpose |
|---|---|---|---|
| GET | `/health` | none | liveness check |
| GET | `/dictionary` | none | list non-archived entries; `?language=FSL\|ASL`, `?category=`, `?validationStatus=`, `?q=` (searches label, gloss, synonyms, English/Filipino meanings, description); `?includeArchived=true` needs validator/admin |
| GET | `/dictionary/:slug` | none | one non-archived entry (`?includeArchived=true` for validator/admin) |
| POST | `/dictionary` | admin | add an entry |
| PATCH | `/dictionary/:slug` | validator, admin | edit an entry (whitelisted, validated fields only) |
| POST | `/dictionary/:slug/verify` | validator, admin | mark an entry `verified` (stamps `validatedBy`/`validatedAt`) |
| POST | `/dictionary/:slug/archive` | admin | archive (soft delete) an entry |
| POST | `/dictionary/:slug/restore` | admin | un-archive an entry |
| DELETE | `/dictionary/:slug` | admin | **alias for archive** -- nothing is ever hard-deleted |
| POST | `/submissions` | none (token optional) | propose a new sign (stricter rate limit) |
| GET | `/submissions` | validator, admin | review queue; `?status=`, `?limit=` (max 200), `?page=` |
| GET | `/submissions/:id` | validator, admin | one submission |
| POST | `/submissions/:id/review` | validator, admin | `{ "action": "start_review" \| "verify" \| "reject" \| "publish", "note": "..." }` |
| POST | `/users/sync` | logged in | create/update the caller's profile (call right after Firebase login) |
| GET | `/users/me` | logged in | caller's profile |
| PATCH | `/users/me` | logged in | update `displayName` |
| PATCH | `/users/:id/role` | admin | `{ "role": "user" \| "validator" \| "admin" }`; `:id` = Mongo `_id`, Firebase uid, or email |
| GET | `/audit` | admin | audit log; `?entityType=dictionary\|submission\|user\|model`, `?entityId=`, `?actor=<uid>`, `?action=`, `?limit=`, `?page=` |
| POST | `/logs/recognition` | none (token optional) | record a recognition event (`predictedSlug`, `confidence`, ...) |
| PATCH | `/logs/recognition/:id` | none (token optional) | attach user feedback (`wasCorrect`) to a logged prediction |
| GET | `/logs/recognition` | admin | paginated raw log list |
| GET | `/logs/stats` | admin | aggregate usage stats (top predicted signs, self-reported accuracy) |
| GET | `/model/version` | none | the currently-active trained model's metadata |
| GET | `/model/versions` | none | full training-run history |
| POST | `/model/versions` | admin | register a training run manually (`seed:model-version` is the CLI equivalent) |

Validation errors return `400 { "error": "Validation failed", "details": [...] }`.
Write bodies are whitelisted: unknown fields and empty PATCH bodies are rejected.

## Roles

Roles live on the MongoDB `User` document (`role`), not in Firebase.

| Can... | user | validator | admin |
|---|:-:|:-:|:-:|
| Browse the dictionary, submit a sign | yes | yes | yes |
| See archived entries (`?includeArchived=true`) | | yes | yes |
| Review submissions (start review, verify, reject, publish) | | yes | yes |
| Edit dictionary entries (PATCH) and verify them | | yes | yes |
| Create dictionary entries | | | yes |
| Archive / restore dictionary entries | | | yes |
| Change user roles | | | yes |
| Read the audit log, recognition logs/stats; register model versions | | | yes |

Bootstrap the first admin by listing their email in `ADMIN_EMAILS` before
their first `POST /users/sync`. After that, admins promote others with
`PATCH /users/:id/role` (a user must have synced once to exist). Admins can't
remove their own admin role. In code, use `requireRole("validator", "admin")`;
`requireAdmin` is `requireRole("admin")`.

## Dictionary entry fields

Besides `slug`, `label`, `language`, `category`, `description`, `emoji`,
`hasVideo`, `hasTrainingData`, entries carry the expert-review metadata:
`gloss` (e.g. `ISA`, stored uppercase), `meaningEn`, `meaningFil`,
`synonyms` (lowercased, trimmed; searchable), `signType` (`static` |
`dynamic`), `hands` (`one` | `two`), `source` (e.g. `FSL-105 (DLSU/DOST)`,
`ASL Citizen (Microsoft Research)`, `Team-recorded`), `validationStatus`
(`unverified` | `for_review` | `verified`), `validatedBy`, `validatedAt`,
`archived`, `archivedAt`, `archivedBy`, `createdBy`, `updatedBy`, plus
`createdAt`/`updatedAt`.

`slug` can't be changed after creation (it's the id shared with the app and
the model's label list). PATCH may set `validationStatus` only to
`unverified` or `for_review` (which clears the old verification stamp);
verifying goes through `POST /dictionary/:slug/verify`.

**Text index note:** MongoDB reserves the field name `language` in text
indexes; the dictionary text index sets `language_override:
"textSearchLanguage"` so our real `language` field ("FSL"/"ASL") doesn't
break writes. Keep that option if you touch the index. Only one text index
is allowed per collection, so `server.js` (on boot) and `seed:dictionary`
call `syncIndexes()` to replace the old `{label, description}` index.

## Archive, not delete

Dictionary entries are never hard-deleted. `DELETE /dictionary/:slug` and
`POST /dictionary/:slug/archive` set `archived: true` (+ `archivedAt`,
`archivedBy`); the entry disappears from public reads (and from the app's
next dictionary sync) but stays in the database with its history.
`POST /dictionary/:slug/restore` brings it back. Both are admin-only and
idempotent.

## Submission lifecycle

```
submitted --start_review--> for_review --verify--> verified --publish--> published
    \                           \                     \
     `---------reject------------`--------reject-------`--> rejected
```

- `POST /submissions` is public (the Android app has no login yet). If a
  valid token is sent, the caller's uid is stored as `submittedBy`.
- The server derives `slug` from `name` (`"Salamat Po"` -> `salamat_po`) and
  returns **409** if a non-archived dictionary entry matches the slug, the
  label (case-insensitive), or a synonym, or if an open submission
  (`submitted`/`for_review`/`verified`) has the same slug.
- Every transition is recorded in `statusHistory` (`status`, `by`, `at`,
  `note`) and in the audit log. Any other transition returns **409** with
  `currentStatus` and `allowedActions`.
- `publish` creates a dictionary entry from the submission with
  `validationStatus: "verified"` and links it via `publishedEntry`.
- **Submissions never affect the recognition model.** Published entries have
  `hasTrainingData: false`; the on-device TFLite model only changes when the
  team retrains from `ai/dataset` and ships a new app build.

Request body (`POST /api/submissions`, JSON, unknown fields rejected):

```json
{
  "name": "Salamat Po",
  "meaningEn": "Thank you (polite)",
  "meaningFil": "Salamat po",
  "category": "FSL - Greetings",
  "signType": "dynamic",
  "hands": "one",
  "source": "Team-recorded",
  "gloss": "THANK-YOU",
  "language": "FSL",
  "description": "optional",
  "mediaUrl": "https://example.com/salamat.mp4",
  "notes": "optional",
  "submitterName": "optional"
}
```

Required: `name`, `meaningEn`, `meaningFil`, `category`, `signType`,
`hands`, `source`. Responses: `201` with the saved submission
(`_id`, `slug`, `status: "submitted"`, ...), `400` validation error, `409`
`{ "error", "conflict": { "type": "dictionary"|"submission", "id", "slug",
"label", "matchedOn", ... } }`, `429` rate-limited.

## Audit log

Every admin/validator write (dictionary create/update/verify/archive/restore,
submission review, role changes, model version registration) and every
public submission writes an `AuditLog` row: `actorUid`, `actorRole`, `action`
(e.g. `dictionary.update`, `submission.review`, `user.role`), `entityType`,
`entityId`, `before`, `after`, `ip`, `at`. Audit failures are logged and
never fail the request. Read it with `GET /api/audit` (admin).

## Rate limits

Per client IP, in memory (reset on restart/redeploy):

| Scope | Limit |
|---|---|
| All requests | 300 / 15 min (`RATE_LIMIT_GLOBAL_MAX`) |
| Non-GET requests | 60 / 15 min (`RATE_LIMIT_WRITE_MAX`) |
| `POST /submissions` | 10 / hour (`RATE_LIMIT_SUBMISSION_MAX`) |

`/logs/recognition` telemetry is exempt from the write limit (it still counts
toward the global one) so a demo with many phones on one Wi-Fi can't use up
the write budget. Exceeding a limit returns `429 { "error": ... }`.

Render sits behind a proxy, so the app sets Express `trust proxy` from
`TRUST_PROXY` (default `1` when `NODE_ENV=production`). Without it every
client shares the proxy's IP and one rate-limit bucket. If `GET /api/audit`
shows the same `ip` for everyone, raise `TRUST_PROXY` to `2`.

## Deploying on Render: check AUTH_DEV_BYPASS

`AUTH_DEV_BYPASS=true` makes **every request an admin** with no token. The
server refuses to boot with it when `NODE_ENV=production`, but that guard
only works if `NODE_ENV` is actually set. In the Render dashboard ->
service -> Environment, confirm `AUTH_DEV_BYPASS` is absent or `false` and
`NODE_ENV=production`, and that the three `FIREBASE_*` values are set.

## Backup and restore

Needs the [MongoDB Database Tools](https://www.mongodb.com/docs/database-tools/installation/)
(`mongodump`, `mongorestore`) on your machine and `MONGODB_URI` in `.env`.

```
npm run backup
# -> backups/signtalk-<timestamp>.gz
#    (mongodump --uri "$MONGODB_URI" --gzip --archive=backups/signtalk-<timestamp>.gz)

npm run restore -- backups/signtalk-<timestamp>.gz            # dry run: prints what it would do
npm run restore -- backups/signtalk-<timestamp>.gz --yes      # merge: adds missing docs, keeps existing ones
npm run restore -- backups/signtalk-<timestamp>.gz --drop --yes  # replace: drops each collection first
```

`backups/` is git-ignored -- copy archives somewhere safe (e.g. Google
Drive). Atlas M0 has no built-in backups, so run `npm run backup` before
seeding, bulk edits, or a defense/demo. To rehearse a restore, point
`MONGODB_URI` at a scratch database for that one run.

## Offline behavior (Android app)

The backend is optional for the app's core features:

- The app is **offline-first**: the bundled 50-sign dictionary is seeded into
  Room on first launch, sign videos are bundled in the APK, and recognition
  runs on-device with TFLite. None of these need the API.
- **Dictionary sync is best-effort.** If the API (Render) or MongoDB is down,
  the sync fails quietly and the app keeps its local data.
- **Recognition logs are dropped silently when offline** (`POST
  /logs/recognition` is fire-and-forget; failures never affect recognition).
- **Submissions fail with a visible message** when the API is unreachable,
  and the sign stays saved locally as a personal entry.

## Testing

```
npm test
```

Runs Jest + Supertest with the Mongoose models mocked (`jest.mock(...)`, see
`test/setup.js`) and `AUTH_DEV_BYPASS=true`, so it needs no real database or
Firebase project. Role checks are tested by making the mocked dev user a
`user`, `validator`, or `admin`. Any test file that hits a write route must
also `jest.mock("../src/models/AuditLog")`.

## Project layout

```
src/
├── config/       env loading, MongoDB connection, Firebase Admin init, shared enums (constants.js)
├── models/       Mongoose schemas (User, DictionaryEntry, Submission, AuditLog, RecognitionLog, ModelVersion)
├── middleware/   auth (Firebase token verification, dev bypass, requireRole), validation rules, rate limits, error handling
├── controllers/  request handlers per resource
├── routes/       Express routers wiring controllers to paths
├── utils/        audit-log helper, slugify
├── scripts/      CLI scripts (seeding, model version registration, backup/restore)
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
- `POST /submissions` needs no login; send exactly the fields listed under
  "Submission lifecycle" (no extra keys such as a local Room `id`). Treat
  `409` as "already in the dictionary / already proposed" and show
  `conflict.label`; treat network errors and `429` as "keep it saved locally".
