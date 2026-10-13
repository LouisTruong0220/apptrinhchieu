package vn.roboworld.congnghiep

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ainirobot.coreservice.client.ApiListener
import com.ainirobot.coreservice.client.Definition
import com.ainirobot.coreservice.client.RobotApi
import com.ainirobot.coreservice.client.listener.ActionListener
import com.ainirobot.coreservice.client.listener.CommandListener
import com.ainirobot.coreservice.client.listener.TextListener
import com.ainirobot.coreservice.client.module.ModuleCallbackApi
import com.ainirobot.coreservice.client.speech.SkillApi
import com.ainirobot.coreservice.client.speech.entity.TTSEntity

/**
 * Lớp bọc RobotApi — gom mọi thứ liên quan tới phần cứng robot vào một chỗ.
 *
 * Vì sao cần lớp này (theo tài liệu RobotApi của OrionStar):
 *  - App chỉ được uỷ quyền dùng SDK khi ĐÃ kết nối RobotOS VÀ giao diện đang ở tiền cảnh.
 *  - Khi có sự kiện hệ thống (dừng khẩn cấp, pin yếu, OTA, lỗi phần cứng) thì app bị TREO
 *    (onSuspend) và mọi lệnh gọi API mất tác dụng cho tới khi onRecovery.
 *  - Điều kiện tiên quyết của MỌI lệnh dẫn đường: đã dựng bản đồ, đã định vị thành công,
 *    LiDAR đang bật. Thiếu một trong ba thì startNavigation luôn lỗi.
 */
object RobotHelper {

    private const val TAG = "RobotHelper"
    private val main = Handler(Looper.getMainLooper())

    /** reqId chỉ dùng để lần log. Tài liệu khuyến nghị dùng biến static tự tăng. */
    private var reqId = 1000
    private fun nextReqId(): Int = ++reqId

    @Volatile var daKetNoi = false; private set
    @Volatile var biTreo = false; private set

    /** Được gọi khi quyền điều khiển bị thu hồi / khôi phục, để màn hình tự cập nhật. */
    var onTrangThaiDoi: ((ketNoi: Boolean, treo: Boolean) -> Unit)? = null

    // ───────────────────────── Kết nối ─────────────────────────

    /** Giọng nói nằm ở SkillApi, KHÁC RobotApi — phải kết nối riêng, dễ quên. */
    private val skill = SkillApi()
    @Volatile private var skillSanSang = false

    fun ketNoi(context: Context) {
        skill.connectApi(context, object : ApiListener {
            override fun handleApiConnected() { skillSanSang = true; Log.d(TAG, "Đã kết nối SkillApi (giọng nói)") }
            override fun handleApiDisconnected() { skillSanSang = false }
            override fun handleApiDisabled() { skillSanSang = false; Log.w(TAG, "SkillApi bị từ chối") }
        })

        RobotApi.getInstance().connectServer(context, object : ApiListener {
            override fun handleApiConnected() {
                daKetNoi = true
                biTreo = false
                Log.d(TAG, "Đã kết nối RobotOS")
                // Đặt callback nhận chỉ lệnh giọng nói và sự kiện hệ thống
                RobotApi.getInstance().setCallback(object : ModuleCallbackApi() {
                    override fun onSendRequest(
                        reqId: Int, reqType: String?, reqText: String?, reqParam: String?
                    ): Boolean {
                        Log.d(TAG, "onSendRequest type=$reqType text=$reqText")
                        // Trả false để hệ thống xử lý tiếp như bình thường
                        return false
                    }

                    override fun onSuspend() {
                        biTreo = true
                        Log.w(TAG, "BỊ TREO — hệ thống thu hồi quyền điều khiển")
                        baoTrangThai()
                    }

                    override fun onRecovery() {
                        biTreo = false
                        Log.d(TAG, "Đã khôi phục quyền điều khiển")
                        baoTrangThai()
                    }
                })
                baoTrangThai()
            }

            override fun handleApiDisconnected() {
                daKetNoi = false
                Log.w(TAG, "Mất kết nối RobotOS")
                baoTrangThai()
            }

            override fun handleApiDisabled() {
                daKetNoi = false
                Log.w(TAG, "RobotOS từ chối — app chưa được uỷ quyền. " +
                        "Nhớ khởi động app từ RobotOS Home, không chạy thẳng từ Android Studio.")
                baoTrangThai()
            }
        })
    }

    private fun baoTrangThai() = main.post { onTrangThaiDoi?.invoke(daKetNoi, biTreo) }

    // ───────────────────────── Đọc thành tiếng ─────────────────────────

