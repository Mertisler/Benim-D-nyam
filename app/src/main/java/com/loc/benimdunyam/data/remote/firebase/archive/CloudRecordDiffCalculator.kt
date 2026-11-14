package com.loc.benimdunyam.data.remote.firebase.archive

import com.loc.benimdunyam.domain.model.CloudRecordDifference

/**
 * Kimlik bazlı karşılaştırma. Veritabanına veya ağa erişmez; test edilmesi kolaydır.
 * Kayıtlar değiştirilmez. Silme/birleştirme kararı bu sınıfta VERİLMEZ.
 */
internal object CloudRecordDiffCalculator {
    fun <T, K> calculate(
        device: List<T>,
        cloud: List<T>,
        key: (T) -> K,
        comparable: (T) -> Any? = { it }
    ): CloudRecordDifference {
        val deviceMap = device.associateBy(key)
        val cloudMap = cloud.associateBy(key)

        // Aynı kimlik iki kez görünüyorsa sessizce sonuncuyu seçip yanlış rapor üretmeyelim.
        require(deviceMap.size == device.size && cloudMap.size == cloud.size) {
            "Karşılaştırılacak kayıtlarda tekrarlanan kimlik bulundu."
        }

        val deviceOnly = deviceMap.keys.count { it !in cloudMap }
        val cloudOnly = cloudMap.keys.count { it !in deviceMap }
        val common = deviceMap.keys.intersect(cloudMap.keys)
        val matching = common.count { id ->
            comparable(requireNotNull(deviceMap[id])) == comparable(requireNotNull(cloudMap[id]))
        }
        return CloudRecordDifference(
            onlyOnDevice = deviceOnly,
            onlyInCloud = cloudOnly,
            different = common.size - matching,
            matching = matching
        )
    }
}
