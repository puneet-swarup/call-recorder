package com.callrecorder.update

/**
 * Checks GitHub Releases for a newer version of the app.
 *
 * Deliberately dependency-free: the JSON response is scanned with small,
 * well-tested string helpers instead of pulling in org.json, so the class is
 * plain JVM code and runs unchanged in unit tests.
 */
class UpdateChecker(
    private val currentVersionCode: Int,
    private val fetcher: Fetcher,
    private val releasesUrl: String = DEFAULT_RELEASES_URL,
) {

    fun interface Fetcher {
        fun get(url: String): String
    }

    sealed class Result {
        data class UpToDate(val latestVersion: String) : Result()
        data class UpdateAvailable(
            val latestVersion: String,
            val apkUrl: String?,
            val sha256Url: String?,
            val releaseUrl: String,
        ) : Result()
        data class Failure(val reason: String) : Result()
    }

    fun check(): Result {
        val body = try {
            fetcher.get(releasesUrl)
        } catch (e: Exception) {
            return Result.Failure("Network error: " + (e.message ?: e.javaClass.simpleName))
        }

        if (body.isBlank()) return Result.Failure("Empty response from GitHub")

        val tag = extractString(body, "tag_name")
        if (tag.isNullOrBlank()) return Result.Failure("Release has no tag_name")

        val releaseUrl = extractString(body, "html_url") ?: RELEASES_PAGE
        val latestCode = parseVersionCode(tag)

        var apkUrl: String? = null
        var sha256Url: String? = null
        ASSET_NAME.findAll(body).forEach { match ->
            val name = match.groupValues[1]
            val start = match.range.last + 1
            val slice = body.substring(start, minOf(body.length, start + 400))
            val url = extractString(slice, "browser_download_url")
            if (url != null) {
                when {
                    name.endsWith(".apk", ignoreCase = true) -> apkUrl = url
                    name.endsWith(".sha256", ignoreCase = true) -> sha256Url = url
                }
            }
        }

        return if (latestCode <= currentVersionCode) {
            Result.UpToDate(tag.removePrefix("v"))
        } else {
            Result.UpdateAvailable(
                latestVersion = tag.removePrefix("v"),
                apkUrl = apkUrl,
                sha256Url = sha256Url,
                releaseUrl = releaseUrl,
            )
        }
    }

    companion object {
        const val DEFAULT_RELEASES_URL =
            "https://api.github.com/repos/puneet-swarup/call-recorder/releases/latest"
        const val RELEASES_PAGE =
            "https://github.com/puneet-swarup/call-recorder/releases"

        private val ASSET_NAME = Regex("""\"name\"\s*:\s*\"([^\"]+)\"""")

        /** Extracts the first `key: value` string for the given key. */
        fun extractString(json: String, key: String): String? {
            val marker = "\"" + key + "\""
            val keyIndex = json.indexOf(marker)
            if (keyIndex < 0) return null
            val colon = json.indexOf(':', keyIndex + marker.length)
            if (colon < 0) return null
            val firstQuote = json.indexOf('"', colon + 1)
            if (firstQuote < 0) return null
            val secondQuote = json.indexOf('"', firstQuote + 1)
            if (secondQuote < 0) return null
            return json.substring(firstQuote + 1, secondQuote)
        }

        /**
         * Derives a numeric version code from a tag like v1.2.0.
         * major10000 + minor*100 + patch, so 1.2.0 -> 10200.
         */
        fun parseVersionCode(tag: String): Int {
            val cleaned = tag.removePrefix("v").substringBefore('-')
            val parts = cleaned.split('.')
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            return major * 10_000 + minor * 100 + patch
        }
    }
}
