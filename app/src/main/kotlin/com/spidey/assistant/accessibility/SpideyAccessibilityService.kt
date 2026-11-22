package com.spidey.assistant.accessibility

import android.accessibilityservice.AccessibilityService
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "SpideyAccessibilityService connected and ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
        Log.i(TAG, "SpideyAccessibilityService destroyed")
    }

    suspend fun clickSendInWhatsApp(timeoutMs: Long = 5000L): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (root in getAllRoots()) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                if (isPackageBlocklisted(pkg)) continue
                if (!pkg.contains("whatsapp")) continue

                // 1. Try WhatsApp send button by view id
                try {
                    val sendNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                    for (node in sendNodes) {
                        if (clickNodeOrTap(node)) {
                            Log.i(TAG, "Clicked WhatsApp send button via id")
                            return true
                        }
                    }
                } catch (_: Exception) {}

                // 2. Try by content description or text
                try {
                    val descNodes = root.findAccessibilityNodeInfosByText("Send")
                    for (node in descNodes) {
                        val desc = node.contentDescription?.toString()
                        val text = node.text?.toString()
                        if (desc.equals("Send", ignoreCase = true) || text.equals("Send", ignoreCase = true)) {
                            if (clickNodeOrTap(node)) {
                                Log.i(TAG, "Clicked WhatsApp send button via text/desc")
                                return true
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 3. Recursive search
                if (findAndClickSendNode(root)) {
                    return true
                }
            }
            delay(200)
        }
        Log.w(TAG, "clickSendInWhatsApp timed out after ${timeoutMs}ms")
        return false
    }

    suspend fun clickSendInGmail(timeoutMs: Long = 6000L): Boolean {
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
            delay(200)
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

    suspend fun setGmailBody(text: String, timeoutMs: Long = 4000L): Boolean {
        if (text.isBlank()) return false
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (root in getAllRoots()) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                if (isPackageBlocklisted(pkg)) continue
                if (findAndSetBodyNode(root, text)) {
                    Log.i(TAG, "Successfully set Gmail body via accessibility")
                    return true
                }
            }
            delay(150)
        }
        Log.w(TAG, "setGmailBody timed out after ${timeoutMs}ms")
        return false
    }

    private suspend fun findAndSetBodyNode(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isPassword) return false
        val viewId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val isSubject = viewId.contains("subject") || (bounds.bottom > 0 && bounds.bottom <= 721)
        val isRecipient = viewId.contains("to") || viewId.contains("recipient") || viewId.contains("peoplekit")

        // Target ONLY the editable EditText in the message body area (below Subject, top >= 700)
        val isBodyEditText = (className.endsWith("EditText") || node.isEditable) &&
                !isSubject && !isRecipient &&
                (viewId == "editor" || bounds.top >= 700)

        if (isBodyEditText && bounds.width() > 0 && bounds.height() > 0) {
            Log.i(TAG, "Found Gmail body EditText: id=$viewId, bounds=$bounds")

            // 1. Put text into system clipboard
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("email_body", text))
            } catch (_: Exception) {}

            // 2. Try ACTION_SET_TEXT directly
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                Log.i(TAG, "Set text on body node via ACTION_SET_TEXT")
                return true
            }

            // 3. Try ACTION_PASTE directly
            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                Log.i(TAG, "Pasted text directly into body node")
                return true
            }

            // 4. Tap the center of the editor, delay 150ms, then paste
            val clickPath = Path().apply {
                moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 50))
                .build()
            dispatchGesture(gesture, null, null)
            delay(150)

            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                Log.i(TAG, "Pasted into focused node after tap")
                return true
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                Log.i(TAG, "Pasted into body node after tap")
                return true
            }
        }

        // Also check if this is the WebView (wc_body) without child EditText yet
        val isWebViewBody = (className.contains("WebView") || viewId.endsWith(":id/wc_body")) &&
                bounds.top >= 700 && bounds.width() > 0 && bounds.height() > 0

        // Traverse children to find the inner EditText
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndSetBodyNode(child, text)) {
                return true
            }
        }

        // If no child EditText was found inside the WebView, tap the WebView to activate the editor
        if (isWebViewBody) {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("email_body", text))
            } catch (_: Exception) {}

            val clickPath = Path().apply {
                moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 50))
                .build()
            dispatchGesture(gesture, null, null)
            delay(200)

            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                Log.i(TAG, "Pasted into focused node after tapping WebView")
                return true
            }
        }

        return false
    }

    private fun getAllRoots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { list.add(it) }
        try {
            for (window in windows) {
                window.root?.let { list.add(it) }
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