    /**
     * Đọc một đoạn bằng giọng của robot.
     *
     * ⚠ PHẢI dùng bản playText(TTSEntity, …), KHÔNG dùng playText(String, …).
     *   Bản nhận String đã lỗi thời: ROM trên máy Nova đọc tham số đó bằng Gson và mong
     *   một đối tượng JSON, đưa chuỗi trần vào là nó ném
     *   "JsonSyntaxException: Expected BEGIN_OBJECT but was STRING" — robot IM RE hoàn toàn,
     *   app không hề báo lỗi vì ngoại lệ xảy ra bên phía dịch vụ, không bắn ngược về.
     *   Trình biên dịch chỉ cảnh báo "deprecated", rất dễ bỏ qua.
     *
     * @param khiXong gọi khi đọc xong hoặc bị lỗi — để giao diện đổi lại nút.
     */
    fun doc(vanBan: String, khiXong: () -> Unit) {
        if (!skillSanSang) {
            Log.w(TAG, "SkillApi chưa sẵn sàng, bỏ qua lệnh đọc")
            main.post { khiXong() }
            return
        }
        Log.d(TAG, "Đọc: ${vanBan.take(60)}…")
        skill.playText(TTSEntity(vanBan), object : TextListener() {
            override fun onStart() { Log.d(TAG, "Bắt đầu đọc") }
            override fun onComplete() { Log.d(TAG, "Đọc xong"); main.post { khiXong() } }
            override fun onError() { Log.w(TAG, "Lỗi khi đọc"); main.post { khiXong() } }
            override fun onStop() { main.post { khiXong() } }
        })
    }

    fun dungDoc() {
        if (skillSanSang) runCatching { skill.stopTTS() }
    }

    // ───────────────────────── Định vị ─────────────────────────

    /**
     * Kiểm tra robot đã định vị trên bản đồ chưa.
     * Chưa định vị thì mọi lệnh dẫn đường đều trả lỗi ERROR_NOT_ESTIMATE (-116).
     */
    fun daDinhVi(ketQua: (Boolean) -> Unit) {
        if (!sanSang()) { main.post { ketQua(false) }; return }
        RobotApi.getInstance().isRobotEstimate(nextReqId(), object : CommandListener() {
            override fun onResult(result: Int, message: String?) {
                main.post { ketQua("true" == message) }
            }
        })
    }

    /** Lấy danh sách điểm đã đặt trên bản đồ hiện tại — dùng để tự kiểm khi lắp đặt. */
    fun layDanhSachDiem(ketQua: (String?) -> Unit) {
        if (!sanSang()) { main.post { ketQua(null) }; return }
        RobotApi.getInstance().getPlaceList(nextReqId(), object : CommandListener() {
            override fun onResult(result: Int, message: String?) {
                main.post { ketQua(message) }
            }
        })
    }

    // ───────────────────────── Dẫn đường ─────────────────────────

