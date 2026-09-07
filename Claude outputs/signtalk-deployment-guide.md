# Deploying SignTalk (backend + Android app)

Your computer isn't reachable from this session right now (the connection
dropped), so I couldn't re-check the exact files live before writing this.
Everything below is based on what's documented in the project notes from
building the backend and Android integration — I've flagged the couple of
spots worth double-checking against the actual code once we're back in
your files.

"Deploying, end-to-end" means two separate things that need to happen:

1. Get `backend/sign-talk-api` running on a real server with a real URL
   (instead of only on your machine via `docker compose up`).
2. Point the Android app at that real URL instead of `10.0.2.2:4000`
   (the emulator's alias for your own PC), then rebuild and share the app.

## Part 1 — Deploy the backend

### 1. Pick a host + database

For a capstone project, the simplest combination is:

- **Render** (render.com) for the API itself — free/hobby web service tier,
  deploys straight from a GitHub repo, gives you HTTPS automatically.
  **Railway** (railway.app) is a solid alternative if you'd rather use
  that.
- **MongoDB Atlas** (mongodb.com/atlas) for the database — free M0 tier,
  fully managed, so you're not running MongoDB in a container on a server
  yourself. This replaces the `docker-compose.yml` Mongo container you've
  been using locally — that one stays for local dev only.

### 2. Get your code on GitHub

Render/Railway both deploy from a GitHub repo. Your project already has a
git repo locally (`SignTalk/.git`) — if it isn't pushed to GitHub yet:

```
cd path\to\SignTalk
git remote add origin https://github.com/<you>/signtalk.git
git push -u origin main
```

(If it's already on GitHub, skip this.)

### 3. Create the MongoDB Atlas cluster

1. Sign up at mongodb.com/atlas, create a free (M0) cluster.
2. Create a database user (username/password) under Database Access.
3. Under Network Access, add `0.0.0.0/0` (allow from anywhere) — simplest
   for a student project; Render's outbound IPs aren't fixed on the free
   tier, so restricting by IP isn't practical here.
4. Get the connection string (Atlas gives you a `mongodb+srv://...` URI) —
   this becomes your `MONGODB_URI` env var.

### 4. Create the Render web service

1. New → Web Service → connect your GitHub repo, set the **root directory**
   to `backend/sign-talk-api` (since the API isn't at the repo root).
2. Build command: `npm install`. Start command: `npm start` (check
   `package.json`'s `scripts.start` — if it's not called `start`, use
   whatever the actual script name is).
3. Add environment variables — open `backend/sign-talk-api/.env.example`
   and set a real value for each one on Render's Environment tab. At
   minimum you'll have `MONGODB_URI` (from step 3) and `PORT` (Render sets
   its own `PORT` automatically — Express should read `process.env.PORT`,
   which `.env.example` should already reflect).

### 5. Decide what to do about `AUTH_DEV_BYPASS`

This is the one real decision point, not just a mechanical step. Right
now the API runs with `AUTH_DEV_BYPASS=true` (no real Firebase project
exists yet), and — by design — **the server refuses to start with that
flag on if `NODE_ENV=production`**. So you have two honest options for a
deployed instance:

- **Demo/defense deployment (recommended for now):** don't set
  `NODE_ENV=production` on Render (leave it unset, or `development`).
  The safety guard only trips on `production`, so the bypass keeps
  working and every request is treated as the same fixed dev user — fine
  for demoing the dictionary, recognition logging, etc., but it means
  there's no real per-user auth on the deployed API. Say this plainly if
  asked during a defense: it's a known, deliberate simplification, not an
  oversight (the proposal notes already call this out).
- **Real deployment:** create an actual Firebase project, generate a
  service-account JSON, wire it into `firebase-admin`'s init (env var or
  secret file on Render), turn `AUTH_DEV_BYPASS` off, and set
  `NODE_ENV=production`. This is more work and needs the Android login
  screen built too (per the proposal notes, that was deliberately not
  built yet). Worth doing before any real public release, not necessarily
  before a capstone demo.

**Worth verifying once we're back in your files:** whether
`firebase-admin` initialization itself requires *some* credential env var
to even boot, even with the bypass on — if so, Render will crash-loop on
missing env vars rather than just skipping auth. I'd want to check
`src/` (wherever the Firebase Admin init lives) before you flip the
switch, so you're not debugging a blind deploy.

### 6. Seed the deployed database once

Once the service is live and `MONGODB_URI` points at Atlas, run the seed
scripts once — either as a Render "one-off job" if the plan supports it,
or from your own machine by temporarily pointing your local `.env`'s
`MONGODB_URI` at the same Atlas cluster and running:

```
npm run seed:dictionary
npm run seed:model-version
```

### 7. Verify it's actually live

```
curl https://<your-service>.onrender.com/api/health
curl https://<your-service>.onrender.com/api/dictionary
```

You should get back the health check and all 50 real dictionary entries,
same as your local live test already confirmed against Docker Mongo.

## Part 2 — Point the Android app at the live backend

1. In `NetworkConfig.kt` (`android/SignTalk/app/.../data/remote/`), change
   the base URL from `http://10.0.2.2:4000/` to your real Render URL,
   e.g. `https://signtalk-api.onrender.com/`.
2. Since Render's URL is HTTPS, you no longer need the cleartext exception
   in `res/xml/network_security_config.xml` for this to work — that file
   can stay as-is (it's harmless, just unused for this host) or you can
   trim the `10.0.2.2`/`localhost` entries if you want the config to only
   describe production. Not required either way.
3. Rebuild the app. `SignTalkApp.onCreate()` already calls
   `AppContainer.syncWithBackend()` best-effort at startup — Settings >
   About should show the real synced model line once it reaches the live
   API instead of "not yet synced".
4. If you still want to test against your own machine's local backend
   from a real phone (not the emulator) on the same Wi-Fi, that's a
   separate, third base URL (your PC's LAN IP, e.g. `http://192.168.x.x:4000/`)
   — different from both `10.0.2.2` (emulator-only) and the Render URL.
   Not needed once the backend is actually deployed, just flagging it
   since it's a common point of confusion.

## Part 3 — Get the Android app onto a phone

Two very different levels of "deploy" here — worth picking based on what
you actually need:

**For a capstone demo/defense (recommended):** build a signed release APK
in Android Studio (`Build > Generate Signed Bundle / APK`, choose APK, use
or create a keystore) and share the `.apk` file directly — Google Drive
link, email, USB transfer, whatever's easiest. Anyone installing it just
needs to allow "install from unknown sources" once. No Play Console
account, no review process, works today.

**For real public distribution:** publish to Google Play. This needs a
one-time $25 Google Play Console developer account, a privacy policy URL,
content rating questionnaire, screenshots/store listing, and (Google now
requires) a closed testing track with a minimum number of testers before
you can go to production. This is real overhead — worth doing only if the
goal is genuinely putting this in front of the public, not just a defense
demo.

## Summary checklist

- [ ] Push `SignTalk` to GitHub (if not already)
- [ ] Create MongoDB Atlas cluster + get connection string
- [ ] Create Render web service pointed at `backend/sign-talk-api`, set env vars
- [ ] Decide on `AUTH_DEV_BYPASS` (demo mode vs. real Firebase)
- [ ] Run `npm run seed:dictionary` / `seed:model-version` against the deployed DB
- [ ] Verify `/api/health` and `/api/dictionary` respond
- [ ] Update `NetworkConfig.kt`'s base URL to the Render URL
- [ ] Rebuild the app, confirm Settings > About syncs
- [ ] Build a signed release APK and share it (or go the Play Store route)

Want me to start on any of these once your computer's reconnected — e.g.
checking the Firebase-admin init before you touch `AUTH_DEV_BYPASS`, or
editing `NetworkConfig.kt` once you've got the Render URL?
