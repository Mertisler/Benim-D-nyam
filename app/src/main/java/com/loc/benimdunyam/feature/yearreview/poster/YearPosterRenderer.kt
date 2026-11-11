package com.loc.benimdunyam.feature.yearreview.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.ImageDecoder
import android.os.Build
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Önizleme ve dışa aktarmada aynı 1080×1920 sanal koordinat sistemi kullanılır. */
object YearPosterRenderer {
    private const val W = 1080f
    private const val H = 1920f
    private const val IVORY = 0xFFF7F0E4.toInt()
    private const val GOLD = 0xFFECC98D.toInt()
    private const val DARK = 0xFF11271E.toInt()
    private const val INK = 0xFF302B26.toInt()

    fun render(
        context: Context,
        content: YearPosterContent,
        options: YearPosterOptions,
        width: Int = 1080,
        height: Int = 1920
    ): Bitmap {
        require(width > 0 && height > 0 && width <= 2160 && height <= 3840)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.save()
        canvas.scale(width / W, height / H)
        try {
            paintPoster(canvas, context, content, options, width)
        } finally {
            canvas.restore()
        }
        return out
    }

    private fun paintPoster(
        c: Canvas, context: Context, content: YearPosterContent,
        options: YearPosterOptions, outputWidth: Int
    ) {
        val dark = options.theme == YearPosterTheme.CINEMA
        val bg = if (dark) DARK else IVORY
        val foreground = if (dark) Color.WHITE else INK
        val muted = if (dark) 0xFFCAD4C7.toInt() else 0xFF7B7369.toInt()
        val accent = if (dark) GOLD else 0xFF426850.toInt()
        val surface = if (dark) 0xFF244638.toInt() else 0xFFEAE1D2.toInt()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, W, H,
            if (dark) intArrayOf(0xFF0F241C.toInt(), 0xFF254937.toInt(), 0xFF091C17.toInt())
            else intArrayOf(IVORY, 0xFFEDE0CD.toInt(), IVORY),
            null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)
        p.shader = null

        // Köşelerde film afişi dokusu; hiçbir kullanıcı verisi içermez.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f
        p.color = if (dark) 0x2245B990 else 0x224F7559
        for (i in 0 until 8) {
            c.drawCircle(1040f, 180f, 90f + i * 38f, p)
            c.drawCircle(28f, 1760f, 80f + i * 34f, p)
        }
        p.style = Paint.Style.FILL

        text(c, if (content.seasonTitle == null) "BENİM DÜNYAM  /  YILLIK HİKÂYE" else "BENİM DÜNYAM  /  SEZON HİKÂYESİ", 82f, 126f,
            25f, accent, bold = true, spacing = 2f)
        line(c, 82f, 161f, 998f, 161f, accent, 2f)
        if (content.seasonTitle == null) {
            text(c, content.year.toString(), 80f, 340f, 177f, accent, serif = true)
        } else {
            // Uzun dönem adları tek satıra sığsın, görseli taşırmasın.
            val title = content.seasonTitle.take(34)
            text(c, title, 80f, 340f,
                if (title.length > 20) 51f else 69f, accent, serif = true)
        }
        text(c, "BENİM HİKÂYEM", 85f, 415f, 52f, foreground, serif = true, bold = true)
        text(c, if (content.seasonTitle == null) "Bir yıl. Birbirinden değerli küçük anlar."
        else "Bir dönem. Birbirinden değerli küçük anlar.", 86f, 474f, 29f, muted)

