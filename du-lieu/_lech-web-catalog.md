# Lệch số liệu giữa roboworld.com.vn và product-catalog.md (đối chiếu 07/09/2026)

App theo **catalog** (`~/.claude/skills/roboworld-seo-content/references/product-catalog.md`) —
nguồn thông số duy nhất theo CLAUDE.md. Website ghi nhiều dòng dạng "~" hoặc lệch, anh Trường nên
nhờ bộ phận web sửa lại:

| Model | Website ghi | Catalog ghi | App dùng |
|---|---|---|---|
| PuduBot 2 | 55 kg · 4 khay · 10–12 giờ | 39 kg · 3 khay (13 kg/khay) · 15 giờ không tải / 12 giờ đầy tải · lối đi 80 cm | catalog |
| PUDU T600 | trọng lượng **12 kg** (lỗi gõ) | 112 kg | catalog |
| KettyBot Pro | 450×450×1120 · tải ~30 kg · 8–10 giờ · sạc 4–5 giờ | 43,5×45×112 cm · 10 kg/khay · 9 giờ · sạc 3,5 giờ · lối đi 52 cm | catalog |
| BellaBot | 10–12 giờ | 12–24 giờ (pin tháo rời) · lối đi 70 cm | catalog |
| GreetingBot Nova | cao ~1,5 m · 8–10 giờ · "màn hình 14 inch" | 55,8×52,5×135 cm · 12–14 giờ | catalog, **không ghi cỡ màn hình** (memory: Nova không có màn 14", chưa có số thật) |
| Gausium Beetle | 4–6 giờ · 0,5–1,0 m/s · "600 m²/h · 420 mm" | 5 giờ · 1,2 m/s · 3.240 m²/h · 75 cm | catalog |
| Gausium Omnie | 6–8 giờ · sạc 3–4 giờ | chà 3 giờ / quét-lau 8 giờ · sạc 2 giờ | catalog |
| Gausium Phantas | 5–8 giờ · sạc 3–4 giờ | chà 4,5 / hút 4 / quét 14 / lau 10 giờ · sạc 2 giờ | catalog |
| MT1 Vac / MT1 Max / SH1 / T150 / T300 | chỉ ghi "~", "lớn", "rất cao" | số cụ thể | catalog |
| BG1 | lối đi 85 cm (web) | catalog không ghi lối đi | không đưa lối đi vào app |
| Hotline | 0986 900 161 / 098 113 6986 | CLAUDE.md: **0866 153 946** duy nhất | 0866 153 946 |

Nội dung mô tả, tính năng, ứng dụng thực tế → lấy từ website (`du-lieu/website/*.txt`).
