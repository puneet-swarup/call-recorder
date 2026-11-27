package com.callrecorder

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Launches the real activity on the JVM. This is the closest thing to a device
 * test available here: it catches missing views, theme attribute failures and
 * crashes during onCreate, which is where the last three bugs lived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityTest {

    @Test
    fun activityStartsAndBindsItsViews() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull("toolbar missing", activity.findViewById(R.id.toolbar))
        assertNotNull("storage line missing", activity.findViewById(R.id.storageText))
        assertNotNull("recordings list missing", activity.findViewById(R.id.recordingsContainer))
        assertNotNull("empty state missing", activity.findViewById(R.id.recordingsEmpty))
        assertNotNull("record button missing", activity.findViewById(R.id.fabRecord))
    }

    @Test
    fun toolbarMenuOffersTheExpectedActions() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val menu = androidx.appcompat.view.menu.MenuBuilder(activity)
        activity.onCreateOptionsMenu(menu)
        assertTrue("menu should have at least 5 entries", menu.size() >= 5)
        val titles = (0 until menu.size()).map { menu.getItem(it).title.toString() }
        assertTrue("settings missing: $titles", titles.any { it.contains("Settings") })
        assertTrue("update check missing: $titles", titles.any { it.contains("update", ignoreCase = true) })
    }

    @Test
    fun settingsScreenStarts() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertNotNull(activity.findViewById(R.id.formatGroup))
        assertNotNull(activity.findViewById(R.id.qualityGroup))
        assertNotNull(activity.findViewById(R.id.storagePath))
    }
}
