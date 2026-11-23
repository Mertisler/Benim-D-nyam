package com.loc.benimdunyam.domain.sync

/** Kullanıcıya bir çakışma için sunulan seçenek. */
enum class ConflictChoice {
    /** Bu telefondaki sürüm kalsın (silinmişse silinmiş kalsın) ve diğer cihazlara gitsin. */
    KEEP_THIS_DEVICE,
    /** Diğer cihazdaki sürüm bu telefona gelsin (silinmişse geri gelsin). */
    TAKE_OTHER_DEVICE,
    /** Bu telefondaki kalsın, diğer cihazdaki ayrı bir anı olarak da saklansın. */
    KEEP_BOTH,
    /** Diğer cihazda silindi: bu telefondaki de "Son silinenler"e taşınsın (30 gün geri alınabilir). */
    MOVE_TO_TRASH
}

/**
 * Aşama 2 — sade çakışma ekranında hangi düğmelerin gösterileceği.
 *
 * Saf Kotlin; düğmenin metni ekranda, güvenlik kontrolü depo katmanındadır.
 * Belirsiz durumlarda (aynı sürümde çelişen iki bulut kaydı gibi) yalnızca
 * "bu telefondakini tut" sunulur; çünkü yerel metin her durumda bilinen
 * ve kullanıcının gördüğü sürümdür.
 */
object ConflictChoicePolicy {

    fun choices(
        localExists: Boolean,
        localDeleted: Boolean,
        remoteIsDelete: Boolean,
        singleRemoteHead: Boolean
    ): List<ConflictChoice> = when {
        // Diğer cihaz sildi, bu telefonda duruyor.
        remoteIsDelete && localExists ->
            listOf(ConflictChoice.KEEP_THIS_DEVICE, ConflictChoice.MOVE_TO_TRASH)

        // İki tarafta da metin var ama farklı.
        localExists && singleRemoteHead ->
            listOf(ConflictChoice.KEEP_THIS_DEVICE, ConflictChoice.TAKE_OTHER_DEVICE, ConflictChoice.KEEP_BOTH)
        localExists ->
            listOf(ConflictChoice.KEEP_THIS_DEVICE)

        // Bu telefonda silindi, diğer cihazda düzenlendi.
        localDeleted && !remoteIsDelete && singleRemoteHead ->
            listOf(ConflictChoice.TAKE_OTHER_DEVICE, ConflictChoice.KEEP_THIS_DEVICE)
        localDeleted ->
            listOf(ConflictChoice.KEEP_THIS_DEVICE)

        else -> emptyList()
    }
}