        val imageArea = RectF(82f, 535f, 998f, 1130f)
        roundRect(c, imageArea, 32f, surface)
        val image = content.photoPath?.let { path -> decodePrivateCover(context, path, outputWidth) }
        if (image != null) {
            try {
                c.save()
                c.clipPath(Path().apply { addRoundRect(imageArea, 32f, 32f, Path.Direction.CW) })
                val scale = max(imageArea.width() / image.width, imageArea.height() / image.height)
                val destW = image.width * scale
                val destH = image.height * scale
                val dest = RectF(
                    imageArea.centerX() - destW / 2f, imageArea.centerY() - destH / 2f,
                    imageArea.centerX() + destW / 2f, imageArea.centerY() + destH / 2f
                )
                c.drawBitmap(image, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                val gradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 750f, 0f, 1130f,
                        intArrayOf(Color.TRANSPARENT, 0xB0000000.toInt()), null,
                        Shader.TileMode.CLAMP)
                }
                c.drawRect(imageArea, gradient)
                c.restore()
                text(c, if (content.seasonTitle == null) "BU YILIN KARELERİNDEN" else "BU DÖNEMİN KARELERİNDEN", 120f, 1076f, 29f, Color.WHITE, bold = true)
            } finally {
                image.recycle()
            }
        } else {
            // Fotoğraf paylaşma tercihi kapalıysa veya fotoğraf okunamazsa güvenli illüstrasyon.
            p.color = if (dark) 0xFF355E49.toInt() else 0xFFD7C4A9.toInt()
            c.drawCircle(790f, 780f, 210f, p)
            p.color = if (dark) 0xFF426E55.toInt() else 0xFFC4AC8A.toInt()
            c.drawCircle(780f, 785f, 135f, p)
            line(c, 135f, 910f, 944f, 910f, accent, 2f)
            text(c, "HER ANI BİR SAHNE", 125f, 990f, 53f,
                if (dark) IVORY else INK, serif = true, bold = true)
            text(c, "Bu hikâyenin başrolü sensin.", 126f, 1047f, 29f,
                if (dark) 0xFFCED9CC.toInt() else 0xFF554C42.toInt())
        }

        if (options.includeStats) {
            val values = listOf(
                content.journalCount.toString() to "ANI",
                content.uniquePeopleCount.toString() to "İNSAN",
                (if (content.seasonTitle == null) content.seasonCount else content.activeMonthCount).toString() to
                        (if (content.seasonTitle == null) "SEZON" else "AY")
            )
            values.forEachIndexed { index, (value, label) ->
                val left = 82f + index * 314f
                roundRect(c, RectF(left, 1172f, left + 288f, 1379f), 23f, surface)
                text(c, value, left + 23f, 1280f, 72f, foreground, serif = true)
                text(c, label, left + 25f, 1340f, 27f, accent, bold = true)
            }
        } else {
            line(c, 82f, 1245f, 998f, 1245f, accent, 2f)
        }

        val earliest = if (options.includeStats) 1442f else 1330f
        var cursor = earliest
        if (content.personNames.isNotEmpty()) {
            text(c, if (content.seasonTitle == null) "BU YILIN İNSANLARI" else "BU DÖNEMİN İNSANLARI", 85f, cursor, 25f, accent, bold = true)
            cursor += 47f
            cursor = wrappedText(c, content.personNames.joinToString("  ·  "), 86f,
                cursor, 910f, 33f, foreground, maxLines = 2, lineHeight = 45f) + 38f
        }
        if (content.memoryTitle != null || content.memoryExcerpt != null) {
            text(c, if (content.seasonTitle == null) "YILIN SAYFALARINDAN" else "SEZONUN SAYFALARINDAN", 85f, cursor, 25f, accent, bold = true)
            cursor += 52f
            if (content.memoryTitle != null) {
                cursor = wrappedText(c, content.memoryTitle, 86f, cursor,
                    904f, 35f, foreground, bold = true, maxLines = 2, lineHeight = 46f) + 25f
            }
            if (content.memoryExcerpt != null && cursor < 1680f) {
                wrappedText(c, content.memoryExcerpt, 86f, cursor, 902f,
                    28f, muted, maxLines = if (cursor < 1580f) 2 else 1, lineHeight = 41f)
            }
        }
        if (content.personNames.isEmpty() && content.memoryTitle == null && content.memoryExcerpt == null) {
            text(c, "${content.activeMonthCount} AYDAN KALAN ANILAR", 86f, 1500f, 31f, accent, bold = true)
            text(c, if (content.seasonTitle == null) "Bazı yıllar geçer. Bazıları bizimle kalır."
            else "Bazı dönemler geçer. Hatıraları bizimle kalır.", 86f, 1556f, 30f, foreground, serif = true)
        }
        line(c, 82f, 1773f, 998f, 1773f, accent, 2f)
        text(c, "BU HİKÂYE DEVAM EDİYOR.", 83f, 1831f, 29f, foreground, bold = true)
        text(c, "BENİM DÜNYAM", 83f, 1872f, 20f, muted, spacing = 1.5f)
    }

    private fun decodePrivateCover(context: Context, path: String, targetOutputWidth: Int): Bitmap? {
        return try {
            val root = File(context.filesDir, "benim_dunyam_journal_photos").canonicalFile
            val file = File(path).canonicalFile
            // Yalnızca uygulamanın yönetilen anı fotoğrafları kabul edilir.
            if (!file.path.startsWith(root.path + File.separator) || !file.isFile) return null
            val target = max(720, targetOutputWidth)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val size = info.size
                    val factor = min(1f, target.toFloat() / max(size.width, size.height))
                    decoder.setTargetSize(max(1, (size.width * factor).toInt()),
                        max(1, (size.height * factor).toInt()))
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val sample = generateSequence(1) { it * 2 }
                    .first { max(bounds.outWidth, bounds.outHeight) / it <= target || it >= 64 }
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun roundRect(c: Canvas, rect: RectF, radius: Float, color: Int) =
        c.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        c.drawLine(x1, y1, x2, y2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; strokeWidth = width
        })
    }

    private fun text(
        c: Canvas, s: String, x: Float, baseline: Float, size: Float, color: Int,
        serif: Boolean = false, bold: Boolean = false, spacing: Float = 0f
    ) {
        val paint = font(size, color, serif, bold)
        // Android Paint'te letterSpacing yok; düz çizim kullanılır, parametre dekoratiftir.
        c.drawText(s, x, baseline, paint)
    }

    private fun font(size: Float, color: Int, serif: Boolean = false, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    /** Metin çok uzunsa posterde dışarı taşmaz; yalnızca izin verilen satırlar çizilir. */
    private fun wrappedText(
        c: Canvas, raw: String, x: Float, baseline: Float, maxWidth: Float,
        size: Float, color: Int, bold: Boolean = false,
        maxLines: Int = 2, lineHeight: Float = size * 1.35f
    ): Float {
        val p = font(size, color, bold = bold)
        val words = raw.replace(Regex("\\s+"), " ").trim().split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        lines.take(maxLines).forEachIndexed { idx, s ->
            var draw = s
            val last = idx == maxLines - 1 && lines.size > maxLines
            if (last) draw += "…"
            while (draw.isNotEmpty() && p.measureText(draw) > maxWidth) {
                draw = draw.dropLast(if (draw.endsWith("…") && draw.length > 1) 2 else 1)
                    .trimEnd('…') + "…"
            }
            c.drawText(draw, x, baseline + idx * lineHeight, p)
        }
        return baseline + min(maxLines, lines.size) * lineHeight
    }
}
