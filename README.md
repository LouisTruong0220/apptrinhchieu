# App trình chiếu robot GreetingBot Nova lên tivi

App sự kiện của **Roboworld** chạy trên robot lễ tân **GreetingBot Nova** (OrionStar), bản **1.2**:
giới thiệu 18 dòng robot, thu đăng ký dùng thử và **chiếu đồng bộ lên tivi có tiếng**.

Robot tự mở một máy chủ web ở cổng **8090**. Tivi cùng mạng Wi-Fi mở trình duyệt vào
`http://<IP robot>:8090` là thấy đúng màn robot đang hiện (màn, sản phẩm, tab, ảnh thứ mấy), phát
giọng đọc của robot, và hiện phụ đề chữ to. Khi tivi đã bật tiếng, loa robot tự nhường; tắt tivi thì
robot tự nói lại sau khoảng 6–10 giây.

📄 **Hướng dẫn cho kỹ thuật viên (PDF):** [`tai-lieu/`](tai-lieu/) — đọc file này trước.
📦 **File cài APK:** xem mục [Releases](../../releases).

---

## Dùng nhanh (kỹ thuật viên)

1. Cài APK lên robot (cáp USB ở **đầu robot**, adb 1.0.41):
   ```
   adb install -r -d roboworld-cong-nghiep-2026-v1.2.apk
   ```
2. Mở app **từ màn hình chính RobotOS** (không dùng `adb shell am start`).
3. Ở màn chọn lĩnh vực, **bấm giữ logo 1,2 giây** → Bảng kỹ thuật → dòng **Tivi** ghi địa chỉ.
4. Trên tivi mở trình duyệt, gõ **đủ** `http://<IP>:8090` (có cả `http://` — trình duyệt Samsung tự đổi
   sang `https` nếu gõ thiếu, khi đó báo "Không tìm thấy máy chủ").
5. Bấm **OK** trên điều khiển tivi một lần để bật tiếng.

Robot và tivi phải cùng mạng, router không bật cách ly máy khách (AP isolation). Nên đặt IP cố định cho robot.

---

## Cấu trúc mã

```
khung-app.html        giao diện màn robot (WebView) — gửi trạng thái sang tivi qua CAU.tivi(json)
khung-tivi.html       trang tivi — nhận SSE, tự vẽ bố cục màn to, phát giong/<id>.mp3, phụ đề
dung-app.py           ghép khung + du-lieu/san-pham.json → demo/ + android/app/src/main/assets/
gia-lap-robot.js      giả lập cầu CAU để chạy thử trên máy tính
du-lieu/san-pham.json nguồn nội dung duy nhất (18 dòng robot, 7 lĩnh vực, câu robot đọc)
media/ bieu-cam/ giong/mp3/ nhac/   ảnh, video, biểu cảm, giọng đọc MP3, nhạc nền
android/              app Kotlin: MainActivity (WebView) · MayChu (máy chủ tivi) · Cau (cầu JS)
                      · Giong (phát MP3, nhường loa cho tivi) · KhachHang · RobotHelper
tools/                build-apk.ps1 · cai-len-robot.ps1 · gia-lap-may-chu.py · thu-tivi.mjs
                      · soi-man-hinh.mjs · tivi-samsung.py · chup-anh-tai-lieu.mjs
```

### Máy chủ tivi (`MayChu.kt`)

| Đường dẫn | Việc |
|---|---|
| `GET /` | trang tivi |
| `GET /su-kien` | luồng SSE trạng thái (nhịp tim 15 giây) |
| `GET /trang-thai?tieng=0\|1` | JSON trạng thái — tivi gọi 2 giây/lần làm **nhịp báo danh** |
| `GET /anh\|video\|bieu-cam\|giong\|nhac/…` | media, hỗ trợ Range (206) |

Ba điều đã đo trên máy thật — đừng sửa ngược:

- Robot đếm tivi "có tiếng" theo **nhịp báo danh**, không theo luồng SSE. Tivi tắt nguồn đột ngột thì
  socket không đóng, ghi vào vẫn "thành công" vào bộ đệm TCP mấy phút — robot sẽ im luôn.
- Kết nối mở đầu bằng byte `0x16` (TLS, trình duyệt tự nâng https) phải **đóng ngay**, chờ 10 giây thì
  trình duyệt không lùi về http.
- Câu đọc hết tự nhiên thì KHÔNG báo tivi im (tivi trễ ~0,3 giây, cắt là mất đuôi câu).

**Không gửi dữ liệu khách sang tivi.** `dung-app.py` có cổng chặn: trang tivi đọc ô form, hoặc lớp web
gửi họ tên / số điện thoại / công ty sang tivi → script dừng, không sinh app.

---

## Dựng và kiểm thử

```powershell
python dung-app.py                      # bắt buộc chạy trước khi build (sinh assets)
powershell -File tools/build-apk.ps1    # JDK 17 + Android SDK 34 → android/app/build/outputs/apk/debug/
python tools/gia-lap-may-chu.py 8095    # giả lập robot trên máy tính
node tools/thu-tivi.mjs                 # 32 phép thử robot ↔ tivi  (npm i playwright)
node tools/thu-tivi.mjs http://<IP>:8090   # kiểm robot thật
```

- Bản xem thử không cần robot: sau `dung-app.py`, mở `demo/thu-nghiem.html`.
- SDK OrionStar (`android/app/libs`): gói offline Agent SDK 0.4.7 của hãng — **không** dùng bản JitPack
  (ghi cứng sai tên gói dịch vụ, AgentCore im lặng không làm gì).

### Điều khiển tivi Samsung từ máy tính (tuỳ chọn)

```
python tools/tivi-samsung.py <IP-tivi> --ghep
python tools/tivi-samsung.py <IP-tivi> --mo http://<IP-robot>:8090
python tools/tivi-samsung.py <IP-tivi> --phim KEY_ENTER
```
Token ghép đôi lưu ở `cau-hinh/tivi-samsung.json` (không đưa lên git). Trên AU8000 đời 2021 trình duyệt là
app `3202010022079`; mã `3201907018807` mà tài liệu trên mạng ghi là trình duyệt thực ra là **Netflix**.

---

## Cấu hình KHÔNG có trong kho

- `telegram.json` (khoá bot Telegram nhận thông tin khách) — tuỳ chọn, xin Roboworld gửi riêng.
  Mẫu: [`cau-hinh/telegram.mau.json`](cau-hinh/telegram.mau.json). Đặt lên robot:
  `/sdcard/Android/data/vn.roboworld.congnghiep/files/telegram.json`.

Hỗ trợ: Roboworld · hotline **0866 153 946** · roboworld.com.vn
