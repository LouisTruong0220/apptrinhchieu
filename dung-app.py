# -*- coding: utf-8 -*-
"""Ghép khung-app.html + du-lieu/san-pham.json → demo/index.html tự chứa, rồi rải
sang android/app/src/main/assets/ (cùng ảnh, video, biểu cảm, giọng).
(App robot sự kiện Roboworld — Tuần lễ Công nghiệp Công nghệ Việt Nam 2026)

Đầu ra:
  demo/index.html          bản sạch, giống hệt trong APK — nháy đúp là chạy (không robot)
  demo/thu-nghiem.html     BẢN THỬ có giả lập robot (gia-lap-robot.js) — thử được du hành, giọng, form
  demo/tivi.html           TRANG TIVI — robot phát ở http://<ip>:8090 (MayChu.kt), cùng dữ liệu sản phẩm
  demo/anh|video|bieu-cam  media rời (không nhúng base64 — WebView phải phân tích cả file mới vẽ)
  du-lieu/_loi-thoai-sinh.json   MỌI câu robot đọc, id → văn bản — đầu vào của tools/sinh-giong.py
  android/.../assets/      index.html + anh + video + bieu-cam + giong

Cổng chất lượng — không đạt là DỪNG, không sinh app:
  · câu robot đọc còn CHỮ SỐ (giọng máy đọc "18,5" thành "mười tám phẩy năm" sai nhịp)
  · thiếu ảnh 1.jpg của một sản phẩm · khai video mà thiếu file · video không phải h264
  · điểm bản đồ có dấu / khoảng trắng thừa
Chạy:  python dung-app.py
"""
import base64, io, json, os, re, shutil, subprocess, sys
try: sys.stdout.reconfigure(encoding="utf-8")
except Exception: pass

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
KHUNG = os.path.join(HERE, "khung-app.html")
KHUNG_TIVI = os.path.join(HERE, "khung-tivi.html")
DULIEU = os.path.join(HERE, "du-lieu", "san-pham.json")
MEDIA = os.path.join(HERE, "media")
BIEUCAM = os.path.join(HERE, "bieu-cam")
GIONG = os.path.join(HERE, "giong", "mp3")
NHAC = os.path.join(HERE, "nhac")
DEMO = os.path.join(HERE, "demo")
ASSETS = os.path.join(HERE, "android", "app", "src", "main", "assets")
# Logo: bản chép sẵn trong app (media/logo-roboworld.png) để kho GitHub tự build được, không cần workspace
LOGO = os.path.join(HERE, "media", "logo-roboworld.png")
if not os.path.isfile(LOGO): LOGO = os.path.join(ROOT, "00-brand", "design-kit", "assets", "logos", "[RBW] Logo-đỏ-xóa-phông-ngang.png")
RA_LOI = os.path.join(HERE, "du-lieu", "_loi-thoai-sinh.json")
os.makedirs(DEMO, exist_ok=True)

khung = io.open(KHUNG, encoding="utf-8").read()
data = io.open(DULIEU, encoding="utf-8").read()
d = json.loads(data)

# ── Gom mọi câu robot đọc: id → text (phải KHỚP cách đặt id trong khung-app.html) ──
loi = {}
for k, v in (d.get("loi") or {}).items(): loi["ui-" + k] = v
dt = d.get("dung_thu") or {}
loi["ui-dung_thu"] = dt.get("doc", ""); loi["ui-doc_form"] = dt.get("doc_form", ""); loi["ui-doc_cam_on"] = dt.get("doc_cam_on", "")
for i, c in enumerate((d.get("du_hanh") or {}).get("cau_chao") or []): loi["dh-%d" % i] = c
for l in d.get("linh_vuc") or []: loi["lv-" + l["ma"]] = l.get("doc", "")
for s in d.get("san_pham") or []:
    for t in ("gioi_thieu", "thong_so", "phu_hop"): loi["sp-%s-%s" % (s["ma"], t)] = (s.get("doc") or {}).get(t, "")
