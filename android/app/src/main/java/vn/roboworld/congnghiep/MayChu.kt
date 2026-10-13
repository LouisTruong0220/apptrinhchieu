package vn.roboworld.congnghiep

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * MÁY CHỦ CHO TIVI — robot tự phát một trang web, tivi cùng Wi-Fi mở http://<ip-robot>:8090 là thấy
 * robot đang chiếu gì, đang đọc câu nào, và phát đúng file MP3 giọng Yuna đó ra loa tivi.
 *
 * Không chép điểm ảnh (không phải scrcpy): lớp web gửi TRẠNG THÁI (màn nào · sản phẩm nào · tab nào ·
 * ảnh thứ mấy · câu đang đọc) qua CAU.tivi(json) → đây → đẩy ngay cho mọi tivi đang nối bằng SSE
 * (/su-kien). Tivi tự vẽ bố cục màn to từ chính dữ liệu sản phẩm nhúng trong tivi.html.
 *
 *   GET /                 tivi.html
 *   GET /su-kien          luồng SSE — robot đẩy trạng thái ngay khi đổi
 *   GET /trang-thai?tieng=1  JSON trạng thái mới nhất — tivi gọi 2 giây/lần làm NHỊP BÁO DANH,
 *                         và là đường lui cho trình duyệt tivi không có EventSource
 *   GET /anh|video|bieu-cam|giong|nhac/...   media lấy y hệt chỗ app robot lấy (thẻ nhớ trước, APK sau)
 *
 * ⚠ KHÔNG BAO GIỜ gửi họ tên / số điện thoại khách sang tivi — lớp web chỉ gửi tên MÀN, không gửi ô form.
 * ⚠ Cổng 8080 đã bị ROM Minibot chiếm (EADDRINUSE, máy chủ chết lặng) → dùng 8090, thử dần các cổng sau.
 */
object MayChu {
    private const val TAG = "CNTivi"
    private val CONG = intArrayOf(8090, 8092, 8093, 8181)
    private lateinit var ctx: Context
    @Volatile private var sock: ServerSocket? = null
    @Volatile var cong = 0; private set

    /** Trạng thái mới nhất lớp web gửi xuống — tivi nối vào sau vẫn thấy ngay màn hiện tại. */
    @Volatile private var trangThai = "{}"

    private class Khach(val out: OutputStream, val coTieng: Boolean)
    private val khachSse = CopyOnWriteArrayList<Khach>()
    /** Tivi đi đường hỏi định kỳ (/trang-thai): ip → (lần hỏi cuối, có tiếng không). */
    private val khachHoi = ConcurrentHashMap<String, Pair<Long, Boolean>>()

    /** Mọi lần ghi socket đi qua MỘT luồng — không để luồng JS của WebView chờ mạng. */
    private val ghi = Executors.newSingleThreadScheduledExecutor()
    private val xuLy = Executors.newCachedThreadPool()

    fun batDau(c: Context) {
        ctx = c.applicationContext
        Thread({
            for (p in CONG) {
                val s = runCatching { ServerSocket(p) }.getOrNull()
                if (s == null) { Log.w(TAG, "Cổng $p bận"); continue }
                sock = s; cong = p
                Log.d(TAG, "Máy chủ tivi: ${diaChi()}")
                break
            }
            val s = sock ?: run { Log.e(TAG, "Không mở được cổng nào ${CONG.joinToString()}"); return@Thread }
            while (!s.isClosed) {
                val k = runCatching { s.accept() }.getOrNull() ?: continue
                xuLy.execute { phucVu(k) }
            }
        }, "may-chu-tivi").apply { isDaemon = true }.start()
        // Nhịp tim 15 giây: giữ kết nối qua router hay cắt luồng im lặng, và dọn tivi đã tắt.
        ghi.scheduleWithFixedDelay({ phatRaw(": tim\n\n") }, 15, 15, TimeUnit.SECONDS)
        ghi.scheduleWithFixedDelay({ donKhachHoi() }, 3, 3, TimeUnit.SECONDS)
    }

    // ── Lớp web gọi ──────────────────────────────────────────────────────
    fun capNhat(json: String) {
        trangThai = json
        phatRaw("data: " + json.replace("\n", " ") + "\n\n")
    }

