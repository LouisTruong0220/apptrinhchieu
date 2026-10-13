/**
 * BỘ THỬ ĐỒNG BỘ ROBOT ↔ TIVI.   node tools/thu-tivi.mjs [địa-chỉ]
 *
 * Mặc định chạy với máy chủ GIẢ LẬP (python tools/gia-lap-may-chu.py 8095 phải đang chạy):
 * mở hai trang cùng lúc — màn ROBOT (/robot) và màn TIVI (/) — bấm qua luồng trên màn robot rồi KIỂM
 * tivi theo đúng: màn · sản phẩm · tab · ảnh thứ mấy · file giọng đang phát · phụ đề, và:
 *   · tivi KHÔNG BAO GIỜ hiện họ tên / số điện thoại khách gõ trên robot
 *   · tivi bật tiếng thì robot nhường loa (chế độ Tự động); chế độ "Chỉ robot" thì tivi câm
 *   · rời màn là tivi im (luật ② của robot)
 * Truyền địa chỉ robot thật (vd http://192.168.1.76:8090) thì CHỈ kiểm trang tivi robot phát ra.
 * Ảnh chụp tivi từng bước: demo/anh-tivi/
 */
import { existsSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url)), APP = resolve(HERE, '..');
const PW = join(APP, '..', '..', 'tools', 'html-video', 'packages', 'adapter-hyperframes', 'node_modules', 'playwright', 'index.mjs');
/* Trong workspace: mượn Playwright của tools/html-video. Ngoài workspace (kho GitHub): npm i playwright */
const { chromium } = existsSync(PW) ? await import(pathToFileURL(PW).href) : await import('playwright');
const GOC = process.argv[2] || 'http://127.0.0.1:8095';
const THAT = !!process.argv[2];
const RA = join(APP, 'demo', 'anh-tivi'); mkdirSync(RA, { recursive: true });

const td = await chromium.launch({ args: ['--autoplay-policy=user-gesture-required'] });
const tivi = await td.newPage({ viewport: { width: 1920, height: 1080 } });
const loi = [];
tivi.on('pageerror', e => loi.push('LOI JS tivi: ' + e.message));
let dat = 0, hong = 0;
function kiem(ten, ok, chiTiet) { if (ok) { dat++; console.log('  ✔ ' + ten); } else { hong++; console.log('  ✘ ' + ten + (chiTiet ? ' — ' + chiTiet : '')); } }
const doi = ms => new Promise(r => setTimeout(r, ms));
const tt = () => tivi.evaluate(() => ({
  man: (document.querySelector('.man.hien') || {}).id, ten: document.querySelector('#ct-ten').textContent,
  tab: (document.querySelector('.tab .dang') || {}).id, thumb: [...document.querySelectorAll('#thumb span')].findIndex(s => s.classList.contains('dang')),
  phuDe: document.querySelector('#phu-de.hien') ? document.querySelector('#phu-de').textContent : '',
  ketNoi: document.querySelector('#ket-noi').hidden, nutTieng: !document.querySelector('#bat-tieng').hidden,
  dsSo: document.querySelectorAll('#luoi-sp .the-sp').length, chu: document.body.innerText, ...window.TIVI.trangThai() }));

await tivi.goto(GOC + '/', { waitUntil: 'load' });
await doi(1500);
let t = await tt();
console.log('\n[1] Tivi mở trang robot phát');
kiem('nối được robot (hộp "Đang nối" tắt)', t.ketNoi);
kiem('chưa bật tiếng → hiện nhắc "Bấm OK"', t.nutTieng && !t.coTieng);
await tivi.screenshot({ path: join(RA, '01-cho-chua-bat-tieng.png') });

if (THAT) {
  const media = await tivi.evaluate(async () => {
    const thu = async u => { const r = await fetch(u, { headers: { Range: 'bytes=0-99' } }); return r.status + ' ' + (r.headers.get('content-type') || ''); };
    return { anh: await thu('anh/bellabot-pro/1.jpg'), video: await thu('video/bellabot-pro.mp4'), giong: await thu('giong/dh-0.mp3'), bc: await thu('bieu-cam/cho_01.mp4') };
  });
  console.log('    media:', JSON.stringify(media));
  kiem('robot phát ảnh/video/giọng/biểu cảm (206 + đúng loại)', Object.values(media).every(x => x.startsWith('206')));
  await tivi.keyboard.press('Enter'); await doi(2500);
  t = await tt();
  kiem('bấm OK → tivi có tiếng, tắt nhắc', t.coTieng && !t.nutTieng);
  await tivi.screenshot({ path: join(RA, '02-robot-that.png') });
  console.log('\n' + (hong ? '✘' : '✔') + ' ' + dat + ' đạt · ' + hong + ' hỏng'); loi.forEach(x => console.log('  ' + x));
  await td.close(); process.exit(hong || loi.length ? 1 : 0);
}

