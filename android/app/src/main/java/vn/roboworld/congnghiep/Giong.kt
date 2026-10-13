package vn.roboworld.congnghiep

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * GIỌNG ROBOT — phát MP3 giọng ElevenLabs sinh sẵn theo id câu; thiếu file thì rơi về giọng máy (SkillApi).
 *
 * Tìm file theo thứ tự:  <externalFilesDir>/giong/<id>.mp3  (đẩy bằng adb push, đổi giọng không cần build lại)
 *                      → assets/giong/<id>.mp3               (đóng trong APK)
 * Vì sao sinh sẵn: Wi-Fi hội chợ rớt · gọi API chậm · key không được nằm trên robot · tốn ký tự đúng một lần.
 * ⚠ Luật ③: noi() LUÔN cắt câu đang đọc trước. Hai câu chồng nhau thì không nghe được câu nào.
 */
object Giong {
    private const val TAG = "CNGiong"
    private val main = Handler(Looper.getMainLooper())
    private lateinit var ctx: Context
    private var thuMucNgoai: File? = null
    private var assetsCo: Set<String> = emptySet()
    @Volatile private var may: MediaPlayer? = null
    /* Ba câu app đọc gần nhất — để MainApplication phân biệt tiếng của app với tiếng AgentOS tự nói
       (luồng chữ TTS của câu cũ còn chảy về cả giây sau khi đã sang câu mới — đo ở app Medinova). */
    private val cauGanDay = ArrayDeque<String>()

    fun nap(c: Context) {
        ctx = c.applicationContext
        thuMucNgoai = File(ctx.getExternalFilesDir(null), "giong").also { it.mkdirs() }
        assetsCo = runCatching { ctx.assets.list("giong")?.toSet() }.getOrNull() ?: emptySet()
        Log.d(TAG, "Giọng: ${assetsCo.size} file trong APK · ${thuMucNgoai?.list()?.size ?: 0} file ở thẻ nhớ")
    }
    fun soFile(): Int = assetsCo.size + (thuMucNgoai?.list()?.count { it.endsWith(".mp3") } ?: 0)

    fun laCauCuaApp(chu: String): Boolean {
        val c = chu.trim(); if (c.isEmpty()) return true
        synchronized(cauGanDay) { return cauGanDay.any { it.contains(c) || c.contains(it.take(30)) } }
    }

    fun noi(id: String, duPhong: String, khiXong: () -> Unit) {
        im()
        if (duPhong.isNotBlank()) synchronized(cauGanDay) { cauGanDay.addFirst(duPhong); while (cauGanDay.size > 3) cauGanDay.removeLast() }
        val ngoai = thuMucNgoai?.let { File(it, "$id.mp3") }
        val p = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        }
        val moDuoc = runCatching {
            when {
                ngoai != null && ngoai.isFile -> { p.setDataSource(ngoai.absolutePath); true }
                "$id.mp3" in assetsCo -> { ctx.assets.openFd("giong/$id.mp3").use { p.setDataSource(it.fileDescriptor, it.startOffset, it.length) }; true }
                else -> false
            }
        }.getOrDefault(false)
        if (!moDuoc) {
            runCatching { p.release() }
            /* Anh Trường chốt 07/09/2026: CHỈ phát giọng Yuna. Thiếu MP3 thì IM và ghi log,
               KHÔNG rơi về giọng máy — giọng máy chen vào nghe như hai robot nói cùng lúc. */
            Log.w(TAG, "THIẾU MP3 '$id' — im lặng. Sinh bằng tools/sinh-giong.py")
            main.post(khiXong); return
        }
        runCatching {
            p.setOnCompletionListener { thuDon(); main.post(khiXong) }
            p.setOnErrorListener { _, w, e -> Log.w(TAG, "Lỗi phát $id ($w/$e)"); thuDon(); main.post(khiXong); true }
            p.prepare()
            /* Tivi đang phát câu này thì robot vẫn CHẠY file ở âm lượng 0 — để khiXong về đúng lúc
               câu kết thúc (nút "Đang đọc…", nhạc nền nhỏ/to vẫn theo nhịp như khi robot tự nói). */
            if (loaIm) p.setVolume(0f, 0f)
            p.start(); may = p
            Log.d(TAG, "Phát MP3 $id")
        }.onFailure { Log.w(TAG, "Không phát được $id: ${it.message}"); thuDon(); main.post(khiXong) }
    }

    /** Loa robot im vì tivi đang phát (Cau.robotIm). Đổi giữa câu thì vặn ngay câu đang đọc. */
    @Volatile private var loaIm = false
    fun datIm(v: Boolean) {
        if (v == loaIm) return
        loaIm = v; Log.d(TAG, if (v) "Tivi đã bật tiếng — loa robot im" else "Không còn tivi có tiếng — loa robot nói lại")
        main.post { may?.let { m -> runCatching { if (v) m.setVolume(0f, 0f) else m.setVolume(1f, 1f) } } }
    }

    fun im() { thuDon(); RobotHelper.dungDoc(); MainApplication.dungTiengAgent() }
    private fun thuDon() { val m = may ?: return; may = null; runCatching { if (m.isPlaying) m.stop() }; runCatching { m.release() } }
}
