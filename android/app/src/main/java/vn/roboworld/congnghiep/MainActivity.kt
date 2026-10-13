package vn.roboworld.congnghiep

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Màn hình duy nhất: một WebView chạy toàn bộ giao diện sự kiện.
 *
 * Giao diện nạp từ MỘT trong hai chỗ, ưu tiên thẻ nhớ:
 *   1. <externalFilesDir>/web/index.html  — đẩy bằng adb push, sửa nội dung KHÔNG cần build lại APK
 *   2. assets/index.html                  — bản đóng sẵn trong APK, luôn có
 * Cả hai bản đều do dung-app.py sinh ra, giống hệt nhau lúc build.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        anThanhHeThong()
        WebView.setWebContentsDebuggingEnabled(true)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = true
            textZoom = 100
            mediaPlaybackRequiresUserGesture = false
            /* Màn Nova 1920×1080 @560dpi → WebView chỉ cho 548×308 css px, chữ bị Chrome ép sàn 8px.
               Bảo nó bố cục ở 1920 rồi thu nhỏ (meta viewport width=1920 trong index.html). */
            useWideViewPort = true
            loadWithOverviewMode = true
            minimumFontSize = 1
            minimumLogicalFontSize = 1
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.d("CNWeb", "${m.messageLevel()} ${m.message()} @${m.lineNumber()}"); return true
            }
        }
        Cau.gan(web)
        web.addJavascriptInterface(Cau, "CAU")

        val ngoai = File(getExternalFilesDir(null), "web/index.html")
        if (ngoai.isFile) {
            Log.d("CNWeb", "Nạp giao diện từ thẻ nhớ: ${ngoai.path}")
            web.loadUrl("file://${ngoai.path}")
        } else {
            web.loadUrl("file:///android_asset/index.html")
        }
        Log.d("CNWeb", "WebView " + (WebView.getCurrentWebViewPackage()?.versionName ?: "?"))
    }

    private fun anThanhHeThong() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) anThanhHeThong() }

    /** Ra khỏi tiền cảnh: im ngay và dừng đi — robot không được nói/đi khi màn hình là app khác. */
    override fun onPause() { super.onPause(); Giong.im(); RobotHelper.dungDieuHuong(); web.onPause() }   // dừng nhạc nền + video khi app rời tiền cảnh
    override fun onResume() { super.onResume(); web.onResume() }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("window.luiHeThong ? window.luiHeThong() : '0'") { kq -> if (kq.contains("0")) super.onBackPressed() }
    }

    override fun onDestroy() { Cau.go(); Giong.im(); web.destroy(); super.onDestroy() }
}
