# -*- coding: utf-8 -*-
"""GIẢ LẬP MÁY CHỦ TIVI CỦA ROBOT (MayChu.kt) trên máy tính — thử đồng bộ robot ↔ tivi không cần robot.

    python tools/gia-lap-may-chu.py            # cổng 8090
    → http://127.0.0.1:8090/          trang TIVI (giống hệt robot phát)
    → http://127.0.0.1:8090/robot     bản thử màn ROBOT (demo/thu-nghiem.html, có giả lập CAU)

Cùng giao thức với MayChu.kt:  GET /su-kien (SSE) · GET /trang-thai?tieng=0|1 (báo danh)
                               · GET /anh|video|bieu-cam|giong|nhac/...
Thêm hai cửa CHỈ có ở bản giả lập (robot thật đi qua cầu CAU, không qua HTTP):
    POST /gia-lap/tivi   ← CAU.tivi(json) của gia-lap-robot.js
    GET  /gia-lap/tt     → số tivi · số tivi có tiếng (để CAU.thongTinTivi giả lập trả lời)
Chạy trước `python dung-app.py` để demo/ có tivi.html.
"""
import json, os, sys, threading, time
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs, unquote

try: sys.stdout.reconfigure(encoding="utf-8")
except Exception: pass

HERE = os.path.dirname(os.path.abspath(__file__))
DEMO = os.path.join(os.path.dirname(HERE), "demo")
CONG = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
LOAI = {".html": "text/html; charset=utf-8", ".jpg": "image/jpeg", ".png": "image/png", ".mp4": "video/mp4",
        ".mp3": "audio/mpeg", ".json": "application/json", ".js": "text/javascript"}

trang_thai = "{}"
khach_sse = []              # wfile của các tivi đang nối SSE
bao_danh = {}               # (ip, user-agent) → (lần cuối, có tiếng)
khoa = threading.Lock()


def phat(chu):
    b = chu.encode("utf-8")
    with khoa:
        for w in list(khach_sse):
            try: w.write(b); w.flush()
            except Exception: khach_sse.remove(w)


def tim():
    while True:
        time.sleep(15); phat(": tim\n\n")
        han = time.time() - 6
        with khoa:
            for k in [k for k, v in bao_danh.items() if v[0] < han]: bao_danh.pop(k, None)


class XuLy(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a): pass

    def tra(self, ma, loai, b):
        self.send_response(ma)
        self.send_header("Content-Type", loai); self.send_header("Content-Length", str(len(b)))
        self.send_header("Cache-Control", "no-cache"); self.end_headers(); self.wfile.write(b)

    def do_POST(self):
        global trang_thai
        if self.path == "/gia-lap/tivi":
            n = int(self.headers.get("Content-Length") or 0)
            trang_thai = self.rfile.read(n).decode("utf-8")
            phat("data: " + trang_thai.replace("\n", " ") + "\n\n")
            return self.tra(200, "text/plain", b"ok")
        self.tra(404, "text/plain", b"")

    def do_GET(self):
        u = urlparse(self.path); duong = unquote(u.path).lstrip("/"); q = parse_qs(u.query)
        if duong == "su-kien":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8"); self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(("retry: 2000\n\ndata: " + trang_thai + "\n\n").encode("utf-8")); self.wfile.flush()
            with khoa: khach_sse.append(self.wfile)
            print("📺 tivi nối SSE — đang có", len(khach_sse))
            while self.wfile in khach_sse: time.sleep(1)
            return
        if duong == "trang-thai":
            with khoa: bao_danh[(self.client_address[0], self.headers.get("User-Agent", ""))] = (time.time(), q.get("tieng", ["0"])[0] == "1")
            return self.tra(200, "application/json", trang_thai.encode("utf-8"))
        if duong == "gia-lap/tt":
            han = time.time() - 6
            with khoa: v = [x for x in bao_danh.values() if x[0] >= han]
            return self.tra(200, "application/json", json.dumps({"soTivi": max(len(khach_sse), len(v)), "soTiviCoTieng": sum(1 for x in v if x[1])}).encode())
        if duong in ("", "tivi"): duong = "tivi.html"
        if duong == "robot": duong = "thu-nghiem.html"
        f = os.path.normpath(os.path.join(DEMO, duong))
        if not f.startswith(DEMO) or not os.path.isfile(f): return self.tra(404, "text/plain", ("Không có " + duong).encode())
        b = open(f, "rb").read()
        self.tra(200, LOAI.get(os.path.splitext(f)[1].lower(), "application/octet-stream"), b)


threading.Thread(target=tim, daemon=True).start()
print("Giả lập máy chủ tivi:  tivi  http://127.0.0.1:%d/   ·   robot  http://127.0.0.1:%d/robot" % (CONG, CONG))
ThreadingHTTPServer(("0.0.0.0", CONG), XuLy).serve_forever()
