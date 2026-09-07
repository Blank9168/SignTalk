# Deploying SignTalk — exact step by step

Verified live against your actual files (`backend/sign-talk-api/package.json`,
`.env.example`, `src/config/env.js`, `src/config/firebase.js`,
`NetworkConfig.kt`, `network_security_config.xml`) and your git remote —
this replaces the earlier draft with the exact commands/values for your repo.

Your repo: **https://github.com/Blank9168/SignTalk.git**, branch **master**.

## Part 1 — Deploy the backend to Render + MongoDB Atlas

### Step 1 — Push your latest changes to GitHub

Your `git status` currently shows uncommitted changes (looks like a bunch
of deleted `.npy` files under `ai/dataset/raw/`, probably from a cleanup).
Commit or stash those first so Render deploys what you expect:

```
cd path\to\SignTalk
git add -A
git commit -m "Prep for deployment"
git push origin master
```

### Step 2 — Create a MongoDB Atlas cluster

1. Go to mongodb.com/atlas → sign up → **Create a free (M0) cluster**.
2. **Database Access** → add a database user (username + password — save these).
3. **Network Access** → **Add IP Address** → **Allow access from anywhere**
   (`0.0.0.0/0`). Render's outbound IP isn't fixed on the free tier, so this
   is the practical option for a student project.
4. **Connect** → **Drivers** → copy the connection string. It looks like:
   ```
   mongodb+srv://<username>:<password>@<cluster>.mongodb.net/signtalk?retryWrites=true&w=majority
   ```
   Replace `<username>`/`<password>` with the user you created, and make
   sure the database name is `signtalk` (add `/signtalk` before the `?` if
   Atlas didn't include it). This full string is your `MONGODB_URI`.

### Step 3 — Create the Render web service

1. render.com → sign up (GitHub login is easiest) → **New** → **Web Service**.
2. Connect the `Blank9168/SignTalk` repo, branch `master`.
3. **Root Directory**: `backend/sign-talk-api` (the API isn't at the repo root).
4. **Runtime**: Node.
5. **Build Command**: `npm install`
6. **Start Command**: `npm start` (confirmed — `package.json` maps this to
   `node src/server.js`).
7. **Instance Type**: Free.
8. Under **Environment Variables**, add these (values from your real
   `.env.example`, confirmed against `src/config/env.js`):

   | Key | Value |
   |---|---|
   | `MONGODB_URI` | the Atlas connection string from Step 2 |
   | `NODE_ENV` | leave unset, or set to `development` — see Step 4, do **not** set `production` yet |
   | `AUTH_DEV_BYPASS` | `true` |
   | `ADMIN_EMAILS` | your email(s), comma-separated (optional — grants admin role on first sync) |

   Leave `FIREBASE_PROJECT_ID` / `FIREBASE_CLIENT_EMAIL` / `FIREBASE_PRIVATE_KEY`
   unset for now — **confirmed safe**: `src/config/firebase.js` only
   initializes Firebase Admin lazily, and explicitly skips it with just a
   console warning when those are missing and `AUTH_DEV_BYPASS=true`. It
   will not crash on boot. Don't set `PORT` — Render injects its own and
   `src/config/env.js` already reads `process.env.PORT`.

9. Click **Create Web Service**. Watch the deploy log; once you see
   `[server] SignTalk API listening on port ...`, it's live.

### Step 4 — About `AUTH_DEV_BYPASS` (the one real decision)

`src/config/env.js` **throws an error and refuses to boot** if
`AUTH_DEV_BYPASS=true` and `NODE_ENV=production`, so:

- **For your capstone demo (do this now):** leave `NODE_ENV` unset on
  Render (defaults to `development` in the code). Bypass stays on, every
  request is treated as one fixed dev user, no real per-user auth — fine
  for demoing the dictionary/recognition-logging/model-version endpoints.
- **Later, for a real deployment:** create a Firebase project, download
  the service-account JSON (Firebase Console → Project Settings → Service
  Accounts → Generate new private key), set `FIREBASE_PROJECT_ID`,
  `FIREBASE_CLIENT_EMAIL`, and `FIREBASE_PRIVATE_KEY` (keep the `\n`
  sequences literal, per the comment in `.env.example`) on Render, set
  `AUTH_DEV_BYPASS=false`, then `NODE_ENV=production`. This also needs a
  real Android login screen, which doesn't exist yet (deliberately, per
  the proposal notes) — a bigger follow-on piece of work, not needed for
  a demo.

### Step 5 — Seed the deployed database

Your Render URL will look like `https://sign-talk-api.onrender.com` (exact
name depends on what you called the service). Seed it once from your own
machine, pointed temporarily at the same Atlas cluster:

```
cd backend\sign-talk-api
set MONGODB_URI=<paste the same Atlas connection string from Step 2>
npm run seed:dictionary
npm run seed:model-version
```

(`set` is the Windows cmd syntax for a one-off env var for that command
session; use `$env:MONGODB_URI="..."` if you're in PowerShell instead.)

### Step 6 — Verify it's live

```
curl https://<your-render-url>/api/health
curl https://<your-render-url>/api/dictionary
```

You should get the health check back, then all 50 real dictionary entries.

## Part 2 — Point the Android app at the live backend

1. Open `android/SignTalk/app/src/main/java/com/example/signtalk/data/remote/NetworkConfig.kt`.
   Line 23 currently reads:
   ```kotlin
   const val BASE_URL = "http://10.0.2.2:4000/"
   ```
   Change it to your real Render URL, with a trailing slash:
   ```kotlin
   const val BASE_URL = "https://<your-render-url>/"
   ```
2. No change needed to `res/xml/network_security_config.xml` — that file
   only allows plain HTTP for `10.0.2.2`/`localhost`, and Render is HTTPS,
   which Android allows by default. You can leave that file as-is.
3. Rebuild and run the app. `SignTalkApp.onCreate()` already calls
   `AppContainer.syncWithBackend()` on startup — check **Settings > About**;
   it should show a real synced-model line instead of "not yet synced"
   once it reaches the live API.

## Part 3 — Get the app onto a phone

**For your capstone demo (recommended):** Android Studio →
**Build > Generate Signed Bundle / APK** → **APK** → create/use a keystore
→ build the release APK → share the `.apk` file directly (Drive link,
email, USB). Anyone installing it just allows "install from unknown
sources" once. No account, no review, works today.

**For real public release:** Google Play Console ($25 one-time), privacy
policy URL, content rating, store listing, and a closed-testing track
before production is allowed. Only worth it if this is going out
publicly, not for a defense demo.

## Checklist

- [ ] Commit + push latest changes to `github.com/Blank9168/SignTalk` (branch `master`)
- [ ] Create MongoDB Atlas M0 cluster, allow `0.0.0.0/0`, get connection string
- [ ] Create Render web service: root dir `backend/sign-talk-api`, build `npm install`, start `npm start`
- [ ] Set `MONGODB_URI`, `AUTH_DEV_BYPASS=true`, leave `NODE_ENV` unset, leave Firebase vars unset
- [ ] Confirm the Render deploy log shows the server listening
- [ ] Run `seed:dictionary` and `seed:model-version` against the Atlas DB
- [ ] Curl `/api/health` and `/api/dictionary` to confirm it's live
- [ ] Update `NetworkConfig.kt` line 23 to the Render URL
- [ ] Rebuild, confirm Settings > About shows a synced model
- [ ] Build a signed release APK and share it