loi = {k: v.strip() for k, v in loi.items() if v and v.strip()}

# ── CỔNG CHẤT LƯỢNG ──────────────────────────────────────────────────────
err = []
for k, v in loi.items():
    if re.search(r"\d", v): err.append("câu đọc '%s' còn chữ số: %s" % (k, re.findall(r"\S*\d\S*", v)[:3]))
for diem in (d.get("du_hanh") or {}).get("diem") or []:
    if diem != diem.strip() or "  " in diem: err.append("điểm bản đồ có khoảng trắng thừa: '%s'" % diem)
    if re.search(r"[^\x00-\x7F]", diem): err.append("điểm bản đồ có dấu tiếng Việt: '%s'" % diem)
for s in d.get("san_pham") or []:
    ma = s["ma"]
    if not os.path.isfile(os.path.join(MEDIA, "anh", ma, "1.jpg")): err.append("thiếu media/anh/%s/1.jpg — chạy tools/chuan-bi-media.py" % ma)
    so = len([f for f in os.listdir(os.path.join(MEDIA, "anh", ma)) if f.endswith(".jpg")]) if os.path.isdir(os.path.join(MEDIA, "anh", ma)) else 0
    s["so_anh"] = so
    s["video"] = os.path.isfile(os.path.join(MEDIA, "video", ma + ".mp4"))
for thu_muc in (os.path.join(MEDIA, "video"), BIEUCAM):
    if not os.path.isdir(thu_muc): continue
    for v in sorted(os.listdir(thu_muc)):
        if not v.endswith(".mp4"): continue
        try:
            ma = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=codec_name", "-of", "csv=p=0",
                                 os.path.join(thu_muc, v)], capture_output=True, text=True, timeout=30).stdout.strip()
            if ma and ma != "h264": err.append("%s: codec '%s' — WebView robot chỉ phát h264" % (v, ma))
            am = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a", "-show_entries", "stream=codec_name", "-of", "csv=p=0",
                                 os.path.join(thu_muc, v)], capture_output=True, text=True, timeout=30).stdout.strip()
            if am: err.append("%s: còn TIẾNG (%s) — sẽ đè lên giọng robot" % (v, am))
        except Exception: pass
if not os.path.isfile(LOGO): err.append("thiếu logo: " + LOGO)
# Tivi treo giữa gian hàng: trang tivi KHÔNG được đọc ô form, lớp web KHÔNG được gửi giá trị form sang.
khung_tivi = io.open(KHUNG_TIVI, encoding="utf-8").read() if os.path.isfile(KHUNG_TIVI) else ""
if not khung_tivi: err.append("thiếu khung-tivi.html")
for cam in ("raw[", "giaTri(", "kq.ten", "kq.sdt", "cong_ty", "sdt"):
    if cam in khung_tivi: err.append("khung-tivi.html chạm tới dữ liệu khách ('%s') — tivi không được hiện thông tin khách" % cam)
for m in re.finditer(r"tv\(\{([^}]*)\}", khung):
    if re.search(r"raw|giaTri|kq\.|sdt|cong_ty|\bten\b", m.group(1)): err.append("khung-app.html gửi dữ liệu khách sang tivi: tv({%s})" % m.group(1))
if not os.path.isfile(os.path.join(NHAC, "nen-du-hanh.mp3")): err.append("thiếu nhac/nen-du-hanh.mp3 — xem nhac/NGUON.md")
if err:
    print("✘ DỪNG — dữ liệu chưa đạt:")
    for x in err: print("   · " + x)
    raise SystemExit(1)

# ── Ghép ────────────────────────────────────────────────────────────────
logo64 = "data:image/png;base64," + base64.b64encode(open(LOGO, "rb").read()).decode()
data_nhung = json.dumps(d, ensure_ascii=False).replace("</", "<\\/")
MOC = "/*__DU_LIEU__*/"
if MOC not in khung: raise SystemExit("Không thấy mốc " + MOC)
html = khung.replace(MOC, "window.DU_LIEU=" + data_nhung + ";").replace("__LOGO__", logo64)
RA = os.path.join(DEMO, "index.html")
io.open(RA, "w", encoding="utf-8").write(html)

