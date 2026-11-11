package com.hidsquid.refreshpaper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForegroundAppTrackerTest {

    private val self = "com.hidsquid.refreshpaper"
    private val ridi = "com.ridi.books.viewer"
    private val other = "com.example.reader"
    private val systemUi = "com.android.systemui"
    private val statusBarActivity = "com.hidsquid.refreshpaper.StatusBarSettingsActivity"
    private val brightnessActivity = "com.hidsquid.refreshpaper.brightness.BrightnessActivity"
    private val mainActivity = "com.hidsquid.refreshpaper.MainActivity"
    private val nonActivityClass = "android.widget.FrameLayout"

    private lateinit var tracker: ForegroundAppTracker

    @Before
    fun setUp() {
        tracker = ForegroundAppTracker(self)
    }

    @Test
    fun `user app window becomes current and effective package`() {
        tracker.onWindowStateChanged(ridi)
        assertEquals(ridi, tracker.getCurrentUserAppPackageName())
        assertEquals(ridi, tracker.getEffectivePackageName())
    }

    @Test
    fun `initial state is blank`() {
        assertEquals("", tracker.getCurrentUserAppPackageName())
        assertEquals("", tracker.getEffectivePackageName())
    }

    @Test
    fun `system packages are not user apps but effective package keeps last user app`() {
        tracker.onWindowStateChanged(ridi)
        for (pkg in listOf("android", systemUi, self)) {
            tracker.onWindowStateChanged(pkg)
            assertEquals("current for $pkg", "", tracker.getCurrentUserAppPackageName())
            assertEquals("effective for $pkg", ridi, tracker.getEffectivePackageName())
        }
    }

    @Test
    fun `system package before any user app leaves effective package blank`() {
        tracker.onWindowStateChanged(systemUi)
        assertEquals("", tracker.getEffectivePackageName())
    }

    @Test
    fun `switching to another user app updates effective package`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onWindowStateChanged(other)
        assertEquals(other, tracker.getEffectivePackageName())
    }

    @Test
    fun `overlay activity open then close restores package before overlay`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onWindowStateChanged(self, statusBarActivity)
        assertEquals("", tracker.getCurrentUserAppPackageName())
        assertEquals(ridi, tracker.getEffectivePackageName())

        tracker.onOverlayClosed()
        assertEquals(ridi, tracker.getCurrentUserAppPackageName())
    }

    @Test
    fun `brightness activity is also treated as overlay`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onWindowStateChanged(self, brightnessActivity)
        tracker.onOverlayClosed()
        assertEquals(ridi, tracker.getCurrentUserAppPackageName())
    }

    @Test
    fun `non-activity window from own package keeps overlay state`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onWindowStateChanged(self, statusBarActivity)
        tracker.onWindowStateChanged(self, nonActivityClass)
        // overlay still open, so closing restores the package before it
        tracker.onOverlayClosed()
        assertEquals(ridi, tracker.getCurrentUserAppPackageName())
    }

    @Test
    fun `unknown or blank class name from own package keeps overlay state`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onWindowStateChanged(self, statusBarActivity)
        tracker.onWindowStateChanged(self, "com.nonexistent.Dialog")
        tracker.onWindowStateChanged(self, "")
        tracker.onOverlayClosed()
        assertEquals(ridi, tracker.getCurrentUserAppPackageName())
    }

    @Test
    fun `non-overlay activity from own package clears overlay state`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onWindowStateChanged(self, statusBarActivity)
        tracker.onWindowStateChanged(self, mainActivity)
        tracker.onOverlayClosed()
        // overlay state was cleared, so nothing is restored; current stays own package
        assertEquals("", tracker.getCurrentUserAppPackageName())
        assertEquals(ridi, tracker.getEffectivePackageName())
    }

    @Test
    fun `different app window clears overlay state`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onWindowStateChanged(self, statusBarActivity)
        tracker.onWindowStateChanged(other)
        tracker.onWindowStateChanged(self, nonActivityClass)
        tracker.onOverlayClosed()
        // overlay no longer open: onOverlayClosed does not restore the old package
        assertEquals("", tracker.getCurrentUserAppPackageName())
        assertEquals(other, tracker.getEffectivePackageName())
    }

    @Test
    fun `onOverlayClosed without overlay does nothing`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onOverlayClosed()
        assertEquals(ridi, tracker.getCurrentUserAppPackageName())
    }

    @Test
    fun `repeated overlay events do not overwrite package before overlay`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onWindowStateChanged(self, statusBarActivity)
        tracker.onWindowStateChanged(self, brightnessActivity)
        tracker.onOverlayClosed()
        assertEquals(ridi, tracker.getCurrentUserAppPackageName())
    }

    @Test
    fun `overlay class name from another package is not an overlay`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onWindowStateChanged(other, statusBarActivity)
        assertEquals(other, tracker.getCurrentUserAppPackageName())
    }

    @Test
    fun `isBlocked is true when nothing has been seen`() {
        assertTrue(tracker.isBlocked(ridi))
    }

    @Test
    fun `isBlocked is true for blocked package and false otherwise`() {
        tracker.onWindowStateChanged(ridi)
        assertTrue(tracker.isBlocked(ridi))
        assertFalse(tracker.isBlocked(other))
        tracker.onWindowStateChanged(other)
        assertFalse(tracker.isBlocked(ridi))
    }

    @Test
    fun `blocking follows effective package across system dialogs`() {
        tracker.onWindowStateChanged(ridi)
        tracker.onWindowStateChanged(systemUi)
        assertTrue(tracker.isBlocked(ridi))
        tracker.onWindowStateChanged(self, statusBarActivity)
        assertTrue(tracker.isBlocked(ridi))
    }

    @Test
    fun `system window before any user app is blocked`() {
        tracker.onWindowStateChanged(systemUi)
        assertTrue(tracker.isBlocked(ridi))
    }
}
