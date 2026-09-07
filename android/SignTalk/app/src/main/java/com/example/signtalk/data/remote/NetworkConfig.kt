package com.example.signtalk.data.remote

/**
 * Points the app at the Node/Express backend in `backend/sign-talk-api`
 * (see proposal-notes.md's "Backend implementation" section). Only used for
 * local development right now -- there is no deployed instance yet.
 *
 * 10.0.2.2 is the Android emulator's special alias for the host machine's
 * own localhost, so this default works out of the box when running the app
 * in an emulator alongside `npm run dev` on the same PC.
 *
 * Testing on a REAL phone instead? "localhost"/10.0.2.2 refers to the phone
 * itself from there, not your PC, so:
 *   1. Find your PC's LAN IP (Windows: `ipconfig`, look for IPv4 Address).
 *   2. Replace BASE_URL below with e.g. "http://192.168.1.23:4000/".
 *   3. Add that same IP as a <domain> entry in
 *      res/xml/network_security_config.xml (cleartext HTTP is only allowed
 *      for hosts explicitly listed there).
 *   4. Make sure the phone and PC are on the same Wi-Fi network, and that
 *      Windows Firewall isn't blocking incoming connections to port 4000.
 */
object NetworkConfig {
    const val BASE_URL = "http://10.0.2.2:4000/"

    /** Network timeouts, in seconds -- generous since this hits a dev machine, not a CDN. */
    const val TIMEOUT_SECONDS = 10L
}
