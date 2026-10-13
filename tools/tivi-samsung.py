# -*- coding: utf-8 -*-
"""ĐIỀU KHIỂN TIVI SAMSUNG (Tizen) QUA MẠNG — bản Python, WebSocket tự viết tay, không thư viện ngoài.

    python tools/tivi-samsung.py <ip-tivi> --ghep                 ghép đôi (tivi hỏi Cho phép lần đầu), lưu token
    python tools/tivi-samsung.py <ip-tivi> --mo <url>             mở trình duyệt tivi thẳng vào url
    python tools/tivi-samsung.py <ip-tivi> --phim KEY_ENTER [..]  gửi phím điều khiển
    python tools/tivi-samsung.py <ip-tivi> --go "chữ"             gõ chữ vào ô đang chọn (thanh địa chỉ)

Vì sao có bản này cạnh tivi-samsung.mjs: WebSocket dựng sẵn của Node tự gửi kèm
"Sec-WebSocket-Extensions: permessage-deflate" — máy chủ remote của tivi Samsung nhận kết nối rồi IM LẶNG
mãi, không hiện hộp Cho phép, không báo lỗi (đo 07/10/2026 trên UA75AU8000). Bản này bắt tay tối giản.
Token lưu ở cau-hinh/tivi-samsung.json (gitignore).
"""
import base64, json, os, socket, ssl, struct, sys, time

try: sys.stdout.reconfigure(encoding="utf-8")
except Exception: pass

HERE = os.path.dirname(os.path.abspath(__file__))
TEP = os.path.join(os.path.dirname(HERE), "cau-hinh", "tivi-samsung.json")
TEN = base64.b64encode("Robot Roboworld".encode()).decode()
TRINH_DUYET = ["3202010022079", "org.tizen.browser"]   # AU8000 2021 = 3202010022079 "Browser" (⚠ 3201907018807 là Netflix)


def doc_luu():
    try: return json.load(open(TEP, encoding="utf-8"))
    except Exception: return {}


class WS:
    def __init__(self, ip, cong, token, cho):
        s = socket.create_connection((ip, cong), timeout=cho)
        if cong == 8002:
            c = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT); c.check_hostname = False; c.verify_mode = ssl.CERT_NONE
            s = c.wrap_socket(s, server_hostname=ip)
        duong = "/api/v2/channels/samsung.remote.control?name=" + TEN + ("&token=" + token if token else "")
        khoa = base64.b64encode(os.urandom(16)).decode()
        s.sendall(("GET %s HTTP/1.1\r\nHost: %s:%d\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                   "Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\n\r\n" % (duong, ip, cong, khoa)).encode())
        dau = b""
        while b"\r\n\r\n" not in dau:
            m = s.recv(1);
            if not m: raise IOError("tivi đóng kết nối lúc bắt tay")
            dau += m
        dong = dau.split(b"\r\n")[0].decode(errors="replace")
        print("  bắt tay cổng %d: %s" % (cong, dong))
        if " 101 " not in dong: raise IOError(dau.decode(errors="replace"))
        self.s = s

    def gui(self, o):
        b = json.dumps(o).encode(); m = os.urandom(4)
        h = bytes([0x81]) + (bytes([0x80 | len(b)]) if len(b) < 126 else bytes([0x80 | 126]) + struct.pack(">H", len(b)))
        self.s.sendall(h + m + bytes(x ^ m[i % 4] for i, x in enumerate(b)))

    def _du(self, n):
        b = b""
        while len(b) < n:
            m = self.s.recv(n - len(b))
            if not m: raise IOError("tivi đóng kết nối")
            b += m
        return b

    def nhan(self):
        a, b = self._du(2)
        n = b & 0x7F
        if n == 126: n = struct.unpack(">H", self._du(2))[0]
        elif n == 127: n = struct.unpack(">Q", self._du(8))[0]
        d = self._du(n)
        if a & 0x0F == 8: raise IOError("tivi đóng kênh: %r" % d)
        return d.decode(errors="replace")


def noi(ip, cho=90):
    luu = doc_luu()
    loi = None
    for cong in (8002, 8001):
        try:
            w = WS(ip, cong, luu.get(ip), cho)
            print("  chờ tivi trả lời (lần đầu: bấm CHO PHÉP trên tivi, %d giây)…" % cho)
            w.s.settimeout(cho)
            while True:
                m = json.loads(w.nhan())
                ev = m.get("event")
                if ev == "ms.channel.connect":
                    tk = (m.get("data") or {}).get("token")
                    if tk:
                        luu[ip] = tk; os.makedirs(os.path.dirname(TEP), exist_ok=True)
                        json.dump(luu, open(TEP, "w", encoding="utf-8"), indent=1)
                    print("  ✔ đã nối tivi qua cổng %d%s" % (cong, " · đã lưu token" if tk else ""))
                    w.s.settimeout(10)
                    return w
                if ev == "ms.channel.unauthorized": raise IOError("tivi TỪ CHỐI thiết bị này")
                print("  tivi:", json.dumps(m, ensure_ascii=False)[:200])
        except Exception as e:
            loi = e; print("  ✘ cổng %d: %s" % (cong, e))
    raise SystemExit("Không nối được tivi: %s" % loi)


def phim(w, k):
    w.gui({"method": "ms.remote.control", "params": {"Cmd": "Click", "DataOfCmd": k, "Option": "false", "TypeOfRemote": "SendRemoteKey"}})


def main():
    a = sys.argv[1:]
    if len(a) < 2: print(__doc__); return
    ip, lenh = a[0], a[1]
    w = noi(ip)
    if lenh == "--mo":
        for app in TRINH_DUYET:
            w.gui({"method": "ms.channel.emit", "params": {"event": "ed.apps.launch", "to": "host",
                   "data": {"appId": app, "action_type": "NATIVE_LAUNCH", "metaTag": a[2]}}})
            print("  mở", app, "→", a[2]); time.sleep(2)
            break
    elif lenh == "--phim":
        for k in a[2:] or ["KEY_ENTER"]: phim(w, k); print("  phím", k); time.sleep(0.6)
    elif lenh == "--go":
        w.gui({"method": "ms.remote.control", "params": {"Cmd": base64.b64encode(a[2].encode()).decode(),
               "DataOfCmd": "base64", "TypeOfRemote": "SendInputString"}})
        print("  gõ:", a[2])
    time.sleep(1)
    try:
        w.s.settimeout(2); print("  tivi:", w.nhan()[:200])
    except Exception: pass


main()
