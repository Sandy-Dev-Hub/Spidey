package com.spidey.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
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

    suspend fun clickSendInWhatsApp(timeoutMs: Long = 4500L): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (root in getAllRoots()) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                if (isPackageBlocklisted(pkg)) continue
                if (!pkg.contains("whatsapp")) continue

                // 1. Try WhatsApp send button by view id
                val sendNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                for (node in sendNodes) {
                    if (clickNodeOrTap(node)) {
                        Log.i(TAG, "Clicked WhatsApp send button via id")
                        return true
                    }
                }

                // 2. Try by content description or text
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
            }
            delay(200)
        }
        Log.w(TAG, "clickSendInWhatsApp timed out after ${timeoutMs}ms")
        return false
    }

    suspend fun clickSendInGmail(timeoutMs: Long = 4500L): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (root in getAllRoots()) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                if (isPackageBlocklisted(pkg)) continue
                if (!pkg.contains("gmail") && !pkg.contains("google.android.gm")) continue

                val sendNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.gm:id/send")
                for (node in sendNodes) {
                    if (clickNodeOrTap(node)) {
                        Log.i(TAG, "Clicked Gmail send button via id")
                        return true
                    }
                }
                val descNodes = root.findAccessibilityNodeInfosByText("Send")
                for (node in descNodes) {
                    val desc = node.contentDescription?.toString()
                    val text = node.text?.toString()
                    if (desc.equals("Send", ignoreCase = true) || text.equals("Send", ignoreCase = true)) {
                        if (clickNodeOrTap(node)) {
                            Log.i(TAG, "Clicked Gmail send button via text/desc")
                            return true
                        }
                    }
                }
            }
            delay(200)
        }
        Log.w(TAG, "clickSendInGmail timed out after ${timeoutMs}ms")
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

        // 1. Physical gesture tap at node's screen center coordinates
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
                return true
            }
        }

        // 2. Fallback to performAction ACTION_CLICK
        if (node.isClickable && node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isPassword) return false
            if (parent.isClickable && parent.isEnabled && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    private fun isPackageBlocklisted(pkg: String?): Boolean {
        if (pkg == null) return false
        return BLOCKLISTED_PACKAGES.contains(pkg.lowercase())
    }
}
