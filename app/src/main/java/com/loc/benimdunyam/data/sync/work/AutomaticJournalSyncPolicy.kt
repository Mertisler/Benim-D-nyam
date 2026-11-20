package com.loc.benimdunyam.data.sync.work

/**
 * Faz A — otomatik günlük senkronizasyonunun saf karar kuralları.
 *
 * Önceki davranış: buluttaki TÜM olaylar temiz değilse (tek bir silme, katalogdan
 * yeni tek bir olay, tek bir eksik ilişki) hiçbir yeni anı içe alınmıyordu. Bulut
 * olayları değiştirilemez olduğundan bu durum kalıcıydı ve otomatik içe alma fiilen
 * dururdu.
 *
 * Yeni davranış: karar OLAY BAZINDA verilir. SafeJournalMergeRepository yalnızca
 * tüm güvenlik kontrollerinden geçen olayları "eligible" sayar; silme, katalogdan
 * yeni, ilişkisi eksik ve yerelde var olan olaylar zaten bu listenin dışındadır ve
 * yalnızca atlanır. Hiçbir güvenlik kontrolü gevşetilmez.
 *
 * Android / Firebase bağımlılığı yoktur; doğrudan birim testlenir.
 */
object AutomaticJournalSyncPolicy {

    /**
     * Yeni anı içe alınabilir mi?
     *
     * Yarım kalmış ruh hali aktarımı varken yeni içe alma yapılmaz; önce o
     * tamamlanmalıdır (SafeJournalMergeRepository.importMissing aynı kuralı uygular).
     */
    fun canImport(eligible: Int, pendingMoodFinishes: Int): Boolean =
        eligible > 0 && pendingMoodFinishes == 0

    /**
     * Önce yarım kalan ruh hali aktarımları otomatik tamamlanmalı mı?
     * İşlem tekrarlanabilir ve mevcut ruh halini asla ezmez.
     */
    fun shouldResumePendingMoods(pendingMoodFinishes: Int): Boolean =
        pendingMoodFinishes > 0

    /**
     * Tarama "tamamlandı" sayılabilir mi? Sayılırsa bir sonraki tam tarama
     * Spark kotası için birkaç saat ertelenir. Bu yüzden içe alınabilecek anı
     * kaldıysa veya ruh hali aktarımı yarım kaldıysa tamamlandı SAYILMAZ.
     *
     * Atlanan olaylar (silme, katalogdan yeni, eksik ilişki) taramayı bekletmez:
     * bunlar ancak başka cihazın yeni kataloğu veya kullanıcının manuel incelemesiyle
     * çözülür; sık tarama onları çözmez, yalnızca kota harcar.
     */
    fun scanCompleted(
        eligibleBefore: Int,
        inserted: Int,
        pendingMoodFinishesAfter: Int,
        editsBefore: Int = 0,
        editsApplied: Int = 0
    ): Boolean = inserted >= eligibleBefore && pendingMoodFinishesAfter == 0 &&
            editsApplied >= editsBefore

    /** Faz C: uygulanabilecek uzak düzenleme var mı? */
    fun canApplyEdits(remoteEdits: Int): Boolean = remoteEdits > 0

    /** Faz B: "Son silinenler"e taşınacak uzak silme var mı? */
    fun canApplyDeletes(remoteDeletesToTrash: Int): Boolean = remoteDeletesToTrash > 0
}
