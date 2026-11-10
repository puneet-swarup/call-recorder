package com.callrecorder.update

import org.junit.Assert
import org.junit.Test

class UpdateCheckerTest {

    private fun checker(current: Int, body: String = "", error: Exception? = null): UpdateChecker {
        val fetcher = UpdateChecker.Fetcher { _: String ->
            if (error != null) throw error
            body
        }
        return UpdateChecker(current, fetcher)
    }

    private val releaseJson =
        "{\"tag_name\":\"v1.2.0\",\"html_url\":\"https://example.test/rel\"," +
        "\"assets\":[{\"name\":\"app-release.apk\"," +
        "\"browser_download_url\":\"https://example.test/app-release.apk\"}," +
        "{\"name\":\"app-release.apk.sha256\"," +
        "\"browser_download_url\":\"https://example.test/app-release.apk.sha256\"}]}"

    @Test
    fun newerVersionReturnsUpdateAvailable() {
        val result = checker(current = 10100, body = releaseJson).check()
        Assert.assertTrue(result is UpdateChecker.Result.UpdateAvailable)
        val update = result as UpdateChecker.Result.UpdateAvailable
        Assert.assertEquals("1.2.0", update.latestVersion)
        Assert.assertEquals("https://example.test/app-release.apk", update.apkUrl)
        Assert.assertEquals("https://example.test/app-release.apk.sha256", update.sha256Url)
    }

    @Test
    fun sameVersionReturnsUpToDate() {
        val result = checker(current = 10200, body = releaseJson).check()
        Assert.assertTrue(result is UpdateChecker.Result.UpToDate)
        Assert.assertEquals("1.2.0", (result as UpdateChecker.Result.UpToDate).latestVersion)
    }

    @Test
    fun olderReleaseReturnsUpToDate() {
        val result = checker(current = 20000, body = releaseJson).check()
        Assert.assertTrue(result is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun missingAssetsStillReportsUpdate() {
        val body = "{\"tag_name\":\"v1.5.0\",\"html_url\":\"https://example.test/rel\"}"
        val result = checker(current = 10200, body = body).check()
        Assert.assertTrue(result is UpdateChecker.Result.UpdateAvailable)
        val update = result as UpdateChecker.Result.UpdateAvailable
        Assert.assertNull(update.apkUrl)
        Assert.assertNull(update.sha256Url)
    }

    @Test
    fun malformedJsonReturnsFailure() {
        val result = checker(current = 1, body = "not json at all").check()
        Assert.assertTrue(result is UpdateChecker.Result.Failure)
    }

    @Test
    fun emptyBodyReturnsFailure() {
        val result = checker(current = 1, body = "   ").check()
        Assert.assertTrue(result is UpdateChecker.Result.Failure)
    }

    @Test
    fun networkErrorReturnsFailure() {
        val result = checker(current = 1, error = RuntimeException("boom")).check()
        Assert.assertTrue(result is UpdateChecker.Result.Failure)
        Assert.assertTrue((result as UpdateChecker.Result.Failure).reason.contains("Network error"))
    }

    @Test
    fun parseVersionCodeHandlesVariousTags() {
        Assert.assertEquals(10200, UpdateChecker.parseVersionCode("v1.2.0"))
        Assert.assertEquals(10200, UpdateChecker.parseVersionCode("1.2.0"))
        Assert.assertEquals(10200, UpdateChecker.parseVersionCode("v1.2.0-beta"))
        Assert.assertEquals(10000, UpdateChecker.parseVersionCode("v1"))
        Assert.assertEquals(0, UpdateChecker.parseVersionCode("garbage"))
    }

    @Test
    fun extractStringFindsFirstMatch() {
        val json = "{\"a\":\"one\",\"b\":\"two\",\"a\":\"three\"}"
        Assert.assertEquals("one", UpdateChecker.extractString(json, "a"))
        Assert.assertEquals("two", UpdateChecker.extractString(json, "b"))
        Assert.assertNull(UpdateChecker.extractString(json, "missing"))
    }
}
