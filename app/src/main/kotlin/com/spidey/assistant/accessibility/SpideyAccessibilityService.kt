package com.spidey.assistant.accessibility

import android.accessibilityservice.AccessibilityService
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitored for window updates if required
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    suspend fun clickSendInWhatsApp(timeoutMs: Long = 3500L): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root != null) {
                if (isPackageBlocklisted(root.packageName?.toString())) {
                    return false
                }
                // Try WhatsApp send button by view id
                val sendNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                for (node in sendNodes) {
                    if (clickNodeOrParent(node)) return true
                }

                // Try by content description or text
                val descNodes = root.findAccessibilityNodeInfosByText("Send")
                for (node in descNodes) {
                    val desc = node.contentDescription?.toString()
                    val text = node.text?.toString()
                    if (desc.equals("Send", ignoreCase = true) || text.equals("Send", ignoreCase = true)) {
                        if (clickNodeOrParent(node)) return true
                    }
                }
            }
            delay(200)
        }
        return false
    }

    suspend fun clickSendInGmail(timeoutMs: Long = 3500L): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root != null) {
                if (isPackageBlocklisted(root.packageName?.toString())) {
                    return false
                }
                val sendNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.gm:id/send")
                for (node in sendNodes) {
                    if (clickNodeOrParent(node)) return true
                }
                val descNodes = root.findAccessibilityNodeInfosByText("Send")
                for (node in descNodes) {
                    val desc = node.contentDescription?.toString()
                    if (desc.equals("Send", ignoreCase = true)) {
                        if (clickNodeOrParent(node)) return true
                    }
                }
            }
            delay(200)
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return false
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isPassword) return false
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
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
