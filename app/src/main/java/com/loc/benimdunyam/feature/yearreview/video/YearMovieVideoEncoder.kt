package com.loc.benimdunyam.feature.yearreview.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.yearreview.YearReviewReport
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Android'in yerleşik H.264 donanım kodlayıcısı; internet, ffmpeg veya yeni Gradle bağımlılığı yok. */
object YearMovieVideoEncoder {
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val BITRATE = 3_500_000

    /** Başarı halinde geçici MP4 dosyasının sahipliği çağırana geçer; çağıran onu silmelidir. */
    suspend fun encode(
        context: Context,
        report: YearReviewReport,
        coverPaths: Map<String, String>,
        featured: List<JournalEntry>,
        options: YearMovieVideoOptions,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.Default) {
        val plan = YearMovieVideoPlanner.create(report, coverPaths, featured, options)
        require(plan.totalFrames > 0) { "Bu yıl için kaydedilmiş anı bulunamadı." }
        val dir = File(context.cacheDir, "year_movies_temp")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Geçici video dizini oluşturulamadı.")
        dir.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 2*86_400_000L }
            ?.forEach { it.delete() }
        val output = File(dir, "movie_${report.year}_${UUID.randomUUID()}.mp4")
        var codec: MediaCodec? = null
        var surface: Surface? = null
        var egl: EncoderEglWindow? = null
        var muxer: MediaMuxer? = null
        var painter: YearMovieVideoFramePainter? = null
        var startedCodec = false
        var completed = false
        try {
            val format = MediaFormat.createVideoFormat(
                MIME, YearMovieVideoPlan.WIDTH, YearMovieVideoPlan.HEIGHT
            ).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, YearMovieVideoPlan.FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            val openedCodec = MediaCodec.createEncoderByType(MIME)
            codec = openedCodec
            openedCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val openedSurface = openedCodec.createInputSurface()
            surface = openedSurface
            openedCodec.start()
            startedCodec = true
            val openedMuxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = openedMuxer
            val openedEgl = EncoderEglWindow(openedSurface)
            egl = openedEgl
            val openedPainter = YearMovieVideoFramePainter(context.applicationContext, report, coverPaths, featured, options)
            painter = openedPainter
            val drain = EncodedVideoDrain(openedCodec, openedMuxer)
            var number = 0
            for ((clipIndex, clip) in plan.clips.withIndex()) {
                for (localFrame in 0 until clip.frameCount) {
                    currentCoroutineContext().ensureActive()
                    val t = localFrame.toFloat() / clip.frameCount.coerceAtLeast(1)
                    val bitmap = openedPainter.render(clip, clipIndex, t, plan.clips.size)
                    val ptsNanos = number.toLong() * 1_000_000_000L / plan.fps
                    // GL swap sonra enkoder output'u tahliye edilir; sırasıyla PTS korunur.
                    openedEgl.draw(bitmap, ptsNanos)
                    drain.drain(endOfStream = false)
                    number++
                    if (number == 1 || number % 12 == 0 || number == plan.totalFrames) {
                        onProgress(((number * 100L) / plan.totalFrames).toInt().coerceIn(1, 99))
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            drain.drain(endOfStream = true)
            if (!drain.finished) throw IOException("Video kodlama tamamlanamadı.")
            if (!drain.muxerStarted) throw IOException("MP4 kapsayıcısı başlatılamadı.")
            openedMuxer.stop()
            openedMuxer.release()
            muxer = null
            completed = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            runCatching { painter?.close() }
            runCatching { egl?.close() }
            runCatching { surface?.release() }
            if (startedCodec) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.closeSafely() }
            if (!completed) output.delete()
        }
        if (!output.isFile || output.length() < 1024L) {
            output.delete()
            throw IOException("MP4 dosyası boş veya eksik oluştu.")
        }
        output
    }
}

private fun MediaMuxer.closeSafely() {
    // stop() başlatılmamış muxer için hata verir; release() her iki durumda da çağrılır.
    runCatching { stop() }
    release()
}

/** INFO_OUTPUT_FORMAT_CHANGED gelmeden track eklenmez. EOS gelene kadar son paket okunur. */
private class EncodedVideoDrain(
    private val codec: MediaCodec,
    private val muxer: MediaMuxer
) {
    private val info = MediaCodec.BufferInfo()
    private var trackIndex = -1
    var muxerStarted: Boolean = false
        private set
    var finished: Boolean = false
        private set

    fun drain(endOfStream: Boolean) {
        if (endOfStream) codec.signalEndOfInputStream()
        var emptyPolls = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            when (index) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    if (++emptyPolls > 1500) throw IOException("Video kodlayıcısından bitiş sinyali gelmedi.")
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) throw IOException("Kodlayıcı çıktı biçimi beklenmedik şekilde değişti.")
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (index >= 0) {
                    emptyPolls = 0
                    val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    try {
                        if (info.size > 0 && !isConfig) {
                            if (!muxerStarted) throw IOException("MP4 kapsayıcısı henüz hazır değil.")
                            val data = codec.getOutputBuffer(index)
                                ?: throw IOException("Kodlayıcı çıktı tamponu okunamadı.")
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, data, info)
                        }
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                    if (isEos) { finished = true; return }
                }
            }
        }
    }
}

