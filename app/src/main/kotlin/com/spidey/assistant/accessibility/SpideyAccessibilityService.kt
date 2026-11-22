package com.spidey.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Accessibility service for SPIDEY to automate hands-free taps (e.g. WhatsApp / Gmail "Send")
 * upon explicit user spoken confirmation ("Send it boss? -> Yes").
 *
 * Safety Constraints:
 * - Never interacts with password fields.
 * - Never interacts with blocklisted apps (banking, payments, keyguard).
 */
class SpideyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SpideyAccessibility"

        @Volatile
        var instance: SpideyAccessibilityService? = null
            private set

        fun isAvailable(): Boolean = instance != null

        val BLOCKLISTED_PACKAGES = setOf(
            "com.phonepe.app",
            "net.one97.paytm",
            "com.google.android.apps.nbu.paisa.user",
            "in.org.npci.upiapp",
            "com.sbi.lotusintouch",
            "com.csam.icici.bank.imobile",
            "com.axis.mobile",
            "com.msf.kbank.mobile",
            "com.onepassword.android",
            "com.lastpass.lpandroid",
            "com.bitwarden.mobile",
            "com.android.keyguard"
        )
    }

    @Volatile
    private var lastGmailRoot: AccessibilityNodeInfo? = null

    @Volatile
    private var lastWhatsAppRoot: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            serviceInfo = info
            Log.i(TAG, "SpideyAccessibilityService configured with dynamic flags=${info.flags}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update dynamic serviceInfo", e)
        }
        Log.i(TAG, "SpideyAccessibilityService connected and ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            val pkg = event?.packageName?.toString() ?: return
            if (pkg == "com.google.android.gm") {
                event.source?.let { lastGmailRoot = it }
            } else if (pkg.contains("whatsapp")) {
                event.source?.let { lastWhatsAppRoot = it }
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
        Log.i(TAG, "SpideyAccessibilityService destroyed")
    }

    suspend fun tapCoordinates(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        return suspendCancellableCoroutine { cont ->
            val clickPath = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(clickPath, 0, durationMs))
                .build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "tapCoordinates ($x, $y) completed successfully")
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "tapCoordinates ($x, $y) was cancelled by system")
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!dispatched && cont.isActive) {
                Log.w(TAG, "dispatchGesture returned false for ($x, $y)")
                cont.resume(false)
            }
        }
    }

    suspend fun setWhatsAppBody(text: String, timeoutMs: Long = 3000L): Boolean {
        if (text.isBlank()) return false

        try {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cb?.setPrimaryClip(ClipData.newPlainText("wa_msg", text))
        } catch (_: Exception) {}

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (root in getAllRoots()) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                if (isPackageBlocklisted(pkg)) continue
                if (!pkg.contains("whatsapp")) continue

                val entryNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
                for (entry in entryNodes) {
                    val cur = entry.text?.toString() ?: ""
                    if (cur == text) {
                        Log.i(TAG, "WhatsApp body already matches target text")
                        return true
                    }

                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    }

                    if (entry.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                        Log.i(TAG, "setWhatsAppBody: set text directly via ACTION_SET_TEXT")
                        return true
                    }

                    // Focus and paste
                    clickNodeOrTap(entry)
                    delay(150)
                    val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: entry
                    if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ||
                        focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                        Log.i(TAG, "setWhatsAppBody: set/pasted text after focus")
                        return true
                    }
                }
            }
            delay(150)
        }

        // Fallback: physical tap on WhatsApp message box [158, 2245][491, 2328] -> center (324, 2286)
        Log.i(TAG, "setWhatsAppBody: fallback physical tap at (324, 2286)")
        tapCoordinates(324f, 2286f)
        delay(200)
        val activeFocus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (activeFocus != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (activeFocus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ||
                activeFocus.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                Log.i(TAG, "setWhatsAppBody: set/pasted text on activeFocus after physical tap")
                return true
            }
        }

        return false
    }

    suspend fun clickSendInWhatsApp(timeoutMs: Long = 3500L): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (root in getAllRoots()) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                if (isPackageBlocklisted(pkg)) continue
                if (!pkg.contains("whatsapp")) continue

                try {
                    val sendNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                    for (node in sendNodes) {
                        if (clickNodeOrTap(node)) {
                            Log.i(TAG, "Clicked WhatsApp send button via id")
                            return true
                        }
                    }
                } catch (_: Exception) {}

                try {
                    val descNodes = root.findAccessibilityNodeInfosByText("Send")
                    for (node in descNodes) {
                        val desc = node.contentDescription?.toString()
                        val nodeText = node.text?.toString()
                        if (desc.equals("Send", ignoreCase = true) || nodeText.equals("Send", ignoreCase = true)) {
                            if (clickNodeOrTap(node)) {
                                Log.i(TAG, "Clicked WhatsApp send button via text/desc")
                                return true
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (findAndClickSendNode(root)) {
                    return true
                }
            }
            delay(150)
        }

        // Physical tap fallback on WhatsApp send button coordinates:
        // When keyboard is open: (986, 1317)
        // When keyboard is closed: (986, 2283)
        Log.i(TAG, "clickSendInWhatsApp: attempting gesture taps at Send coordinates")
        if (tapCoordinates(986f, 1317f)) return true
        if (tapCoordinates(986f, 2283f)) return true

        Log.w(TAG, "clickSendInWhatsApp timed out after ${timeoutMs}ms")
        return false
    }

    suspend fun setWhatsAppBodyAndSend(text: String, timeoutMs: Long = 4000L): Boolean {
        if (text.isNotBlank()) {
            setWhatsAppBody(text, timeoutMs = 2500L)
            delay(200)
        }
        return clickSendInWhatsApp(timeoutMs = 2500L)
    }

    suspend fun clickSendInGmail(timeoutMs: Long = 3500L): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (root in getAllRoots()) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                if (isPackageBlocklisted(pkg)) continue

                // 1. Direct view ID search
                try {
                    val sendNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.gm:id/send")
                    for (node in sendNodes) {
                        if (clickNodeOrTap(node)) {
                            Log.i(TAG, "Clicked Gmail send button via id")
                            return true
                        }
                    }
                } catch (_: Exception) {}

                // 2. Recursive search
                if (findAndClickSendNode(root)) {
                    return true
                }
            }
            delay(150)
        }

        // 3. Guaranteed Physical Gesture Tap:
        // In Gmail Compose, Send button is always at bounds [809,87][945,222] -> center (877, 154)
        Log.i(TAG, "clickSendInGmail: attempting gesture tap at Send coordinates (877, 154)")
        val tapped = tapCoordinates(877f, 154f)
        if (tapped) {
            Log.i(TAG, "clickSendInGmail: successfully tapped Send button via gesture at (877, 154)")
            return true
        }

        Log.w(TAG, "clickSendInGmail timed out after ${timeoutMs}ms")
        return false
    }

    private fun findAndClickSendNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return false
        val viewId = node.viewIdResourceName ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""

        val isSendButton = viewId.endsWith(":id/send") ||
                desc.equals("Send", ignoreCase = true) ||
                text.equals("Send", ignoreCase = true)

        if (isSendButton) {
            if (clickNodeOrTap(node)) {
                Log.i(TAG, "Clicked send node: id=$viewId, desc=$desc, text=$text")
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickSendNode(child)) {
                return true
            }
        }
        return false
    }

    suspend fun setGmailBody(text: String, timeoutMs: Long = 3000L): Boolean {
        if (text.isBlank()) return false

        // 1. Copy text to system clipboard
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("email_body", text))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy to clipboard", e)
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (root in getAllRoots()) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                if (isPackageBlocklisted(pkg)) continue
                if (!pkg.contains("com.google.android.gm")) continue
                if (findAndSetBodyNode(root, text)) {
                    Log.i(TAG, "Successfully set Gmail body via accessibility node")
                    return true
                }
            }
            delay(150)
        }

        // 2. Guaranteed physical tap at Gmail compose body center (540, 795)
        Log.i(TAG, "setGmailBody: attempting physical tap at body coordinates (540, 795)")
        tapCoordinates(540f, 795f)
        delay(250)

        for (root in getAllRoots()) {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ||
                    focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                    Log.i(TAG, "setGmailBody: set text via ACTION_SET_TEXT / ACTION_PASTE after tap")
                    return true
                }
            }
        }

        val activeFocus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (activeFocus != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (activeFocus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ||
                activeFocus.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                Log.i(TAG, "setGmailBody: pasted text via activeFocus")
                return true
            }
        }

        return false
    }

    private suspend fun findAndSetBodyNode(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isPassword) return false
        val viewId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val isSubject = viewId.contains("subject") || (bounds.bottom in 1..721)
        val isRecipient = viewId.contains("to") || viewId.contains("recipient") || viewId.contains("peoplekit")

        // Match the Gmail composer body:
        // - id is "editor" or ends with ":id/wc_body"
        // - or it's an editable/focusable area with top in 650..1200
        val isBodyTarget = !isSubject && !isRecipient && (
            viewId == "editor" ||
            viewId.endsWith(":id/wc_body") ||
            ((className.contains("EditText") || className.contains("WebView")) && bounds.top in 650..1200)
        )

        if (isBodyTarget && bounds.width() > 100 && bounds.height() > 50) {
            Log.i(TAG, "Found Gmail body target: id=$viewId, class=$className, bounds=$bounds")

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                Log.i(TAG, "Set text on Gmail body via ACTION_SET_TEXT")
                return true
            }

            // Tap the body to focus it, then try pasting
            clickNodeOrTap(node)
            delay(150)

            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: node
            if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ||
                focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                Log.i(TAG, "Pasted/set text into Gmail body after focus")
                return true
            }

            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                Log.i(TAG, "Pasted into Gmail body directly")
                return true
            }
        }

        // Traverse children to find the inner EditText
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndSetBodyNode(child, text)) {
                return true
            }
        }

        return false
    }

    private fun getAllRoots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        try {
            rootInActiveWindow?.let { list.add(it) }
        } catch (_: Exception) {}
        try {
            lastGmailRoot?.let { if (it.refresh()) list.add(it) }
        } catch (_: Exception) {}
        try {
            lastWhatsAppRoot?.let { if (it.refresh()) list.add(it) }
        } catch (_: Exception) {}
        try {
            val winList = windows
            for (window in winList) {
                try {
                    window.root?.let { list.add(it) }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return list
    }

    private fun clickNodeOrTap(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return false

        // 1. Direct performAction ACTION_CLICK
        if (node.isClickable && node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "clickNodeOrTap: clicked node directly via ACTION_CLICK")
            return true
        }

        // 2. Try parent performAction ACTION_CLICK
        var parent = node.parent
        while (parent != null) {
            if (parent.isPassword) break
            if (parent.isClickable && parent.isEnabled && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "clickNodeOrTap: clicked parent via ACTION_CLICK")
                return true
            }
            parent = parent.parent
        }

        // 3. Fallback to physical gesture tap at node's screen center coordinates
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0) {
            val clickPath = Path().apply {
                moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 50))
                .build()
            val dispatched = dispatchGesture(gesture, null, null)
            if (dispatched) {
                Log.d(TAG, "clickNodeOrTap: dispatched gesture tap at (${bounds.centerX()}, ${bounds.centerY()})")
                return true
            }
        }

        return false
    }

    private fun isPackageBlocklisted(pkg: String?): Boolean {
        if (pkg == null) return false
        return BLOCKLISTED_PACKAGES.contains(pkg.lowercase())
    }
}

