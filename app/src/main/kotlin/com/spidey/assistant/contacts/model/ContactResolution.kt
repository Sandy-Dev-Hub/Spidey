package com.spidey.assistant.contacts.model

/**
 * Result of resolving a contact name query against the user's contacts.
 */
sealed interface ContactResolution {
    data class Resolved(val contact: ContactRecord, val score: Double) : ContactResolution
    data class Ambiguous(val query: String, val candidates: List<ContactRecord>) : ContactResolution
    data class NotFound(val searchedQuery: String) : ContactResolution
    data object PermissionDenied : ContactResolution
}