const robot = await td.newPage({ viewport: { width: 1920, height: 1080 } });
robot.on('pageerror', e => loi.push('LOI JS robot: ' + e.message));
await robot.goto(GOC + '/robot', { waitUntil: 'load' });
await doi(1200);
const r = (f, a) => robot.evaluate(f, a);

console.log('\n[2] Người trực bấm OK trên điều khiển tivi');
await tivi.keyboard.press('Enter'); await doi(4500);       // nhịp báo danh 2 giây + giả lập hỏi 2 giây
t = await tt();
kiem('tivi có tiếng, tắt nhắc', t.coTieng && !t.nutTieng);
const ttRobot = await r(() => JSON.parse(CAU.thongTinTivi()));
kiem('robot biết có 1 tivi có tiếng → loa robot nhường (robotIm)', ttRobot.soTiviCoTieng >= 1 && ttRobot.robotIm, JSON.stringify(ttRobot));

console.log('\n[3] Khách chạm màn robot → chọn lĩnh vực');
await r(() => thoiCho()); await doi(900); t = await tt();
kiem('tivi sang màn lĩnh vực', t.man === 'tv-lv', t.man);
kiem('tivi phát đúng file giọng ui-cham_man_hinh.mp3', /giong\/ui-cham_man_hinh\.mp3$/.test(t.nguon), t.nguon);
kiem('tivi hiện phụ đề câu robot đang đọc', t.phuDe.indexOf('Xin chào quý khách') === 0, t.phuDe.slice(0, 40));
await tivi.screenshot({ path: join(RA, '03-linh-vuc.png') });

console.log('\n[4] Chọn lĩnh vực Nhà máy');
await r(() => moLinhVuc('nha-may')); await doi(900); t = await tt();
kiem('tivi sang danh sách, đúng 9 dòng của Nhà máy', t.man === 'tv-ds' && t.dsSo === 9, t.man + ' · ' + t.dsSo);
kiem('đổi câu: tivi phát lv-nha-may.mp3', /lv-nha-may\.mp3$/.test(t.nguon), t.nguon);
await tivi.screenshot({ path: join(RA, '04-danh-sach.png') });
await r(() => moLinhVuc('')); await doi(500); await r(() => locTheo('ve-sinh')); await doi(700); t = await tt();
const soVeSinh = await r(() => D.san_pham.filter(s => s.nhom === 've-sinh').length);
kiem('Xem tất cả + lọc Vệ sinh → tivi lọc theo (' + soVeSinh + ' dòng)', t.dsSo === soVeSinh, String(t.dsSo));

console.log('\n[5] Mở chi tiết BellaBot Pro');
await r(() => moSanPham('bellabot-pro')); await doi(1000); t = await tt();
kiem('tivi sang chi tiết, đúng tên', t.man === 'tv-ct' && t.ten === 'BellaBot Pro', t.man + ' ' + t.ten);
kiem('tab Giới thiệu + giọng sp-bellabot-pro-gioi_thieu', t.tab === 'tab-gioi_thieu' && /sp-bellabot-pro-gioi_thieu\.mp3$/.test(t.nguon), t.tab + ' ' + t.nguon);
kiem('ảnh/video đang chiếu khớp robot (mục 0)', t.thumb === 0, String(t.thumb));
await tivi.screenshot({ path: join(RA, '05-chi-tiet.png') });
await r(() => moTab('thong_so')); await r(() => chieuMedia(3)); await doi(900); t = await tt();
kiem('đổi tab Thông số → tivi đổi theo + phát câu thông số', t.tab === 'tab-thong_so' && /thong_so\.mp3$/.test(t.nguon), t.tab + ' ' + t.nguon);
kiem('robot chuyển sang ảnh thứ 4 → tivi chiếu ảnh thứ 4', t.thumb === 3, String(t.thumb));
await tivi.screenshot({ path: join(RA, '06-thong-so.png') });

console.log('\n[6] Đăng ký dùng thử → form → gõ thông tin khách');
await r(() => moDungThu()); await doi(800); t = await tt();
kiem('tivi sang màn 4 bước', t.man === 'tv-dt', t.man);
await tivi.screenshot({ path: join(RA, '07-dung-thu.png') });
await r(() => { moForm(); 'nguyeenx thij lan'.split('').forEach(c => go(c)); chonO('sdt'); '0987654321'.split('').forEach(c => go(c));
  chonO('cong_ty'); 'coong ty ABC'.split('').forEach(c => go(c)); }); await doi(900); t = await tt();
