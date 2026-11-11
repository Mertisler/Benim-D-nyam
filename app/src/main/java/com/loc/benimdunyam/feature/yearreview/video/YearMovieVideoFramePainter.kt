package com.loc.benimdunyam.feature.yearreview.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.loc.benimdunyam.feature.yearreview.YearMovieChapter
import com.loc.benimdunyam.feature.yearreview.YearReviewMonthStory
import com.loc.benimdunyam.feature.yearreview.YearReviewReport
import com.loc.benimdunyam.domain.model.JournalEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Android Canvas ile video karesini üretir; fotoğraf sadece sahne değiştiğinde çözülür. */
internal class YearMovieVideoFramePainter(
    private val context: Context,
    private val report: YearReviewReport,
    private val covers: Map<String, String>,
    private val featured: List<JournalEntry>,
    private val options: YearMovieVideoOptions
) : AutoCloseable {
    private val w = YearMovieVideoPlan.WIDTH.toFloat()
    private val h = YearMovieVideoPlan.HEIGHT.toFloat()
    private val gold = Color.rgb(231, 198, 138)
    private val cream = Color.rgb(250, 245, 233)
    private val muted = Color.rgb(203, 217, 202)
    private val dark = Color.rgb(16, 39, 30)
    private val forest = Color.rgb(35, 80, 56)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmap = Bitmap.createBitmap(YearMovieVideoPlan.WIDTH, YearMovieVideoPlan.HEIGHT, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private var currentClipIndex = -1
    private var currentImage: Bitmap? = null
    private val months = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
    private val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("tr-TR"))
    // İçe/dışa aktarılan filmde kişisel sezon adı varsayılan olarak paylaşılmaz.
    private val visiblePeriodTitle = when {
        report.seasonId == null -> report.year.toString()
        options.includeMemoryText -> report.seasonTitle ?: "Bir Dönem"
        else -> "Bir Dönem"
    }

    fun render(clip: YearMovieVideoClip, clipIndex: Int, progress: Float, totalClips: Int): Bitmap {
        if (currentClipIndex != clipIndex) {
            currentImage?.recycle()
            currentImage = null
            currentClipIndex = clipIndex
            if (options.includePhotos) {
                imagePathFor(clip)?.let { currentImage = decodePrivatePhoto(it) }
            }
        }
        val t = progress.coerceIn(0f, 1f)
        background(t)
        header(clipIndex, totalClips)
        when (clip.frame.chapter) {
            YearMovieChapter.OPENING -> opening(t)
            YearMovieChapter.MONTHS -> allMonths(t)
            YearMovieChapter.MONTH_STORY -> monthStory(t, clip.frame.monthIndex, clip.frame.monthYear)
            YearMovieChapter.MOOD -> Unit // Ruh hali verisi video için varsayılan olarak dışarı aktarılmaz.
            YearMovieChapter.CAST -> people(t)
            YearMovieChapter.SEASONS -> seasons(t)
            YearMovieChapter.MEMORIES -> memories(t)
            YearMovieChapter.FEATURED -> featuredMemory(t, clip.frame.journalId)
            YearMovieChapter.PHOTOS -> photos(t)
            YearMovieChapter.FINALE -> finale(t)
        }
        footer()
        return bitmap
    }

    private fun background(t: Float) {
        paint.shader = LinearGradient(0f, 0f, w, h, intArrayOf(dark, forest, dark), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        currentImage?.let { photo ->
            // Yavaş 1.00→1.08 yakınlaşma ve hafif kayma (Ken Burns).
            val scale = max(w / photo.width, h / photo.height) * (1f + .08f * t)
            val bw = photo.width * scale
            val bh = photo.height * scale
            val offset = 18f * (t - .5f)
            val dst = RectF((w-bw)/2f+offset, (h-bh)/2f, (w+bw)/2f+offset, (h+bh)/2f)
            paint.alpha = 215
            canvas.drawBitmap(photo, null, dst, paint)
            paint.alpha = 255
            paint.shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xB0000000.toInt(), 0x8D11291F.toInt(), 0xEF0B2018.toInt()),
                floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        } ?: run {
            paint.color = 0x193DE2AE
            for (i in 0..6) canvas.drawCircle(w+45f, 280f, 80f+i*65f+10*t, paint)
            paint.color = 0x2264A47B
            canvas.drawCircle(-75f, 990f, 260f+30*t, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x6680A98A
        canvas.drawRoundRect(RectF(32f, 34f, w-32f, h-34f), 20f, 20f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun header(index: Int, count: Int) {
        text("BENİM DÜNYAM  /  ${visiblePeriodTitle.take(24)}", 58f, 100f, 19f, gold, true, spacing = 1.3f)
        paint.color = gold
        canvas.drawRect(58f, 124f, 662f, 126f, paint)
        val left = 58f
        val gap = 5f
        val segmentW = (604f - gap*(min(count, 12)-1)) / min(count, 12).coerceAtLeast(1)
        val groupStart = (index / 12) * 12
        val visible = min(12, count-groupStart)
        repeat(visible) { i ->
            val x = left + (segmentW+gap)*i
            paint.color = if (groupStart + i <= index) gold else 0x557D9986
            canvas.drawRoundRect(RectF(x, 145f, x+segmentW, 149f), 3f, 3f, paint)
        }
    }

    private fun opening(t: Float) {
        smallTag(if (report.seasonId == null) "BİR YILIN FİLMİ" else "BİR DÖNEMİN FİLMİ", 254f)
        text(visiblePeriodTitle.take(30),
            56f, 475f+(1f-t)*24f, if (report.seasonId == null) 165f else 59f,
            gold, serif = true, alpha = appear(t))
        text("Benim Hikâyem", 60f, 556f, 57f, cream, serif = true, alpha = appear(t))
        wrapped(if (report.seasonId == null) "Her yıl bir hikâye bırakır. İşte seninki."
        else "Her dönem bir hikâye bırakır. İşte seninki.", 60f, 625f, 35f, 580f, 2, muted)
        statCard(60f, 800f, "${report.journalCount}", "ANI")
        statCard(265f, 800f, "${report.uniquePeopleCount}", "İNSAN")
        statCard(470f, 800f, "${if (report.seasonId == null) report.seasonCount else report.activeMonthCount}",
            if (report.seasonId == null) "SEZON" else "AY")
    }

    private fun allMonths(t: Float) {
        smallTag("SAHNE 01", 255f)
        text("On İki Ayın İzi", 58f, 355f, 56f, cream, serif = true, alpha = appear(t))
        wrapped("Takvim değişti, yaşadıkların birikti.", 60f, 410f, 28f, 595f, 2, muted)
        val counts = report.monthlyCounts
        val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        val anim = ((t-.06f)/.7f).coerceIn(0f,1f)
        repeat(12) { i ->
            val col = i % 6
            val row = i / 6
            val x = 60f + col*102f
            val baseline = 745f + row*195f
            val count = counts.getOrElse(i) { 0 }
            val height = 112f * count/maxCount * anim
            paint.color = if (count > 0) gold else 0x665F9272
            canvas.drawRoundRect(RectF(x, baseline-height, x+60f, baseline), 7f, 7f, paint)
            text(months[i].take(3).uppercase(Locale.forLanguageTag("tr-TR")), x, baseline+36f, 18f, cream, true)
            text(count.toString(), x, baseline-122f, 19f, muted)
        }
        wrapped("${report.activeMonthCount} ayda anı biriktirdin.", 60f, 1102f, 31f, 575f, 2, cream)
    }

    private fun monthStory(t: Float, monthIndex: Int?, monthYear: Int?) {
        val story = report.monthlyStories.firstOrNull {
            it.monthIndex == monthIndex && it.storyYear == monthYear
        }
        val name = months.getOrNull(monthIndex ?: -1) ?: "Bir Ay"
        smallTag("${(monthIndex ?: 0)+1}. BÖLÜM  /  $visiblePeriodTitle", 246f)
        text(name, 58f, 377f+(1f-t)*16f, 98f, gold, serif = true, alpha = appear(t))
        if (story == null || story.journalCount == 0) {
            wrapped("Bu ay kaydedilmiş bir anı yok.", 60f, 530f, 35f, 590f, 3, cream)
            wrapped("Hikâyenin sessiz bir sahnesi.", 60f, 624f, 26f, 590f, 2, muted)
            return
        }
        statCard(60f, 450f, story.journalCount.toString(), "ANI")
        statCard(265f, 450f, story.uniquePeopleCount.toString(), "İNSAN")
        if (options.includePersonNames && story.topPeople.isNotEmpty()) {
            text("BU AYIN İNSANLARI", 60f, 686f, 23f, gold, true)
            wrapped(story.topPeople.joinToString(" • ") { it.name }, 60f, 736f, 29f, 582f, 3, cream)
        }
        if (options.includeMemoryText) {
            story.lastJournal?.let { journal ->
                text("BU AYDAN BİR SAYFA", 60f, 880f, 22f, gold, true)
                wrapped(journal.title.ifBlank { "Adsız Anı" }, 60f, 940f, 37f, 585f, 2, cream)
                wrapped(journal.content, 60f, 1030f, 25f, 580f, 3, muted)
            }
        } else {
            wrapped("Bu ayın hikâyesi arşivinde saklı.", 60f, 920f, 30f, 565f, 2, muted)
        }
    }

    private fun people(t: Float) {
        smallTag("OYUNCU KADROSU", 250f)
        text(if (report.seasonId == null) "Yılın İnsanları" else "Dönemin İnsanları", 58f, 340f, 54f, cream, serif = true, alpha = appear(t))
        report.topPeople.take(5).forEachIndexed { i, person ->
            val y = 450f+i*118f
            paint.color = 0x99304E3D.toInt()
            canvas.drawRoundRect(RectF(58f,y-53f,662f,y+42f),18f,18f,paint)
            text("0${i+1}", 75f, y+10f, 33f, gold, true)
            val displayName = if (options.includePersonNames) person.name else "Kişi ${i+1}"
            wrapped(displayName, 150f, y+3f, 29f, 385f, 1, cream)
            text("${person.journalCount} anı", 536f, y+9f, 20f, muted)
        }
    }

    private fun seasons(t: Float) {
        smallTag(if (report.seasonId == null) "YILIN PERDELERİ" else "DÖNEMİN PERDELERİ", 255f)
        text("Hayatının Sezonları", 58f, 360f, 52f, cream, serif = true, alpha = appear(t))
        val max = report.topSeasons.maxOfOrNull { it.journalCount }?.coerceAtLeast(1) ?: 1
        report.topSeasons.take(4).forEachIndexed { i, season ->
            val y = 488f + i*164f
            val title = if (options.includeMemoryText) season.title else "Sezon ${i+1}"
            wrapped(title,60f,y,30f,590f,1,cream)
            paint.color = 0x886B977A.toInt()
            canvas.drawRoundRect(RectF(60f,y+23f,660f,y+47f),12f,12f,paint)
            paint.color = gold
            val percent = ((t-.05f)/.7f).coerceIn(0f,1f)
            canvas.drawRoundRect(RectF(60f,y+23f,60f+600f*season.journalCount/max*percent,y+47f),12f,12f,paint)
            text("${season.journalCount} anı", 60f, y+88f, 23f, muted)
        }
    }

    private fun memories(t: Float) {
        smallTag("DÖNÜM SAYFALARI", 252f)
        text("Unutulmaz Anlar", 58f, 365f, 57f, cream, serif = true, alpha = appear(t))
        report.selectedMemories.take(3).forEachIndexed { i, journal ->
            memoryCard(journal, i, 480f+i*196f)
        }
    }

    private fun featuredMemory(t: Float, id: String?) {
        val journal = featured.firstOrNull { it.id == id }
        smallTag("SENİN SEÇTİĞİN SAHNE", 270f)
        text("Unutulmaz Anım", 58f, 380f, 55f, cream, serif = true, alpha = appear(t))
        if (journal != null && options.includeMemoryText) {
            wrapped(journal.title.ifBlank { "Başlıksız Anı" }, 60f, 545f, 49f, 580f, 3, gold)
            wrapped(journal.content, 60f, 780f, 30f, 580f, 6, cream)
            text(dateFormat.format(Date(journal.entryDate)), 60f, 1120f, 24f, muted)
        } else {
            wrapped("Bu anı senin için özel.", 60f, 570f, 40f, 565f, 3, cream)
            wrapped("Hikâyenin bu sahnesi yalnızca sana ait.", 60f, 675f, 28f, 570f, 3, muted)
        }
    }

    private fun photos(t: Float) {
        smallTag(if (report.seasonId == null) "YILINDAN KARELER" else "DÖNEMDEN KARELER", 270f)
        text("Hatıra Kareleri", 58f, 396f, 60f, cream, serif = true, alpha = appear(t))
        wrapped("Fotoğraflar bazen kelimelerden daha fazlasını anlatır.", 60f, 510f, 32f, 570f, 4, cream)
    }

    private fun finale(t: Float) {
        smallTag("SON SAHNE", 280f)
        text("Hikâyen", 58f, 448f+(1f-t)*22f, 77f, cream, serif = true, alpha = appear(t))
        text("Devam Ediyor.", 58f, 545f, 75f, gold, serif = true, alpha = appear(t))
        wrapped(if (report.seasonId == null)
            "${report.year} yılında ${report.journalCount} anı biriktirdin. Her biri sana ait."
        else "$visiblePeriodTitle döneminde ${report.journalCount} anı biriktirdin. Her biri sana ait.",
            60f, 657f, 32f, 565f, 4, cream)
        text("BENİM DÜNYAM", 60f, 1027f, 38f, gold, true, spacing = 3f)
        text("SON  /  YENİ BİR BAŞLANGIÇ", 60f, 1072f, 20f, muted, true)
    }

    private fun memoryCard(journal: JournalEntry, index: Int, y: Float) {
        paint.color = 0xA52F4E3B.toInt()
        canvas.drawRoundRect(RectF(60f,y-42f,660f,y+125f),20f,20f,paint)
        text("SAHNE 0${index+1}", 78f,y,22f,gold,true)
        val title = if (options.includeMemoryText) journal.title.ifBlank { "Adsız Anı" } else "Bir Anı"
        wrapped(title, 78f, y+53f, 31f, 560f, 2, cream)
        if (options.includeMemoryText) text(dateFormat.format(Date(journal.entryDate)),78f,y+101f,19f,muted)
    }

    private fun statCard(x: Float, top: Float, value: String, label: String) {
        paint.color = 0xAA305641.toInt()
        canvas.drawRoundRect(RectF(x,top,x+186f,top+143f),19f,19f,paint)
        text(value, x+17f, top+78f, 60f, gold, true)
        text(label, x+17f, top+118f, 21f, cream, true)
    }

    private fun smallTag(label: String, y: Float) {
        text(label, 60f, y, 22f, gold, true, spacing = 2f)
    }

    private fun footer() {
        paint.color = 0x667A9C83
        canvas.drawRect(59f, 1163f, 661f, 1165f, paint)
        text("HAYATININ FİLMİ  •  BENİM DÜNYAM", 60f, 1195f, 18f, muted, true)
    }

    private fun appear(t: Float) = ((t / .2f).coerceIn(0f,1f)*255).toInt()

    private fun text(
        value: String, x: Float, y: Float, size: Float, color: Int,
        bold: Boolean = false, serif: Boolean = false, alpha: Int = 255, spacing: Float = 0f
    ) {
        paint.shader = null
        paint.color = color
        paint.alpha = alpha
        paint.textSize = size
        paint.typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.DEFAULT,
            if (bold) Typeface.BOLD else Typeface.NORMAL)
        paint.letterSpacingSafe(spacing)
        canvas.drawText(value, x, y, paint)
        paint.alpha = 255
    }

    // Paint'in Android'de letterSpacing özelliği yoktur: tek kelimelerde manuel harf aralığı gerekmez.
    private fun Paint.letterSpacingSafe(unused: Float) { /* İsteğe bağlı, API uyumluluğu için no-op */ }

    private fun wrapped(input: String, x: Float, y: Float, size: Float, maxWidth: Float,
                        maxLines: Int, color: Int) {
        val words = input.replace(Regex("\\s+"), " ").trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        paint.shader = null
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = size
        var line = ""
        var lines = 0
        var cursorY = y
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                text(line, x, cursorY, size, color)
                lines++
                if (lines >= maxLines) return
                cursorY += size*1.35f
                line = word
            } else line = candidate
        }
        if (line.isNotBlank() && lines < maxLines) text(line.take(110), x, cursorY, size, color)
    }

    private fun imagePathFor(clip: YearMovieVideoClip): String? {
        val ids = when (clip.frame.chapter) {
            YearMovieChapter.FEATURED -> listOfNotNull(clip.frame.journalId)
            YearMovieChapter.MONTH_STORY -> report.monthlyStories.firstOrNull {
                it.monthIndex == clip.frame.monthIndex &&
                        it.storyYear == clip.frame.monthYear
            }?.coverCandidates.orEmpty().map { it.id }
            YearMovieChapter.PHOTOS, YearMovieChapter.OPENING ->
                (featured + report.coverCandidates).distinctBy { it.id }.map { it.id }
            else -> emptyList()
        }
        return ids.asSequence().mapNotNull { covers[it] }.firstOrNull { it.isNotBlank() }
    }

    private fun decodePrivatePhoto(path: String): Bitmap? {
        return try {
            val file = File(path).canonicalFile
            val base = context.filesDir.canonicalFile
            // Yalnızca uygulama özel dosya alanındaki günlük fotoğraflarını kullan.
            if (!file.path.startsWith(base.path + File.separator) || !file.isFile) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth/sample > 1440 || bounds.outHeight/sample > 1920) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
    }

    override fun close() {
        currentImage?.recycle()
        currentImage = null
        bitmap.recycle()
    }
}
