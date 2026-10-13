package vn.roboworld.congnghiep

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LƯU ĐĂNG KÝ DÙNG THỬ CỦA KHÁCH — nguyên tắc số một: KHÔNG BAO GIỜ MẤT MỘT KHÁCH NÀO.
 *
 * Thứ tự bắt buộc: GHI RA THẺ NHỚ TRƯỚC, gửi mạng sau. Wi-Fi hội chợ rớt đúng lúc đông khách nhất.
 * Chỗ ghi: /sdcard/Android/data/vn.roboworld.congnghiep/files/khach-hang/
 *   khach-hang.jsonl   mỗi dòng một khách (bền, không bao giờ ghi đè)
 *   khach-hang.csv     bản mở bằng Excel (UTF-8 BOM) — cuối ngày adb pull về
 *   hang-doi.json      những lượt chưa gửi được Telegram
 * Telegram: tuỳ chọn. Có file files/telegram.json {"token":"…","chat_id":"…"} thì gửi, không thì thôi.
 *   ⚠ Token KHÔNG ghi trong mã — APK là file phát ra ngoài.
 */
object KhachHang {
    private const val TAG = "CNKhach"
    private lateinit var thuMuc: File
    private lateinit var fJsonl: File
    private lateinit var fCsv: File
    private lateinit var fHangDoi: File
    private var token = ""; private var chatId = ""
    private val gio = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale("vi"))

    fun nap(ctx: Context) {
        thuMuc = File(ctx.getExternalFilesDir(null), "khach-hang").also { it.mkdirs() }
        fJsonl = File(thuMuc, "khach-hang.jsonl"); fCsv = File(thuMuc, "khach-hang.csv"); fHangDoi = File(thuMuc, "hang-doi.json")
        val fTg = File(ctx.getExternalFilesDir(null), "telegram.json")
        if (fTg.isFile) runCatching {
            val j = JSONObject(fTg.readText()); token = j.optString("token"); chatId = j.optString("chat_id")
            Log.d(TAG, "Telegram: có cấu hình, chat_id=$chatId")
        }.onFailure { Log.w(TAG, "telegram.json hỏng: ${it.message}") }
        Thread { guiHangDoi() }.start()
    }

    fun dem(): Int = runCatching { if (fJsonl.isFile) fJsonl.readLines().count { it.isNotBlank() } else 0 }.getOrDefault(0)
    fun trangThaiTelegram(): String = if (token.isBlank()) "chưa cấu hình" else "đã cấu hình · chờ gửi ${docMang().length()}"

    @Synchronized
    fun ghi(k: JSONObject): Boolean {
        k.put("luc", gio.format(Date())); k.put("moc", System.currentTimeMillis())
        return runCatching {
            fJsonl.appendText(k.toString() + "\n", Charsets.UTF_8)
            if (!fCsv.isFile) fCsv.writeText("﻿Thời gian,Họ tên,Điện thoại,Công ty,Tỉnh/TP,Robot quan tâm,Lĩnh vực\n", Charsets.UTF_8)
            fCsv.appendText(listOf(k.optString("luc"), k.optString("ten"), k.optString("sdt"), k.optString("cong_ty"),
                k.optString("tinh"), k.optString("san_pham"), k.optString("linh_vuc")).joinToString(",") { csv(it) } + "\n", Charsets.UTF_8)
            Log.d(TAG, "ĐÃ GHI khách: ${k.optString("ten")} · ${k.optString("sdt")} · ${k.optString("san_pham")}")
            val m = docMang(); m.put(k); fHangDoi.writeText(m.toString(), Charsets.UTF_8)
            Thread { guiHangDoi() }.start()
            true
        }.onFailure { Log.e(TAG, "KHÔNG GHI ĐƯỢC KHÁCH: ${it.message}") }.getOrDefault(false)
    }
    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    private fun docMang(): JSONArray = runCatching { if (fHangDoi.isFile) JSONArray(fHangDoi.readText()) else JSONArray() }.getOrDefault(JSONArray())

    @Synchronized
    private fun guiHangDoi() {
        if (token.isBlank() || chatId.isBlank()) return
        var m = docMang()
        while (m.length() > 0) {
            val k = m.getJSONObject(0)
            if (!guiMot(k)) break
            val moi = JSONArray(); for (i in 1 until m.length()) moi.put(m.get(i)); m = moi
            fHangDoi.writeText(m.toString(), Charsets.UTF_8)
        }
    }
    private fun guiMot(k: JSONObject): Boolean = runCatching {
        val chu = "🤖 ĐĂNG KÝ DÙNG THỬ ROBOT\n\n👤 ${k.optString("ten")}\n📞 ${k.optString("sdt")}\n🏢 ${k.optString("cong_ty")}" +
            (k.optString("tinh").takeIf { it.isNotBlank() }?.let { "\n📍 $it" } ?: "") +
            "\n🔎 Quan tâm: ${k.optString("san_pham")} (${k.optString("linh_vuc")})\n🎪 ${k.optString("su_kien")}\n🕒 ${k.optString("luc")}"
        (URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection).run {
            requestMethod = "POST"; connectTimeout = 8000; readTimeout = 8000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            outputStream.use { it.write(JSONObject().put("chat_id", chatId).put("text", chu).toString().toByteArray(Charsets.UTF_8)) }
            val ma = responseCode
            (if (ma in 200..299) inputStream else errorStream)?.bufferedReader()?.use { it.readText() }
            disconnect()
            if (ma in 200..299) Log.d(TAG, "Telegram ĐÃ GỬI: ${k.optString("ten")}") else Log.w(TAG, "Telegram từ chối HTTP $ma")
            ma in 200..299
        }
    }.onFailure { Log.w(TAG, "Telegram lỗi: ${it.message}") }.getOrDefault(false)
}
