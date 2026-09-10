package io.ronesec.android.platform.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import io.ronesec.domain.protection.ProtectionEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T09: Foreground detection normalization, adaptive service subscription, and bounded EventIngress.
 * F24, F25, Section 2.4, Section 3.1-3.2, Section 11.2 (T09).
 */
class T09_ForegroundIngressAndSubscriptionTest {

    // --- SubscriptionController tests ---

    @Test
    fun subscriptionController_idleAppliesEnabledTargets_with25msTimeout() {
        var appliedInfo: AccessibilityServiceInfo? = null
        var setCallCount = 0

        val fakeAdapter = object : AccessibilityServiceConfigAdapter {
            override fun getServiceInfo(): AccessibilityServiceInfo = AccessibilityServiceInfo()
            override fun setServiceInfo(info: AccessibilityServiceInfo) {
                appliedInfo = info
                setCallCount++
            }
        }

        val controller = SubscriptionController(fakeAdapter)
        val targets = setOf("com.example.app1", "com.example.app2")

        val changed = controller.applyConfiguration(targets, trackAll = false)
        assertTrue(changed)
        assertEquals(1, setCallCount)
        assertNotNull(appliedInfo)
        assertEquals(25L, appliedInfo!!.notificationTimeout)
        assertTrue(appliedInfo!!.packageNames.contentEquals(arrayOf("com.example.app1", "com.example.app2")))

        // Idempotent re-apply avoids repeated IPC
        val changedAgain = controller.applyConfiguration(targets, trackAll = false)
        assertFalse(changedAgain)
        assertEquals(1, setCallCount)
    }

    @Test
    fun subscriptionController_zeroTargets_usesSafeDummyPackageInsteadOfNull() {
        var appliedInfo: AccessibilityServiceInfo? = null

        val fakeAdapter = object : AccessibilityServiceConfigAdapter {
            override fun getServiceInfo(): AccessibilityServiceInfo = AccessibilityServiceInfo()
            override fun setServiceInfo(info: AccessibilityServiceInfo) {
                appliedInfo = info
            }
        }

        val controller = SubscriptionController(fakeAdapter)
        controller.applyConfiguration(emptySet(), trackAll = false)

        assertNotNull(appliedInfo)
        assertNotNull("packageNames must NOT be null (which would listen to all packages)", appliedInfo!!.packageNames)
        assertEquals(1, appliedInfo!!.packageNames.size)
        assertEquals(SubscriptionController.DUMMY_NO_TARGETS_PACKAGE, appliedInfo!!.packageNames[0])
    }

    @Test
    fun subscriptionController_trackAll_widensToAllPackages() {
        var appliedInfo: AccessibilityServiceInfo? = null

        val fakeAdapter = object : AccessibilityServiceConfigAdapter {
            override fun getServiceInfo(): AccessibilityServiceInfo = AccessibilityServiceInfo()
            override fun setServiceInfo(info: AccessibilityServiceInfo) {
                appliedInfo = info
            }
        }

        val controller = SubscriptionController(fakeAdapter)
        controller.applyConfiguration(setOf("com.example.app"), trackAll = true)

        assertNotNull(appliedInfo)
        assertNull("packageNames must be null to listen to all packages during tracking", appliedInfo!!.packageNames)
    }

    // --- ForegroundTracker tests ---

    @Test
    fun foregroundTracker_filtersNoiseAndNormalizesAuthorizedEvents() {
        val tracker = ForegroundTracker(ownPackageName = "io.ronesec.android")

        // 1. Non-TYPE_WINDOW_STATE_CHANGED ignored
        val nonWindowState = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            packageName = "com.example.app",
            uptimeMs = 1000L
        )
        assertNull(tracker.normalizeEvent(nonWindowState))

