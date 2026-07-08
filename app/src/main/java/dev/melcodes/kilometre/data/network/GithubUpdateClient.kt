package dev.melcodes.kilometre.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// One release as reported by the public GitHub Releases API.
data class GithubRelease(
    val tagName: String,      // e.g. "v0.2.4"
    val name: String,         // release title, e.g. "0.2.4"
    val notes: String,        // release body (the notes shown in "What's new")
    val htmlUrl: String,      // the release page on github.com
    val apkUrl: String?,      // browser_download_url of the .apk asset, if present
    val versionCode: Int?,    // parsed from "versionCode N" in the notes
)

// Reads release info from the public GitHub repo so the app can show "what's
// new" and a manual update check without the user going through Obtainium.
//
// This is the app's only outbound network call, and it is gated behind an
// explicit user tap — Kilomètre is otherwise fully offline. The repo is public,
// so the unauthenticated Releases API is used (no token, no secret in the APK;
// 60 requests/hour per IP, far more than a manual check needs).
//
// Update ordering follows the project's own rule: compare `versionCode`, not the
// versionName, since a versionName can read "lower" than an older tag. Each
// release's notes carry a standalone "versionCode N" line by convention; that's
// what's parsed here. The match is anchored to a whole line (MULTILINE) so an
// inline prose mention like "(versionCode 41)" isn't picked up ahead of the real
// line. A release without the line is treated as having an unknown code and is
// never offered as an update.
object GithubUpdateClient {
    private const val RELEASES_URL =
        "https://api.github.com/repos/melcodesdev/kilometre/releases?per_page=20"
    private val VERSION_CODE_RE = Regex("""(?m)^\s*versionCode\s+(\d+)\s*$""")

    // Fetches the recent releases, newest first. Returns null on any failure
    // (no network, timeout, rate limit, parse error) — "couldn't check" is a
    // normal UI state, not an exception to propagate.
    suspend fun fetchReleases(): List<GithubRelease>? = withContext(Dispatchers.IO) {
        val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub rejects API requests without a User-Agent.
            setRequestProperty("User-Agent", "Kilometre-Android")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } catch (e: IOException) {
            // Offline, DNS failure, or timeout — surfaced as a null result so the
            // caller shows "couldn't check for updates" rather than crashing.
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String): List<GithubRelease> {
        val arr = JSONArray(json)
        val out = ArrayList<GithubRelease>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optBoolean("draft", false)) continue
            val notes = o.optString("body", "")
            var apkUrl: String? = null
            o.optJSONArray("assets")?.let { assets ->
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name").endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
            }
            out.add(
                GithubRelease(
                    tagName = o.optString("tag_name"),
                    name = o.optString("name").ifBlank { o.optString("tag_name") },
                    notes = notes,
                    htmlUrl = o.optString("html_url"),
                    apkUrl = apkUrl,
                    versionCode = VERSION_CODE_RE.find(notes)?.groupValues?.get(1)?.toIntOrNull(),
                ),
            )
        }
        return out
    }
}