    /**
     * Dẫn khách tới một điểm đã đặt tên trên bản đồ.
     *
     * @param tenDiem  tên điểm, phải khớp TUYỆT ĐỐI với tên trong Công cụ bản đồ
     * @param khiToiNoi  gọi khi tới nơi thành công
     * @param khiLoi     gọi kèm câu giải thích tiếng Việt đã dịch sẵn từ mã lỗi
     * @param khiCapNhat gọi khi có sự kiện dọc đường (gặp vật cản, vật cản đã dọn…)
     */
    fun dieuHuongToi(
        tenDiem: String,
        khiToiNoi: () -> Unit,
        khiLoi: (String) -> Unit,
        khiCapNhat: (String) -> Unit = {}
    ) {
        if (!sanSang()) { main.post { khiLoi("Robot chưa sẵn sàng — chưa kết nối RobotOS.") }; return }

        // coordinateDeviation 0.2 m và time 30 s là giá trị tài liệu hãng khuyến nghị.
        // Ghi lại MÃ TRẢ VỀ: nếu lệnh bị từ chối ngay thì ActionListener không bao giờ
        // được gọi, không ghi mã này thì robot đứng im mà không có lấy một dòng log.
        val ma = RobotApi.getInstance().startNavigation(
            nextReqId(), tenDiem, 0.2, 30_000L,
            object : ActionListener() {

                override fun onResult(status: Int, response: String?) {
                    main.post {
                        if (status == Definition.RESULT_OK && "true" == response) khiToiNoi()
                        else khiLoi("Dẫn đường không thành công. Mời quý vị chờ nhân viên hỗ trợ.")
                    }
                }

                override fun onError(errorCode: Int, errorString: String?) {
                    main.post { khiLoi(dichMaLoi(errorCode)) }
                }

                /**
                 * Dọc đường robot bắn ra rất nhiều trạng thái. Ba nhóm phải phân biệt rõ:
                 *  - tạm thời (tránh vật cản)  -> chỉ thông báo, vẫn đang đi
                 *  - HỎNG HẲN (không tìm được đường, ra ngoài bản đồ) -> phải báo THẤT BẠI,
                 *    nếu không nút cứ đứng ở "đang đi" mãi mà robot thì không nhúc nhích
                 *  - đã tới nơi
                 */
                override fun onStatusUpdate(status: Int, data: String?) {
                    Log.d(TAG, "Dẫn đường onStatusUpdate: status=$status data=$data")
                    when (status) {
                        // ── hỏng hẳn ──
                        Definition.STATUS_NAVI_GLOBAL_PATH_FAILED -> main.post {
                            khiLoi("Tôi không tìm được đường tới chỗ đó. Có thể lối đi đang bị chắn, " +
                                   "hoặc tôi đang đứng ngoài khu vực đã quét bản đồ. " +
                                   "Mời quý vị đi theo biển chỉ dẫn giúp tôi ạ.")
                        }
                        Definition.STATUS_NAVI_OUT_MAP -> main.post {
                            khiLoi("Tôi đang ở ngoài vùng bản đồ nên chưa tự đi được. " +
                                   "Nhờ nhân viên đưa tôi về khu vực phục vụ giúp ạ.")
                        }
                        Definition.STATUS_GOAL_OCCLUDED -> main.post {
                            khiCapNhat("Chỗ đứng ở quầy đang bị chắn, tôi chờ một chút.")
                        }
                        // ── tạm thời ──
                        Definition.STATUS_NAVI_AVOID,
                        Definition.STATUS_NAVI_OBSTACLES_AVOID -> main.post {
                            khiCapNhat("Phía trước đang có vật cản, tôi chờ một chút.")
                        }
                        Definition.STATUS_NAVI_AVOID_END,
                        Definition.STATUS_NAVI_OBSTACLES_DISAPPEAR -> main.post {
                            khiCapNhat("Đường đã thông, tôi đi tiếp.")
                        }
                        Definition.STATUS_DEST_NEAR -> main.post {
                            khiCapNhat("Sắp tới nơi rồi ạ.")
                        }
                        else -> return
                    }
                }
            }
        )
        Log.d(TAG, "startNavigation('$tenDiem') trả mã = $ma")
        if (ma < 0) {
            main.post { khiLoi("Robot chưa nhận lệnh đi (mã $ma). Nhờ nhân viên kỹ thuật kiểm tra giúp ạ.") }
        }
    }

    fun dungDieuHuong() {
        if (!sanSang()) return
        RobotApi.getInstance().stopNavigation(nextReqId())
    }

    /** Dịch mã lỗi dẫn đường thành câu người thường đọc được. */
    private fun dichMaLoi(code: Int): String = when (code) {
        Definition.ERROR_NOT_ESTIMATE ->
            "Robot chưa định vị được trên bản đồ. Nhờ nhân viên kỹ thuật định vị lại giúp."
        Definition.ERROR_DESTINATION_NOT_EXIST ->
            "Chưa có điểm này trên bản đồ. Nhờ nhân viên kỹ thuật đặt điểm giúp."
        Definition.ERROR_IN_DESTINATION ->
            "Mình đang ở ngay tại vị trí đó rồi ạ."
        Definition.ERROR_DESTINATION_CAN_NOT_ARRAIVE ->
            "Đường đi đang bị chắn nên tôi không tới được. Mời quý vị đi theo biển chỉ dẫn."
        Definition.ACTION_RESPONSE_ALREADY_RUN,
        Definition.ACTION_RESPONSE_REQUEST_RES_ERROR ->
            "Tôi đang bận một việc khác. Mời quý vị chờ tôi xong việc rồi bấm lại giúp ạ."
        else -> "Có trục trặc khi dẫn đường (mã $code). Mời quý vị chờ nhân viên hỗ trợ."
    }

    private fun sanSang(): Boolean = lyDoChuaSanSang() == null

    /**
     * Vì sao chưa điều khiển được robot — trả null nghĩa là sẵn sàng.
     * Viết bằng tiếng người thường để đọc thẳng cho dân nghe, không phải mã lỗi.
     */
    fun lyDoChuaSanSang(): String? = when {
        !daKetNoi -> {
            Log.w(TAG, "Chưa kết nối RobotOS")
            "Tôi chưa kết nối được với hệ thống của robot. Nhờ quý vị báo cán bộ kỹ thuật giúp ạ."
        }
        biTreo -> {
            Log.w(TAG, "Đang bị treo — app chưa được uỷ quyền hoặc hệ thống đang bận")
            "Lúc này tôi chưa được phép tự đi. Nhờ nhân viên mở ứng dụng từ màn hình chính " +
            "của robot giúp ạ."
        }
        else -> null
    }
}