        // 2. Null or blank package ignored
        val nullPkg = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = null,
            uptimeMs = 1001L
        )
        assertNull(tracker.normalizeEvent(nullPkg))

        // 3. IME package ignored
        val imeEvent = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "com.google.android.inputmethod.latin",
            uptimeMs = 1002L
        )
        assertNull(tracker.normalizeEvent(imeEvent))

        // 4. SystemUI ignored
        val sysUiEvent = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "com.android.systemui",
            uptimeMs = 1003L
        )
        assertNull(tracker.normalizeEvent(sysUiEvent))

        // 5. Own overlay window ignored
        val ownOverlay = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "io.ronesec.android",
            uptimeMs = 1004L,
            isOverlayWindow = true
        )
        assertNull(tracker.normalizeEvent(ownOverlay))

        // The live service cannot reliably label an application overlay by window type.
        // Any own-package window other than the real MainActivity must remain noise.
        val ownUnlabelledOverlay = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "io.ronesec.android",
            className = "android.widget.FrameLayout",
            uptimeMs = 1005L,
            isOverlayWindow = false
        )
        assertNull(tracker.normalizeEvent(ownUnlabelledOverlay))

        // 6. Own MainActivity is recognized as departure
        val ownMainActivity = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "io.ronesec.android",
            className = "io.ronesec.android.ui.MainActivity",
            uptimeMs = 1006L,
            isOverlayWindow = false
        )
        val mainResult = tracker.normalizeEvent(ownMainActivity)
        assertNotNull(mainResult)
        assertTrue(mainResult is ProtectionEvent.ForegroundCandidate)
        assertEquals("io.ronesec.android", (mainResult as ProtectionEvent.ForegroundCandidate).packageName)

        // 7. Legitimate target application normalized
        val targetEvent = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "com.example.target",
            uptimeMs = 1007L
        )
        val targetResult = tracker.normalizeEvent(targetEvent)
        assertNotNull(targetResult)
        assertEquals("com.example.target", (targetResult as ProtectionEvent.ForegroundCandidate).packageName)

        // 8. Stale timestamp rejected
        val staleEvent = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "com.example.target2",
            uptimeMs = 1000L // earlier than 1007L
        )
        assertNull(tracker.normalizeEvent(staleEvent))
    }

    @Test
    fun foregroundTracker_filtersAndroidFrameworkAndDynamicImeAndPopups() {
        val dynamicImes = mutableSetOf("ru.yandex.keyboard", "com.facemoji.lite.xiaomi")
        val tracker = ForegroundTracker(
            ownPackageName = "io.ronesec.android",
            imePackageProvider = { dynamicImes }
        )

        // 1. Android framework package ("android") ignored
        val androidEvent = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "android",
            uptimeMs = 2000L
        )
        assertNull("Android framework package must be filtered as noise", tracker.normalizeEvent(androidEvent))

        // 2. Dynamic IME package (e.g. Yandex Keyboard) ignored
        val yandexImeEvent = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "ru.yandex.keyboard",
            uptimeMs = 2001L
        )
        assertNull("Dynamic IME package must be filtered", tracker.normalizeEvent(yandexImeEvent))

        // 3. IME window semantics ignored regardless of unknown package
        val unknownImeWindow = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "com.unknown.keyboard",
            uptimeMs = 2002L,
            isImeWindow = true
        )
        assertNull("Window with IME type must be filtered", tracker.normalizeEvent(unknownImeWindow))

        // 4. System window semantics ignored
        val systemWindow = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "com.oem.overlay",
            uptimeMs = 2003L,
            isSystemWindow = true
        )
        assertNull("System window must be filtered", tracker.normalizeEvent(systemWindow))

        // 5. Popup window class ignored
        val popupEvent = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "org.telegram.messenger",
            className = "android.widget.PopupWindow",
            uptimeMs = 2004L
        )
        assertNull("PopupWindow class must be filtered", tracker.normalizeEvent(popupEvent))

        // 6. Sub-window / attached window ignored
        val subWindowEvent = RawAccessibilityPayload(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = "org.telegram.messenger",
            uptimeMs = 2005L,
            isSubWindow = true
        )
        assertNull("Sub-window must be filtered", tracker.normalizeEvent(subWindowEvent))
    }

    // --- EventIngress tests ---

    @Test
    fun eventIngress_coalescesConsecutiveIdenticalCandidates_preservesTransitions() = runBlocking {
        val ingress = EventIngress(foregroundCapacity = 4)

        // Consecutive identical candidates are coalesced
        ingress.sendForegroundCandidate(ProtectionEvent.ForegroundCandidate("com.app.A", 1000L, 1L))
        ingress.sendForegroundCandidate(ProtectionEvent.ForegroundCandidate("com.app.A", 1050L, 2L))

        // Different app -> transition preserved
        ingress.sendForegroundCandidate(ProtectionEvent.ForegroundCandidate("com.app.B", 1100L, 3L))

        // Back to A -> A -> B -> A preserved
        ingress.sendForegroundCandidate(ProtectionEvent.ForegroundCandidate("com.app.A", 1150L, 4L))

        val e1 = ingress.pollNextEvent() as ProtectionEvent.ForegroundCandidate
        assertEquals("com.app.A", e1.packageName)
        assertEquals(1050L, e1.sourceUptimeMs) // coalesced latest timestamp

        val e2 = ingress.pollNextEvent() as ProtectionEvent.ForegroundCandidate
        assertEquals("com.app.B", e2.packageName)

        val e3 = ingress.pollNextEvent() as ProtectionEvent.ForegroundCandidate
        assertEquals("com.app.A", e3.packageName)

        assertNull(ingress.pollNextEvent())
    }

    @Test
    fun eventIngress_overloadMarksDirtyAndEmitsResync_withoutGrowingMemory() = runBlocking {
        val capacity = 3
        val ingress = EventIngress(foregroundCapacity = capacity)
        ingress.setGeneration(1L)

        // Fill capacity with distinct packages
        assertTrue(ingress.sendForegroundCandidate(ProtectionEvent.ForegroundCandidate("com.app.1", 1000L, 1L)))
        assertTrue(ingress.sendForegroundCandidate(ProtectionEvent.ForegroundCandidate("com.app.2", 1001L, 2L)))
        assertTrue(ingress.sendForegroundCandidate(ProtectionEvent.ForegroundCandidate("com.app.3", 1002L, 3L)))

        // 4th distinct candidate overflows
        val accepted = ingress.sendForegroundCandidate(ProtectionEvent.ForegroundCandidate("com.app.4", 1003L, 4L))
        assertFalse("Overflow candidate should not grow queue", accepted)
        assertEquals(1, ingress.currentOverflowResyncCount)
        assertEquals(3, ingress.currentHighWaterMark)

        // Add a control event (Exit) during overload
        ingress.sendControlEvent(ProtectionEvent.ActionExit(null))

        // Priority 1: Control event must be delivered first!
        val c1 = ingress.pollNextEvent()
        assertTrue("Control event must have priority", c1 is ProtectionEvent.ActionExit)

        // Priority 2: ResyncRequested from dirty overload flag
        val c2 = ingress.pollNextEvent()
        assertTrue("Dirty overload triggers ResyncRequested", c2 is ProtectionEvent.ResyncRequested)
        assertEquals(1L, (c2 as ProtectionEvent.ResyncRequested).generation)

        // Priority 3: Buffered candidates
        val c3 = ingress.pollNextEvent() as ProtectionEvent.ForegroundCandidate
        assertEquals("com.app.1", c3.packageName)
    }
}
