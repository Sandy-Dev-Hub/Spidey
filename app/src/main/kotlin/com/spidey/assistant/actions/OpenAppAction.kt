package com.spidey.assistant.actions

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import com.spidey.spikes.util.SpikeLogger
import kotlinx.coroutines.delay

/**
 * Common pre-mapped apps with canonical names, package names, and popular aliases.
 */
enum class AllowedApp(
    val displayName: String,
    val packageName: String,
    val aliases: List<String> = emptyList()
) {
    YOUTUBE("YouTube", "com.google.android.youtube", listOf("yt", "you tube")),
    WHATSAPP("WhatsApp", "com.whatsapp", listOf("whats app", "watsapp", "wats app")),
    CHROME("Chrome", "com.android.chrome", listOf("google chrome", "browser", "internet")),
    SPOTIFY("Spotify", "com.spotify.music", listOf("music")),
    CAMERA("Camera", "com.sec.android.app.camera", listOf("cam", "take photo")),
    SETTINGS("Settings", "com.android.settings", listOf("setting", "phone settings")),
    INSTAGRAM("Instagram", "com.instagram.android", listOf("insta")),
    MAPS("Maps", "com.google.android.apps.maps", listOf("google maps", "maps", "navigation", "gps")),
    GMAIL("Gmail", "com.google.android.gm", listOf("email", "mail")),
    PHONE("Phone", "com.samsung.android.dialer", listOf("dialer", "call app")),
    MESSAGES("Messages", "com.google.android.apps.messaging", listOf("sms", "message", "text")),
    GALLERY("Gallery", "com.sec.android.gallery3d", listOf("photos", "pictures")),
    CALCULATOR("Calculator", "com.sec.android.app.popupcalculator", listOf("calc")),
    CLOCK("Clock", "com.sec.android.app.clockpackage", listOf("alarm", "timer")),
    TELEGRAM("Telegram", "org.telegram.messenger", listOf("tg")),
    CONTACTS("Contacts", "com.samsung.android.app.contacts", listOf("contact", "phonebook"))
}

/**
 * Action representing a request to open an application by name or package.
 */
data class OpenAppAction(
    val appName: String,
    val packageName: String? = null
) : AssistantAction {

    val app: AllowedApp?
        get() = AllowedApp.entries.firstOrNull {
            it.displayName.equals(appName, ignoreCase = true) ||
            (packageName != null && it.packageName == packageName)
        }

    constructor(app: AllowedApp) : this(appName = app.displayName, packageName = app.packageName)
}

/**
 * Handles [OpenAppAction] safely from any background state.
 * Uses Android 15-compliant BackgroundAppLauncher routes.
 * Verifies that the target app is actually in the foreground before reporting success.
 * If the device is locked, returns [ActionResult.NeedsUnlock] to prompt the unlock flow.
 */
class OpenAppHandler(
    private val context: Context,
    private val isDeviceLocked: () -> Boolean = {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        km?.isDeviceLocked == true
    }
) : ActionHandler<OpenAppAction> {

    override val actionType: Class<OpenAppAction> = OpenAppAction::class.java

    override suspend fun handle(action: OpenAppAction): ActionResult {
        if (isDeviceLocked()) {
            return ActionResult.NeedsUnlock
        }

        val pm = context.packageManager
        val targetName = action.appName.trim().lowercase()

        // 1. Check known app mappings
        var targetPackage: String? = action.packageName
        var displayLabel: String = action.appName

        if (targetPackage == null) {
            val matchedAllowed = AllowedApp.entries.firstOrNull {
                it.displayName.lowercase() == targetName ||
                it.aliases.any { alias -> alias == targetName } ||
                targetName.contains(it.displayName.lowercase())
            }
            if (matchedAllowed != null) {
                targetPackage = matchedAllowed.packageName
                displayLabel = matchedAllowed.displayName
            }
        }

        // 2. If package not resolved yet, dynamic scan across all installed launcher apps
        if (targetPackage == null) {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

            // Exact match on label
            var bestMatch = resolveInfos.firstOrNull {
                it.loadLabel(pm).toString().trim().lowercase() == targetName
            }

            // Substring / word match
            if (bestMatch == null) {
                bestMatch = resolveInfos.firstOrNull {
                    val label = it.loadLabel(pm).toString().trim().lowercase()
                    label.contains(targetName) || targetName.contains(label)
                }
            }

            if (bestMatch != null) {
                targetPackage = bestMatch.activityInfo.packageName
                displayLabel = bestMatch.loadLabel(pm).toString()
            }
        }

        if (targetPackage == null) {
            return ActionResult.Failure(
                spokenMessage = "I couldn't find ${action.appName} on your phone.",
                reason = "App not found or not installed"
            )
        }

        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
        if (launchIntent == null) {
            return ActionResult.Failure(
                spokenMessage = "I couldn't launch $displayLabel.",
                reason = "No launcher intent for package $targetPackage"
            )
        }

        SpikeLogger.log("SPIDEY", "Attempting background launch for $displayLabel ($targetPackage)")

        // 3. Launch via background-activity-allowed options
        val launched = BackgroundAppLauncher.launch(context, launchIntent)
        if (!launched) {
            return ActionResult.Failure(
                spokenMessage = "Failed to open $displayLabel.",
                reason = "startActivity failed"
            )
        }

        // 4. Verify whether target app actually reached the foreground
        var isVerifiedInForeground = false
        for (i in 0 until 6) {
            delay(250)
            if (BackgroundAppLauncher.isAppInForeground(context, targetPackage)) {
                isVerifiedInForeground = true
                break
            }
        }

        return if (isVerifiedInForeground) {
            SpikeLogger.log("SPIDEY", "✅ Verified in foreground: $displayLabel ($targetPackage)")
            ActionResult.Success(spokenMessage = "Opening $displayLabel.")
        } else {
            SpikeLogger.log("SPIDEY", "⚠️ Unverified foreground state for $displayLabel ($targetPackage)")
            ActionResult.Failure(
                spokenMessage = "I sent the request to open $displayLabel, but can't confirm it worked.",
                reason = "Target app did not reach foreground within timeout"
            )
        }
    }
}
