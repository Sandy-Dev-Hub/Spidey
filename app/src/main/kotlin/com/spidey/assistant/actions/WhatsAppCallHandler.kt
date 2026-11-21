package com.spidey.assistant.actions

import android.content.Context
import com.spidey.assistant.contacts.ContactRepository
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import com.spidey.assistant.skills.WhatsAppCallSkill

/**
 * Handles [WhatsAppCallAction] by delegating to [WhatsAppCallSkill].
 */
class WhatsAppCallHandler(
    private val context: Context,
    private val contactRepository: ContactRepository? = null,
    private val skill: WhatsAppCallSkill = WhatsAppCallSkill(contactRepository)
) : ActionHandler<WhatsAppCallAction> {
    override val actionType: Class<WhatsAppCallAction> = WhatsAppCallAction::class.java

    override suspend fun handle(action: WhatsAppCallAction): ActionResult {
        return skill.execute(action, context)
    }
}
