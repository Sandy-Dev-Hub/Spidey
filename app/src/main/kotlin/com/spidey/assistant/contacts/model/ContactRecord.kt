package com.spidey.assistant.contacts.model

/**
 * Clean domain representation of a device contact.
 * Contains only identity and phone numbers, avoiding raw bloat.
 */
data class ContactRecord(
    val id: String,
    val lookupKey: String,
    val displayName: String,
    val phoneNumbers: List<PhoneNumberRecord> = emptyList()
)

data class PhoneNumberRecord(
    val number: String,
    val label: String = "Mobile"
)
