# -*- coding: utf-8 -*-
"""ĐÓNG GÓI KHO GITHUB CÔNG KHAI github.com/LouisTruong0220/apptrinhchieu — cho phòng kỹ thuật.

    python tools/dong-goi-github.py         → kho-github/  (kho git riêng, workspace đã gitignore)

Chép ĐÚNG danh sách cần để build + chạy + đọc hướng dẫn. CỐ Ý KHÔNG chép:
  · cau-hinh/telegram.json, apk/telegram.json   (khoá bot Telegram)
  · cau-hinh/tivi-samsung.json                  (token ghép đôi tivi)
  · README.md nội bộ (có chat_id Telegram riêng + đường dẫn máy anh Trường) → dùng tools/github/README.md
  · demo/, assets đã sinh, du-lieu/website/ (99 MB trang tải về), khach-hang-tai-ve/, file APK
    (APK đưa lên mục Releases, không vào git)
CỔNG CHẶN — kho công khai, đẩy lên là nằm lại lịch sử vĩnh viễn: quét mọi file chữ trong kho-github/,
gặp khoá Telegram / chat_id / token tivi / mật khẩu PUDU / đường dẫn workspace là DỪNG.
"""
import io, json, os, re, shutil, sys
try: sys.stdout.reconfigure(encoding="utf-8")
except Exception: pass

HERE = os.path.dirname(os.path.abspath(__file__)); APP = os.path.dirname(HERE)
KHO = os.path.join(APP, "kho-github")

THU_MUC = ["media", "bieu-cam", "giong/mp3", "nhac", "tai-lieu", "android/app/libs", "android/app/src/main/java",
           "android/app/src/main/res", "android/gradle"]
TEP = ["khung-app.html", "khung-tivi.html", "dung-app.py", "gia-lap-robot.js",
       "du-lieu/san-pham.json", "du-lieu/_lech-web-catalog.md", "du-lieu/_loi-thoai-sinh.json", "giong/giong-chon.json",
       "android/build.gradle.kts", "android/settings.gradle.kts", "android/gradle.properties", "android/gradlew", "android/gradlew.bat",
       "android/app/build.gradle.kts", "android/app/src/main/AndroidManifest.xml", "android/app/src/main/assets/actionRegistry.json",
       "tools/build-apk.ps1", "tools/cai-len-robot.ps1", "tools/gia-lap-may-chu.py", "tools/thu-tivi.mjs", "tools/soi-man-hinh.mjs",
       "tools/tivi-samsung.py", "tools/chup-anh-tai-lieu.mjs", "tools/dong-goi-github.py"]
GITIGNORE = """# Sinh ra bởi dung-app.py / Gradle — không đưa lên
demo/
android/.gradle/
android/app/build/
android/build/
android/local.properties*
android/app/src/main/assets/*
!android/app/src/main/assets/actionRegistry.json
*.apk
# Khoá / token — tuyệt đối không đưa lên kho công khai
cau-hinh/*.json
!cau-hinh/telegram.mau.json
khach-hang-tai-ve/
__pycache__/
node_modules/
"""


def chep(rel):
    src, dst = os.path.join(APP, rel), os.path.join(KHO, rel)
    if os.path.isdir(src):
        for goc, dirs, fs in os.walk(src):
            dirs[:] = [d for d in dirs if d not in ("build", "_khong-dung", "__pycache__")]
            for f in fs:
                s = os.path.join(goc, f); d = os.path.join(KHO, os.path.relpath(s, APP))
                os.makedirs(os.path.dirname(d), exist_ok=True); shutil.copy2(s, d)
    elif os.path.isfile(src):
        os.makedirs(os.path.dirname(dst), exist_ok=True); shutil.copy2(src, dst)
    else:
        raise SystemExit("✘ thiếu " + rel)


os.makedirs(KHO, exist_ok=True)
for r in THU_MUC + TEP: chep(r)
shutil.copy2(os.path.join(HERE, "github", "README.md"), os.path.join(KHO, "README.md"))
io.open(os.path.join(KHO, ".gitignore"), "w", encoding="utf-8", newline="\n").write(GITIGNORE)
os.makedirs(os.path.join(KHO, "cau-hinh"), exist_ok=True)
io.open(os.path.join(KHO, "cau-hinh", "telegram.mau.json"), "w", encoding="utf-8").write(
    json.dumps({"token": "<token bot Telegram — xin Roboworld>", "chat_id": "<chat id nhận tin>"}, ensure_ascii=False, indent=1))

# ── CỔNG CHẶN ──
cam = [(re.compile(r"\b\d{8,10}:[A-Za-z0-9_-]{30,}"), "khoá bot Telegram"),
       (re.compile("55880" + "10643"), "chat_id Telegram riêng"),
       (re.compile("PUDU" + "666"), "mật khẩu kỹ thuật PUDU"),
       (re.compile(r"[A-Za-z]:[\\/]+RBW_Claude", re.I), "đường dẫn workspace máy anh Trường"),
       (re.compile(r"Downloads[\\/]+[^\"'\s]*(API|Telegram)", re.I), "đường dẫn file khoá trên máy anh Trường")]
for f in (os.path.join(APP, "cau-hinh", "tivi-samsung.json"), os.path.join(APP, "cau-hinh", "telegram.json")):
    try:
        for v in json.load(open(f, encoding="utf-8")).values():
            if isinstance(v, str) and len(v) >= 8: cam.append((re.compile(re.escape(v)), "giá trị trong " + os.path.basename(f)))
    except Exception: pass
loi = []
for goc, _, fs in os.walk(KHO):
    if os.sep + ".git" in goc: continue
    for f in fs:
        p = os.path.join(goc, f)
        if os.path.splitext(f)[1].lower() in (".jpg", ".png", ".mp4", ".mp3", ".jar", ".aar", ".pdf") and f != "gradle-wrapper.jar": continue
        if f == "gradle-wrapper.jar": continue
        s = open(p, "rb").read().decode("utf-8", "ignore")
        for re_, ten in cam:
            if re_.search(s): loi.append("%s: %s" % (os.path.relpath(p, KHO), ten))
# PDF: đọc bằng thư viện (chữ nằm trong luồng nén — tìm byte thô sẽ báo sạch giả)
try:
    import pymupdf
    for goc, _, fs in os.walk(os.path.join(KHO, "tai-lieu")):
        for f in fs:
            if f.endswith(".pdf"):
                t = "".join(pg.get_text() for pg in pymupdf.open(os.path.join(goc, f)))
                for re_, ten in cam:
                    if re_.search(t): loi.append("%s: %s" % (f, ten))
except ImportError:
    loi.append("không có pymupdf để soát PDF — pip install pymupdf")
if loi:
    print("✘ DỪNG — kho công khai sắp lộ:"); [print("   · " + x) for x in loi]; raise SystemExit(1)
n = sum(len(fs) for g, _, fs in os.walk(KHO) if os.sep + ".git" not in g)
mb = sum(os.path.getsize(os.path.join(g, f)) for g, _, fs in os.walk(KHO) if os.sep + ".git" not in g for f in fs) / 1048576
print("✔ %s — %d file · %.1f MB · cổng chặn sạch" % (KHO, n, mb))
