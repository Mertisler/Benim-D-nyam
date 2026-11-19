package com.loc.benimdunyam.domain.sync

import java.security.MessageDigest

/**
 * Firestore'a yazılan olay belgelerinin kararlı kimlikleri.
 *
 * Aynı günlük + sürüm + işlem türü her seferinde aynı belgeye karşılık gelir.
 * Böylece başarısız bir isteğin tekrarı aynı veriyi ikinci kez eklemez.
 * Kimlikler günlük başlığını veya metnini içermez.
 */
object SyncEventIdentity {
    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    fun journalHash(ownerUid: String, journalId: String): String {
        require(ownerUid.isNotBlank() && journalId.isNotBlank())
        return sha256("BD-JOURNAL-V1|$ownerUid|$journalId")
    }

    fun eventId(ownerUid: String, journalId: String, version: Long, operation: String): String {
        require(version > 0 && (operation == "UPSERT" || operation == "DELETE"))
        return "e_" + sha256("BD-EVENT-V1|$ownerUid|$journalId|$version|$operation")
    }
}
