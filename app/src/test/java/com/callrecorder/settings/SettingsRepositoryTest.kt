package com.callrecorder.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsRepositoryTest {

    private fun repo(): SettingsRepository {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        return SettingsRepository(ctx)
    }

    @Test
    fun recorderEnabledDefaultsToFalse() {
        Assert.assertFalse(repo().isRecorderEnabled())
    }

    @Test
    fun setRecorderEnabledPersistsAcrossInstances() {
        val first = repo()
        first.setRecorderEnabled(true)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val second = SettingsRepository(ctx)
        Assert.assertTrue(second.isRecorderEnabled())
    }

    @Test
    fun shareAndMinimiseFieldsRoundTrip() {
        val repo = repo()
        repo.save(repo.load().copy(shareRecipient = "a@b.com", autoShareOnComplete = true, minimiseMode = true))
        val loaded = repo.load()
        Assert.assertEquals("a@b.com", loaded.shareRecipient)
        Assert.assertTrue(loaded.autoShareOnComplete)
        Assert.assertTrue(loaded.minimiseMode)
    }
}
