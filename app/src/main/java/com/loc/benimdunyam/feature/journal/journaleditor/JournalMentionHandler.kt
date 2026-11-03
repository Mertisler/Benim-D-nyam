
package com.loc.benimdunyam.feature.journal.journaleditor


internal data class MentionDetection(
    val isVisible: Boolean,
    val query: String = "",
    val startIndex: Int? = null
)


internal object JournalMentionHandler {

    /**
     * Metindeki aktif @ ifadesini bulur.
     *
     * Örnek:
     * "Bugün @mert ile yürüyüş yaptım."
     *
     * query = mert
     */
    fun detect(content: String): MentionDetection {

        val atIndex = content.lastIndexOf('@')

        if (atIndex == -1) {
            return MentionDetection(
                isVisible = false
            )
        }

        // E-posta adresi veya kelime içindeki @ işaretlerini
        // kişi etiketleme olarak kabul etme.
        if (
            atIndex > 0 &&
            (content[atIndex - 1].isLetterOrDigit() ||
                    content[atIndex - 1] == '_')
        ) {
            return MentionDetection(
                isVisible = false
            )
        }

        val query = content.substring(atIndex + 1)

        // Kullanıcı boşluk veya satır atladıysa
        // aktif öneri penceresini kapat.
        if (query.any { it.isWhitespace() }) {
            return MentionDetection(
                isVisible = false
            )
        }

        return MentionDetection(
            isVisible = true,
            query = query,
            startIndex = atIndex
        )
    }


    /**
     * Kişi seçildiğinde metindeki aktif @ ifadesini değiştirir.
     */
    fun replaceMention(
        content: String,
        personName: String
    ): String {

        val detection = detect(content)

        val startIndex = detection.startIndex
            ?: return "$content@$personName "

        return content.substring(
            0,
            startIndex
        ) + "@$personName "
    }
}
