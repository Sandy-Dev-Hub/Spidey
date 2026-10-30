package com.spidey.assistant.contacts

import android.content.Context
import android.provider.ContactsContract
import com.spidey.assistant.contacts.matcher.ContactMatcher
import com.spidey.assistant.contacts.model.ContactRecord
import com.spidey.assistant.contacts.model.ContactResolution
import com.spidey.assistant.contacts.model.PhoneNumberRecord
import com.spidey.assistant.data.AliasDao
import com.spidey.assistant.safety.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Accesses device contacts via Android ContactsContract.
 * Caches contacts in-memory for fast repeated queries; never persists full contacts list.
 * Respects strict privacy guidelines (zero logging of contact names or numbers).
 */
class ContactRepository(
    private val context: Context,
    private val permissionManager: PermissionManager = PermissionManager(context),
    private val aliasDao: AliasDao? = null,
    private val matcher: ContactMatcher = ContactMatcher()
) {
    private var cachedContacts: List<ContactRecord>? = null
    private var lastCacheTime = 0L
    private val cacheDurationMs = 5 * 60 * 1000L // 5 minutes

    suspend fun findContact(query: String): ContactResolution = withContext(Dispatchers.IO) {
        if (!permissionManager.hasReadContacts()) {
            return@withContext ContactResolution.PermissionDenied
        }

        val contacts = getContacts()
        val aliases = aliasDao?.getAll() ?: emptyList()
        matcher.match(query, contacts, aliases)
    }

    suspend fun getContacts(forceRefresh: Boolean = false): List<ContactRecord> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedContacts != null && (now - lastCacheTime) < cacheDurationMs) {
            return@withContext cachedContacts!!
        }

        if (!permissionManager.hasReadContacts()) {
            return@withContext emptyList()
        }

        val contactsMap = mutableMapOf<String, MutableContact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC"
        )

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val lookupIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

            while (c.moveToNext()) {
                val id = c.getString(idIdx) ?: continue
                val lookup = c.getString(lookupIdx) ?: ""
                val name = c.getString(nameIdx) ?: continue
                val rawNumber = c.getString(numberIdx) ?: continue
                val type = c.getInt(typeIdx)
                val labelCustom = c.getString(labelIdx)

                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources,
                    type,
                    labelCustom
                ).toString()

                val entry = contactsMap.getOrPut(id) {
                    MutableContact(id, lookup, name, mutableListOf())
                }
                entry.phoneNumbers.add(PhoneNumberRecord(rawNumber, label))
            }
        }

        val list = contactsMap.values.map {
            ContactRecord(it.id, it.lookupKey, it.displayName, it.phoneNumbers)
        }

        cachedContacts = list
        lastCacheTime = now
        list
    }

    fun invalidateCache() {
        cachedContacts = null
        lastCacheTime = 0L
    }

    private data class MutableContact(
        val id: String,
        val lookupKey: String,
        val displayName: String,
        val phoneNumbers: MutableList<PhoneNumberRecord>
    )
}
