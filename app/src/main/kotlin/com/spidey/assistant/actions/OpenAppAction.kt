package com.spidey.assistant.actions

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.spidey.assistant.apps.AppExclusionRepository
import com.spidey.assistant.apps.AppLauncherRepository
import com.spidey.assistant.apps.matcher.AppMatchResult
import com.spidey.assistant.apps.matcher.AppMatcher
import com.spidey.assistant.apps.model.AppRecord
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
 * Dynamically resolves any installed app by spoken name using [AppMatcher] (fuzzy matching + alias table).
 * Never uses an allowlist; excludes apps marked by the user in [AppExclusionRepository].
 * Resolves packages strictly locally from app labels.
 * Uses Android 15-compliant BackgroundAppLauncher routes and verifies foreground status.
 */
class OpenAppHandler(
    private val context: Context,
    private val appLauncherRepository: AppLauncherRepository = AppLauncherRepository(context),
    private val appExclusionRepository: AppExclusionRepository = AppExclusionRepository(context),
    private val appMatcher: AppMatcher = AppMatcher(),
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

        val installedApps = appLauncherRepository.getInstalledApps()

        // 1. Resolve target app locally using fuzzy matching & aliases
        val matchResult = appMatcher.match(action.appName, installedApps)

        val targetApp: AppRecord = when (matchResult) {
            is AppMatchResult.NotFound -> {
                return ActionResult.Failure(
                    spokenMessage = "I couldn't find ${action.appName} on your phone.",
                    reason = "App not found in installed apps"
                )
            }
            is AppMatchResult.Ambiguous -> {
                val candidateLabels = matchResult.candidates.map { it.label }
                val question = "I found ${candidateLabels.joinToString(" and ")}. Which one would you like to open?"
                return ActionResult.NeedsClarification(
                    question = question,
                    candidates = listOf("open_app_choice") + candidateLabels
                )
            }
            is AppMatchResult.Resolved -> {
                matchResult.app
            }
        }

        // 2. Check user exclusion preferences (e.g. banking apps)
        if (appExclusionRepository.isAppExcluded(targetApp.packageName)) {
            SpikeLogger.log("SPIDEY", "Blocked launch of excluded app: ${targetApp.label} (${targetApp.packageName})")
            return ActionResult.Failure(
                spokenMessage = "${targetApp.label} is excluded from voice launch in your settings.",
                reason = "App excluded by user preference"
            )
        }

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(targetApp.packageName)
        if (launchIntent == null) {
            return ActionResult.Failure(
                spokenMessage = "I couldn't launch ${targetApp.label}.",
                reason = "No launcher intent for package ${targetApp.packageName}"
            )
        }

        SpikeLogger.log("SPIDEY", "Attempting background launch for ${targetApp.label} (${targetApp.packageName})")

        // 3. Launch via BAL-exempt route
        val launched = BackgroundAppLauncher.launch(context, launchIntent)
        if (!launched) {
            return ActionResult.Failure(
                spokenMessage = "Failed to open ${targetApp.label}.",
                reason = "startActivity failed"
            )
        }

        // 4. Verify whether target app actually reached the foreground
        var isVerifiedInForeground = false
        for (i in 0 until 6) {
            delay(250)
            if (BackgroundAppLauncher.isAppInForeground(context, targetApp.packageName)) {
                isVerifiedInForeground = true
                break
            }
        }

        return if (isVerifiedInForeground) {
            SpikeLogger.log("SPIDEY", "✅ Verified in foreground: ${targetApp.label} (${targetApp.packageName})")
            val message = com.spidey.assistant.personality.SpideyPersona.appOpening(targetApp.label)
            ActionResult.Success(spokenMessage = message)
        } else {
            SpikeLogger.log("SPIDEY", "⚠️ Unverified foreground state for ${targetApp.label} (${targetApp.packageName})")
            ActionResult.Failure(
                spokenMessage = "I sent the request to open ${targetApp.label}, but can't confirm it worked.",
                reason = "Target app did not reach foreground within timeout"
            )
        }
    }
}

