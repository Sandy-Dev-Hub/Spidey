package com.spidey.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Custom user alias mapping (e.g. "Anna" -> "Guru Anna", "Mom" -> Contact ID).
 */
@Entity(tableName = "contact_aliases")
data class ContactAlias(
    @PrimaryKey val alias: String,
    val contactId: String,
    val contactName: String
)