    fun soTivi(): Int = maxOf(khachSse.size, khachHoi.size)
    /**
     * Đếm theo NHỊP BÁO DANH (/trang-thai, tivi gọi 2 giây/lần), KHÔNG theo luồng SSE: tivi tắt nguồn
     * đột ngột thì socket SSE không đóng — ghi vào vẫn "thành công" vào bộ đệm TCP suốt mấy phút,
     * robot tưởng tivi còn nghe và im luôn. Quá 6 giây không báo danh là coi như tivi đã đi.
     */
    fun soTiviCoTieng(): Int = khachHoi.values.count { it.second }

    fun diaChi(): String {
        if (cong == 0) return ""
        val ip = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
                .flatMap { ni -> if (!ni.isUp || ni.isLoopback) emptyList() else ni.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull() ?: return ""
        return "http://$ip:$cong"
    }

    // ── Bên trong ────────────────────────────────────────────────────────
    @Volatile private var coTiengTruoc = 0
    /** Số tivi có tiếng đổi → báo lớp web (để robot tự im / tự nói lại ở chế độ Tự động). */
    private fun soatThayDoi() {
        val n = soTiviCoTieng()
        if (n != coTiengTruoc) { coTiengTruoc = n; Cau.baoTivi() }
    }

    private fun phatRaw(chu: String) {
        val b = chu.toByteArray(Charsets.UTF_8)
        ghi.execute {
            var bot = false
            for (k in khachSse) {
                val ok = runCatching { k.out.write(b); k.out.flush() }.isSuccess
                if (!ok) { khachSse.remove(k); runCatching { k.out.close() }; bot = true }
            }
            if (bot) { Log.d(TAG, "Tivi rời đi — còn ${khachSse.size}"); soatThayDoi() }
        }
    }

    private fun donKhachHoi() {
        val gioiHan = System.currentTimeMillis() - 6000
        if (khachHoi.entries.removeIf { it.value.first < gioiHan }) soatThayDoi()
    }

    private fun phucVu(k: Socket) {
        var giuLai = false
        try {
            k.soTimeout = 10000
            /* Trình duyệt tivi Samsung (và Chrome mới) TỰ NÂNG http → https: gửi gói TLS ClientHello (byte đầu
               0x16) chứ không gửi "GET …". Bản đầu ngồi readLine chờ dấu xuống dòng 10 giây rồi mới cắt — trình duyệt
               coi là lỗi và KHÔNG lùi về http, tivi đứng ở trang lỗi (đo 07/10/2026, UA75AU8000). Đóng NGAY thì nó lùi. */
            val tho = java.io.PushbackInputStream(k.getInputStream(), 1)
            val dau1 = tho.read()
            if (dau1 < 0) return
            if (dau1 == 0x16) { Log.d(TAG, "${k.inetAddress.hostAddress} gọi https — đóng ngay để trình duyệt lùi về http"); return }
            tho.unread(dau1)
            val vao = BufferedReader(InputStreamReader(tho, Charsets.UTF_8))
            val dong = vao.readLine() ?: return
            val phan = dong.split(" ")
            if (phan.size < 2) return
            if (!dong.contains("/trang-thai")) Log.d(TAG, "${k.inetAddress.hostAddress} → $dong")
            var range: String? = null
            while (true) { val h = vao.readLine() ?: break; if (h.isEmpty()) break
                if (h.startsWith("Range:", true)) range = h.substringAfter(':').trim() }
            val day = phan[1]
            val duong = java.net.URLDecoder.decode(day.substringBefore('?'), "UTF-8").trimStart('/')
            val hoi = day.substringAfter('?', "")
            val out = k.getOutputStream()
            when {
                phan[0] != "GET" && phan[0] != "HEAD" -> traChu(out, 405, "text/plain", "Chỉ nhận GET")
                duong == "su-kien" -> { giuLai = true; moSse(k, out, hoi.contains("tieng=1")) }
                duong == "trang-thai" -> {
                    khachHoi[k.inetAddress.hostAddress ?: "?"] = System.currentTimeMillis() to hoi.contains("tieng=1")
                    soatThayDoi()
                    traChu(out, 200, "application/json", trangThai)
                }
                duong == "" || duong == "tivi" -> traTep(out, "tivi.html", null, phan[0] == "HEAD")
                else -> traTep(out, duong, range, phan[0] == "HEAD")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Kết nối lỗi: ${e.message}")
        } finally {
            if (!giuLai) runCatching { k.close() }
        }
    }

    private fun moSse(k: Socket, out: OutputStream, coTieng: Boolean) {
        k.soTimeout = 0
        k.tcpNoDelay = true
        val dau = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nCache-Control: no-cache\r\n" +
                  "Connection: keep-alive\r\nAccess-Control-Allow-Origin: *\r\n\r\n" +
                  "retry: 2000\n\ndata: " + trangThai.replace("\n", " ") + "\n\n"
        ghi.execute {
            if (runCatching { out.write(dau.toByteArray(Charsets.UTF_8)); out.flush() }.isSuccess) {
                khachSse.add(Khach(out, coTieng))
                Log.d(TAG, "Tivi nối ${k.inetAddress.hostAddress} (tiếng=$coTieng) — đang có ${khachSse.size}")
                soatThayDoi()
            } else runCatching { k.close() }
        }
    }

    private fun traChu(out: OutputStream, ma: Int, loai: String, chu: String) {
        val b = chu.toByteArray(Charsets.UTF_8)
        out.write(("HTTP/1.1 $ma ${if (ma == 200) "OK" else "Error"}\r\nContent-Type: $loai; charset=utf-8\r\n" +
                   "Content-Length: ${b.size}\r\nCache-Control: no-cache\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(b); out.flush()
    }

    private fun loaiTep(t: String) = when (t.substringAfterLast('.').lowercase()) {
        "html" -> "text/html; charset=utf-8"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"
        "mp4" -> "video/mp4"; "mp3" -> "audio/mpeg"; "json" -> "application/json"; "js" -> "text/javascript"
        else -> "application/octet-stream"
    }

    /** Mở tệp theo đúng thứ tự app robot dùng: thẻ nhớ (files/web, files/giong) → APK (assets). */
    private fun moTep(duong: String): Pair<InputStream, Long>? {
        if (duong.contains("..")) return null
        val ngoai = ctx.getExternalFilesDir(null)
        val ungVien = mutableListOf(File(ngoai, "web/$duong"))
        if (duong.startsWith("giong/")) ungVien.add(File(ngoai, duong))     // Giong.kt đọc files/giong/<id>.mp3
        ungVien.firstOrNull { it.isFile }?.let { return it.inputStream() to it.length() }
        return runCatching {
            val fd = ctx.assets.openFd(duong)          // mp4/mp3/jpg/html để noCompress → openFd được
            fd.createInputStream() to fd.length
        }.getOrNull() ?: runCatching {
            val b = ctx.assets.open(duong).use { it.readBytes() }
            Pair<InputStream, Long>(b.inputStream(), b.size.toLong())
        }.getOrNull()
    }

    /** Có Range → trả 206. Trình duyệt tivi (Tizen/webOS) đòi 206 mới chịu phát video. */
    private fun traTep(out: OutputStream, duong: String, range: String?, chiDau: Boolean) {
        val (vao, dai) = moTep(duong) ?: run { traChu(out, 404, "text/plain", "Không có: $duong"); return }
        vao.use {
            var tu = 0L; var den = dai - 1; var ma = 200
            if (range != null && range.startsWith("bytes=") && dai > 0) {
                val r = range.removePrefix("bytes=").substringBefore(',').split('-')
                val a = r.getOrNull(0)?.toLongOrNull(); val b = r.getOrNull(1)?.toLongOrNull()
                if (a != null) { tu = a; if (b != null) den = minOf(b, dai - 1) }
                else if (b != null) { tu = maxOf(0, dai - b) }
                if (tu > den || tu >= dai) {
                    out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$dai\r\nConnection: close\r\n\r\n".toByteArray()); return
                }
                ma = 206
            }
            val soByte = den - tu + 1
            val dau = StringBuilder("HTTP/1.1 $ma ${if (ma == 206) "Partial Content" else "OK"}\r\n")
                .append("Content-Type: ${loaiTep(duong)}\r\nContent-Length: $soByte\r\nAccept-Ranges: bytes\r\n")
                .append(if (duong.endsWith(".html")) "Cache-Control: no-cache\r\n" else "Cache-Control: max-age=3600\r\n")
            if (ma == 206) dau.append("Content-Range: bytes $tu-$den/$dai\r\n")
            out.write(dau.append("Connection: close\r\n\r\n").toString().toByteArray())
            if (chiDau) { out.flush(); return }
            var boQua = tu
            while (boQua > 0) { val n = it.skip(boQua); if (n <= 0) break; boQua -= n }
            val dem = ByteArray(64 * 1024); var con = soByte
            while (con > 0) {
                val n = it.read(dem, 0, minOf(dem.size.toLong(), con).toInt()); if (n < 0) break
                out.write(dem, 0, n); con -= n
            }
            out.flush()
        }
    }
}