GIALAP = os.path.join(HERE, "gia-lap-robot.js")
RA_THU = None
if os.path.isfile(GIALAP):
    gl = io.open(GIALAP, encoding="utf-8").read()
    html_thu = html.replace("</script>\n<script>\n'use strict';", "</script>\n<script>\n" + gl + "\n</script>\n<script>\n'use strict';", 1)
    if html_thu == html: raise SystemExit("Không tìm thấy chỗ chèn giả lập")
    RA_THU = os.path.join(DEMO, "thu-nghiem.html")
    io.open(RA_THU, "w", encoding="utf-8").write(html_thu.replace("<title>", "<title>[BẢN THỬ] ", 1))

RA_TIVI = os.path.join(DEMO, "tivi.html")
if MOC not in khung_tivi: raise SystemExit("khung-tivi.html không có mốc " + MOC)
io.open(RA_TIVI, "w", encoding="utf-8").write(khung_tivi.replace(MOC, "window.DU_LIEU=" + data_nhung + ";").replace("__LOGO__", logo64))

io.open(RA_LOI, "w", encoding="utf-8").write(json.dumps(loi, ensure_ascii=False, indent=1))

def rai(nguon, dich, duoi):
    """Copy đè từng file, không xoá hàng loạt (quy tắc 2)."""
    if not os.path.isdir(nguon): return 0
    os.makedirs(dich, exist_ok=True); n = 0
    for goc, _, fs in os.walk(nguon):
        rel = os.path.relpath(goc, nguon)
        for f in fs:
            if not f.lower().endswith(duoi): continue
            dd = os.path.join(dich, rel) if rel != "." else dich
            os.makedirs(dd, exist_ok=True)
            src, dst = os.path.join(goc, f), os.path.join(dd, f)
            if not os.path.isfile(dst) or os.path.getsize(dst) != os.path.getsize(src) or int(os.path.getmtime(dst)) != int(os.path.getmtime(src)): shutil.copy2(src, dst)
            n += 1
    return n

for dich in (DEMO, ASSETS) if os.path.isdir(os.path.dirname(ASSETS)) else (DEMO,):
    if dich == ASSETS: shutil.copy2(RA, os.path.join(ASSETS, "index.html")); shutil.copy2(RA_TIVI, os.path.join(ASSETS, "tivi.html"))
    a = rai(os.path.join(MEDIA, "anh"), os.path.join(dich, "anh"), (".jpg",))
    v = rai(os.path.join(MEDIA, "video"), os.path.join(dich, "video"), (".mp4",))
    b = rai(BIEUCAM, os.path.join(dich, "bieu-cam"), (".mp4",))
    g = rai(GIONG, os.path.join(dich, "giong"), (".mp3",))
    n = rai(NHAC, os.path.join(dich, "nhac"), (".mp3",))
    print("→ %s  (%d ảnh · %d video · %d biểu cảm · %d giọng · %d nhạc)" % (dich, a, v, b, g, n))

thieu = [k for k in loi if not os.path.isfile(os.path.join(GIONG, k + ".mp3"))]
print("✔ %d sản phẩm · %d lĩnh vực · %d câu đọc (%s ký tự) · thiếu MP3: %d câu → robot IM câu đó (không dùng giọng máy)"
      % (len(d["san_pham"]), len(d["linh_vuc"]), len(loi), format(sum(len(v) for v in loi.values()), ","), len(thieu)))
print("→ %s (%.2f MB)" % (RA, os.path.getsize(RA) / 1048576))
if RA_THU: print("→ %s — BẢN THỬ có giả lập robot" % RA_THU)
print("→ %s — TRANG TIVI (robot phát ở cổng 8090)" % RA_TIVI)
if thieu: print("   Sinh giọng: python tools\\sinh-giong.py   (--nghe để xem trước)")
