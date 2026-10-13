package vn.roboworld.congnghiep

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.ainirobot.agent.AgentCore
import com.ainirobot.agent.AppAgent
import com.ainirobot.agent.OnTranscribeListener
import com.ainirobot.agent.action.Action
import com.ainirobot.agent.base.Transcription

/**
 * Khởi tạo hai tầng:
 *  · RobotApi/SkillApi (robotservice.jar) — đi lại + giọng máy dự phòng. Đây là phần app sống bằng.
 *  · Agent SDK — app này KHÔNG dùng mic, KHÔNG gọi mô hình. Khởi tạo chỉ để:
 *      1. tắt thanh thoại của hãng (isEnableVoiceBar) — nó đè lên giao diện
 *      2. tắt bắt lời tự do (isEnableWakeFree) — robot đứng giữa hội chợ không được tự trả lời người đi ngang
 *      3. tắt bộ hoạch định (isDisablePlan)
 *      4. cắt tiếng AgentOS nếu nó vẫn tự mở miệng (onTTSResult → stopTTS)
 *  ⚠ TUYỆT ĐỐI KHÔNG đặt isMicrophoneMuted = true: nó khoá chết mic của MÁY, đặt lại false không mở được.
 *  ⚠ appId app_70eef22c4a6d4777af413ac942ea0153 — anh Trường chỉ định 07/09/2026 (assets/actionRegistry.json).
 */
class MainApplication : Application() {

    companion object {
        private const val TAG = "CNApp"
        @Volatile var agentOk = false
        fun dungTiengAgent() = runCatching { AgentCore.stopTTS() }.isSuccess
        private var app: Application? = null
        /** Chế độ nguồn tiếng (Cau.cheDo) — giữ qua lần tắt mở app. */
        fun luuCheDoTieng(v: String) { app?.getSharedPreferences("tivi", android.content.Context.MODE_PRIVATE)?.edit()?.putString("cheDo", v)?.apply() }
    }

    private var agent: AppAgent? = null

    override fun onCreate() {
        super.onCreate()
        RobotHelper.ketNoi(this)
        Giong.nap(this)
        KhachHang.nap(this)
        app = this
        Cau.cheDo = getSharedPreferences("tivi", MODE_PRIVATE).getString("cheDo", "tu-dong") ?: "tu-dong"
        MayChu.batDau(this)      // tivi cùng Wi-Fi mở http://<ip-robot>:8090

        runCatching {
            agent = object : AppAgent(this) {
                override fun onCreate() {
                    isEnableVoiceBar = false
                    isDisablePlan = true
                    isEnableWakeFree = false
                    runCatching { AgentCore.enableWakeupMode(false) }
                    Log.d(TAG, "Công tắc hãng: voiceBar=$isEnableVoiceBar · disablePlan=$isDisablePlan · wakeFree=$isEnableWakeFree")
                    setPersona("Bạn là Nova, robot lễ tân của Roboworld tại gian hàng triển lãm. Bạn không tự trả lời — mọi nội dung do ứng dụng đọc.")
                    setObjective("Không tự trả lời khách. Ứng dụng đã có toàn bộ nội dung.")
                    setOnTranscribeListener(object : OnTranscribeListener {
                        override fun onASRResult(t: Transcription): Boolean = false
                        override fun onTTSResult(t: Transcription): Boolean {
                            /* AgentOS tự nói (đo 335 lượt ở app Tây Ninh) — cắt ngay từ mẩu đầu, ở MỌI màn. */
                            /* App KHÔNG BAO GIỜ nói qua AgentOS (mọi câu là MP3 Yuna qua MediaPlayer),
                               nên bất kỳ chữ nào tới đây đều là AgentOS tự nói → cắt ngay, không xét. */
                            Log.w(TAG, "AgentOS tự nói '${t.text}' — cắt")
                            runCatching { AgentCore.stopTTS() }
                            return false
                        }
                    })
                    agentOk = true
                }
                override fun onExecuteAction(action: Action, params: Bundle?): Boolean = false
            }
        }.onFailure { Log.w(TAG, "Agent SDK không khởi tạo được: ${it.message} — app vẫn chạy, chỉ mất bốn công tắc") }
    }
}