kiem('tivi sang màn form', t.man === 'tv-form', t.man);
kiem('tivi KHÔNG hiện họ tên / SĐT / công ty khách đang gõ', !/Nguyễn Thị Lan|0987654321|ABC/i.test(t.chu));
await tivi.screenshot({ path: join(RA, '08-form.png') });
await r(() => guiForm()); await doi(900); t = await tt();
kiem('gửi xong → tivi sang cảm ơn', t.man === 'tv-camon', t.man);
kiem('màn cảm ơn trên tivi KHÔNG có tên / SĐT khách', !/Nguyễn Thị Lan|0987654321/i.test(t.chu));
const daGui = await r(() => GIA_LAP.tivi.join('\n'));
kiem('mọi tin robot gửi sang tivi đều KHÔNG chứa dữ liệu khách', !/Nguy|Lan|0987654321|ABC/.test(daGui));
await tivi.screenshot({ path: join(RA, '09-cam-on.png') });

console.log('\n[7] Về màn chờ giữa câu → tivi im ngay');
await r(() => veManCho()); await doi(900); t = await tt();
kiem('tivi về màn chờ', t.man === 'tv-cho', t.man);
kiem('giọng tivi dừng (luật ②)', !t.dangDoc && !t.phuDe);
await tivi.screenshot({ path: join(RA, '10-ve-cho.png') });

console.log('\n[8] Chế độ "Chỉ loa robot"');
await r(() => { CAU.datCheDoTieng('robot'); tiviTT.cheDo = 'robot'; tv({ cheDo: 'robot' }); thoiCho(); }); await doi(900); t = await tt();
kiem('tivi nhận chế độ robot, KHÔNG phát giọng', t.cheDo === 'robot' && !t.dangDoc, t.cheDo + ' dangDoc=' + t.dangDoc);
kiem('phụ đề vẫn hiện cho người đứng xa', t.phuDe.length > 10);
await r(() => { CAU.datCheDoTieng('tu-dong'); tiviTT.cheDo = 'tu-dong'; tv({ cheDo: 'tu-dong' }); });

console.log('\n[9] Tivi nối SAU khi robot đã ở giữa luồng');
await r(() => moSanPham('pudu-cc1')); await doi(600);
const tivi2 = await td.newPage({ viewport: { width: 1280, height: 720 } });
await tivi2.goto(GOC + '/', { waitUntil: 'load' }); await doi(1500);
const t2 = await tivi2.evaluate(() => ({ man: document.querySelector('.man.hien').id, ten: document.querySelector('#ct-ten').textContent, ...window.TIVI.trangThai() }));
kiem('tivi mới mở thấy NGAY màn robot đang hiện', t2.man === 'tv-ct' && /CC1/.test(t2.ten), t2.man + ' ' + t2.ten);
kiem('và KHÔNG đọc lại câu cũ đang dở', !t2.dangDoc);
await tivi2.screenshot({ path: join(RA, '11-tivi-720p.png') });

console.log('\n[10] Bố cục tivi không tràn ở mọi màn');
for (const [man, ham] of [['mh-lv', () => thoiCho()], ['mh-ds', () => moLinhVuc('')], ['mh-ct', () => { moSanPham('pudu-t600'); moTab('thong_so'); }],
  ['mh-ct', () => { moSanPham('bellabot-pro'); moTab('phu_hop'); }], ['mh-dt', () => moDungThu()]]) {
  await r(ham); await doi(700);
  const tran = await tivi.evaluate(() => { const r = [], g = document.querySelector('.man.hien'); g.querySelectorAll('.the-sp,.the-lv,.noi,.buoc > div,.ct-phai h2').forEach(e => {
    const b = e.getBoundingClientRect(); if (b.bottom > innerHeight + 1 || b.right > innerWidth + 1) r.push((e.className || e.tagName) + ' ' + Math.round(b.bottom)); });
    const n = g.querySelector('.noi'); if (n && n.scrollHeight > n.clientHeight + 4) r.push('nội dung tab bị cắt ' + n.scrollHeight + '>' + n.clientHeight); return r; });
  kiem('không tràn: ' + man + ' ' + (await tt()).ten, !tran.length, tran.join(' · '));
}
await tivi.screenshot({ path: join(RA, '12-phu-hop.png') });

console.log('\n' + (hong ? '✘' : '✔') + ' ' + dat + ' đạt · ' + hong + ' hỏng');
loi.forEach(x => console.log('  ' + x));
console.log('Ảnh: ' + RA);
await td.close();
process.exit(hong || loi.length ? 1 : 0);
