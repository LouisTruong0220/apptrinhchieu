package vn.roboworld.congnghiep

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * Cầu hai chiều web ↔ robot.  Web → Android: đối tượng `CAU`.  Android → Web: evaluateJavascript.
 * ⚠ Hàm @JavascriptInterface chạy trên luồng riêng của WebView — đụng WebView phải post về luồng chính.
 */
object Cau {
    private const val TAG = "CNCau"
    @Volatile private var web: WebView? = null
    fun gan(w: WebView) { web = w }
    fun go() { web = null }

    private fun js(code: String) { val w = web ?: return; w.post { w.evaluateJavascript(code, null) } }
    private fun baoDocXong() = js("window.robotDocXong && window.robotDocXong()")
    private fun baoDanDuong(tt: String, loi: String) {
        if (dangVeCho) return
        js("window.baoDanDuong && window.baoDanDuong(${JSONObject.quote(tt)},${JSONObject.quote(loi)})")
    }

    // ── Giọng ──
    /** Đọc một câu: phát MP3 giọng ElevenLabs theo id, thiếu file thì đọc `vanBan` bằng giọng máy. */
    @JavascriptInterface fun noi(id: String?, vanBan: String?) {
        val t = vanBan.orEmpty(); if (t.isBlank() && id.isNullOrBlank()) return
        Giong.noi(id.orEmpty(), t) { baoDocXong() }
    }
    @JavascriptInterface fun dungDoc() { Giong.im() }

    // ── Đi lại ──
    @Volatile private var dangVeCho = false
    @Volatile private var tenDiemVeCho = "Diem 1"
    @JavascriptInterface fun datDiemVeCho(ten: String?) { val t = ten.orEmpty().trim(); if (t.isNotEmpty()) tenDiemVeCho = t }

    @JavascriptInterface fun danDuongToiDiem(tenDiem: String?) {
        val diem = tenDiem.orEmpty().trim()
        Log.d(TAG, "Đi tới '$diem'")
        if (diem.isEmpty()) { baoDanDuong("loi", "Chưa cấu hình điểm."); return }
        RobotHelper.lyDoChuaSanSang()?.let { baoDanDuong("loi", it); return }
        dangVeCho = false
        baoDanDuong("bat-dau", "")
        RobotHelper.dieuHuongToi(diem,
            khiToiNoi = { baoDanDuong("toi-noi", "") },
            khiLoi = { loi -> Log.w(TAG, "Không đi được '$diem': $loi"); RobotHelper.dungDieuHuong(); baoDanDuong("loi", loi) },
            khiCapNhat = { tin -> baoDanDuong("dang-di", tin) })
    }
    @JavascriptInterface fun dungDanDuong() { dangVeCho = false; RobotHelper.dungDieuHuong(); baoDanDuong("da-dung", "") }
    @JavascriptInterface fun lyDoChuaDanDuongDuoc(): String = RobotHelper.lyDoChuaSanSang() ?: ""
    @JavascriptInterface fun robotSanSang(): Boolean = RobotHelper.lyDoChuaSanSang() == null

    @JavascriptInterface fun kiemTraBanDo() {
        RobotHelper.daDinhVi { dinhVi ->
            RobotHelper.layDanhSachDiem { ds ->
                val j = JSONObject().put("daDinhVi", dinhVi).put("danhSachDiem", ds ?: "")
                Log.d(TAG, "Bản đồ: đãĐịnhVị=$dinhVi điểm=$ds")
                js("window.baoTinhTrangBanDo && window.baoTinhTrangBanDo(${JSONObject.quote(j.toString())})")
            }
        }
    }

    // ── Tivi (MayChu.kt) ──
    /** Lớp web gửi trạng thái màn hình (KHÔNG có dữ liệu khách) → đẩy cho mọi tivi đang xem. */
    @JavascriptInterface fun tivi(json: String?) { MayChu.capNhat(json.orEmpty()) }

    /**
     * Nguồn tiếng: "tu-dong" (mặc định) — có tivi đã bật tiếng thì TIVI phát, robot im; không có tivi
     *              thì robot tự nói như cũ. Rút dây tivi giữa buổi là robot nói lại, không ai phải bấm gì.
     *              "robot" — chỉ loa robot, tivi câm.   "ca-hai" — cả hai cùng phát (sẽ dội tiếng ~0,3 giây).
     */
    @JavascriptInterface fun cheDoTieng(): String = cheDo
    @JavascriptInterface fun datCheDoTieng(m: String?) {
        val v = m.orEmpty(); if (v !in listOf("tu-dong", "robot", "ca-hai")) return
        cheDo = v; MainApplication.luuCheDoTieng(v); baoTivi()
    }
    @Volatile var cheDo = "tu-dong"
    /** Robot có nên im không — đọc ở Giong.noi và ở nhạc nền lớp web. */
    fun robotIm(): Boolean = cheDo == "tu-dong" && MayChu.soTiviCoTieng() > 0
    @JavascriptInterface fun thongTinTivi(): String = JSONObject()
        .put("diaChi", MayChu.diaChi()).put("soTivi", MayChu.soTivi()).put("soTiviCoTieng", MayChu.soTiviCoTieng())
        .put("cheDo", cheDo).put("robotIm", robotIm()).toString()
    fun baoTivi() {
        val im = robotIm()
        Giong.datIm(im)
        js("window.baoTivi && window.baoTivi(${JSONObject.quote(thongTinTivi())})")
    }

    // ── Khách hàng ──
    /** Ghi một đăng ký dùng thử. Trả true khi ĐÃ GHI RA THẺ NHỚ (gửi mạng là chuyện sau). */
    @JavascriptInterface fun ghiKhach(json: String?): Boolean = runCatching { KhachHang.ghi(JSONObject(json.orEmpty())) }.getOrDefault(false)

    @JavascriptInterface fun thongTinMay(): String = JSONObject()
        .put("daKetNoi", RobotHelper.daKetNoi).put("biTreo", RobotHelper.biTreo)
        .put("soKhach", KhachHang.dem()).put("soGiong", Giong.soFile())
        .put("telegram", KhachHang.trangThaiTelegram()).put("agentOk", MainApplication.agentOk).toString()
}
