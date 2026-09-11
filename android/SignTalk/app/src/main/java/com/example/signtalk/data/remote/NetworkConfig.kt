package com.example.signtalk.data.remote

/**
 * Points the app at the Node/Express backend in `backend/sign-talk-api`
 * (see proposal-notes.md's "Backend implementation" and
 * deployment-guide.md). BASE_URL is currently the live Render deployment
 * (https://signtalk-ag90.onrender.com/) -- confirmed responding correctly
 * with the current 50-class model via /api/health and /api/model/version.
 *
 * Falling back to local dev instead? Point BASE_URL at whichever of these
 * matches how you're testing:
 *   - Android emulator + `npm run dev` on the same PC: "http://10.0.2.2:4000/"
 *     (10.0.2.2 is the emulator's special alias for the host machine's own
 *     localhost).
 *   - A real phone + `npm run dev` on your PC:
 *     1. Find your PC's LAN IP (Windows: `ipconfig`, look for IPv4 Address).
 *     2. Use e.g. "http://192.168.1.23:4000/".
 *     3. Add that same IP as a <domain> entry in
 *        res/xml/network_security_config.xml (cleartext HTTP is only
 *        allowed for hosts explicitly listed there -- the Render URL above
 *        is HTTPS and doesn't need this).
 *     4. Make sure the phone and PC are on the same Wi-Fi network, and that
 *        Windows Firewall isn't blocking incoming connections to port 4000.
 */
object NetworkConfig {
    const val BASE_URL = "https://signtalk-ag90.onrender.com/"

    /**
     * Connection timeout, in seconds. Short is fine here -- Render's edge
     * accepts the TCP connection immediately even while the backend
     * instance behind it is asleep, so this almost never times out on its
     * own.
     */
    const val CONNECT_TIMEOUT_SECONDS = 10L

    /**
     * Read timeout, in seconds -- how long we wait for the HTTP response
     * body after connecting. This needs to be generous: Render's free tier
     * spins the backend down after ~15 minutes idle, and a "cold start" on
     * the next request can take 30-50+ seconds while the instance wakes
     * back up. The old single 10s timeout for everything was tuned for a
     * dev machine on localhost and was too short for that cold-start
     * window -- it's what caused Settings > About to get stuck on
     * "not yet synced" even though the backend and data were both fine,
     * because AppContainer.syncWithBackend() only ever runs once at app
     * startup and silently swallows a failed/timed-out sync (by design,
     * so a slow/offline backend never blocks the app) rather than retrying.
     */
    const val READ_TIMEOUT_SECONDS = 45L
}