/** Surface.lockCanvas yerine EGL/OpenGL ES 2.0 kullanılır; encoder surface'lerinde kilitleme güvenilir değildir. */
private class EncoderEglWindow(private val surface: Surface) : AutoCloseable {
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var window = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var texture = 0
    private var allocated = false
    private val points = floatArrayOf(
        -1f,-1f,0f,1f,  1f,-1f,1f,1f,
        -1f, 1f,0f,0f,  1f, 1f,1f,0f
    )
    private val vertices = ByteBuffer.allocateDirect(points.size*4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(points); position(0) }
    init {
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "EGL ekranı açılamadı." }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL başlatılamadı." }
            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE,8, EGL14.EGL_GREEN_SIZE,8, EGL14.EGL_BLUE_SIZE,8,
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                0x3142,1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attributes,0,configs,0,1,count,0) && count[0] > 0) {
                "Kayıt uyumlu EGL yapılandırması bulunamadı."
            }
            val chosen = configs[0] ?: throw IOException("EGL yapılandırması boş.")
            context = EGL14.eglCreateContext(display,chosen,EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            check(context != EGL14.EGL_NO_CONTEXT) { "EGL OpenGL ES bağlamı açılamadı." }
            window = EGL14.eglCreateWindowSurface(display,chosen,surface,intArrayOf(EGL14.EGL_NONE),0)
            check(window != EGL14.EGL_NO_SURFACE) { "Video EGL yüzeyi oluşturulamadı." }
            check(EGL14.eglMakeCurrent(display,window,window,context)) { "EGL yüzeyi etkinleştirilemedi." }
            program = createProgram()
            val out = IntArray(1)
            GLES20.glGenTextures(1,out,0)
            texture = out[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun draw(bitmap: Bitmap, timeNanos: Long) {
        GLES20.glViewport(0,0,YearMovieVideoPlan.WIDTH,YearMovieVideoPlan.HEIGHT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        if (!allocated) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
            allocated = true
        } else {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,bitmap)
        }
        val aPosition = GLES20.glGetAttribLocation(program,"aPosition")
        val aTexCoord = GLES20.glGetAttribLocation(program,"aTexCoord")
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,16,vertices)
        vertices.position(2)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord,2,GLES20.GL_FLOAT,false,16,vertices)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        EGLExt.eglPresentationTimeANDROID(display,window,timeNanos)
        check(EGL14.eglSwapBuffers(display,window)) { "Kodlayıcıya kare aktarılamadı." }
    }

    private fun shader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        if (id == 0) throw IOException("OpenGL shader oluşturulamadı.")
        GLES20.glShaderSource(id,source)
        GLES20.glCompileShader(id)
        val checkResult = IntArray(1)
        GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,checkResult,0)
        if (checkResult[0] == 0) {
            val reason = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            throw IOException("Video shader hatası: $reason")
        }
        return id
    }

    private fun createProgram(): Int {
        val vs = shader(GLES20.GL_VERTEX_SHADER,"""
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }
        """.trimIndent())
        val fs = shader(GLES20.GL_FRAGMENT_SHADER,"""
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() { gl_FragColor = texture2D(uTexture, vTexCoord); }
        """.trimIndent())
        try {
            val id = GLES20.glCreateProgram()
            if (id == 0) throw IOException("OpenGL programı oluşturulamadı.")
            GLES20.glAttachShader(id,vs)
            GLES20.glAttachShader(id,fs)
            GLES20.glLinkProgram(id)
            val link = IntArray(1)
            GLES20.glGetProgramiv(id,GLES20.GL_LINK_STATUS,link,0)
            if (link[0] == 0) {
                val reason = GLES20.glGetProgramInfoLog(id)
                GLES20.glDeleteProgram(id)
                throw IOException("OpenGL bağlantı hatası: $reason")
            }
            return id
        } finally {
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
        }
    }

    override fun close() {
        if (display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT && window != EGL14.EGL_NO_SURFACE) {
            runCatching { EGL14.eglMakeCurrent(display,window,window,context) }
            if (texture != 0) runCatching { GLES20.glDeleteTextures(1,intArrayOf(texture),0) }
            if (program != 0) runCatching { GLES20.glDeleteProgram(program) }
        }
        if (display != EGL14.EGL_NO_DISPLAY) {
            runCatching { EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT) }
            if (window != EGL14.EGL_NO_SURFACE) runCatching { EGL14.eglDestroySurface(display,window) }
            if (context != EGL14.EGL_NO_CONTEXT) runCatching { EGL14.eglDestroyContext(display,context) }
            runCatching { EGL14.eglTerminate(display) }
        }
        window = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
        display = EGL14.EGL_NO_DISPLAY
    }
}
