/**
 * Chụp từng màn của app ở đúng khung 1920×1080 của Nova và tự bắt lỗi bố cục
 * (nút 0×0, bị co, lọt khung, tràn chiều cao, lỗi JS).   node tools/soi-man-hinh.mjs
 * Chụp bản demo/thu-nghiem.html (có giả lập robot) — bản duy nhất thấy được du hành và form gửi được.
 */
import { existsSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url)), APP = resolve(HERE, '..');
const PW = join(APP, '..', '..', 'tools', 'html-video', 'packages', 'adapter-hyperframes', 'node_modules', 'playwright', 'index.mjs');
/* Trong workspace: mượn Playwright của tools/html-video. Ngoài workspace (kho GitHub): npm i playwright */
const { chromium } = existsSync(PW) ? await import(pathToFileURL(PW).href) : await import('playwright');
const RA = resolve(process.argv[2] || join(APP, 'demo', 'anh-soi')); mkdirSync(RA, { recursive: true });

const BUOC = [
  ['01-man-cho',      'Man cho — video bieu cam + bang chu sang', null, 800],
  ['02-linh-vuc',     'Cham -> chon linh vuc (8 the)', () => thoiCho(), 600],
  ['03-ds-nha-may',   'Linh vuc Nha may -> 9 dong', () => moLinhVuc('nha-may'), 600],
  ['04-ds-tat-ca',    'Xem tat ca -> 18 dong + bo loc', () => moLinhVuc(''), 600],
  ['05-loc-ve-sinh',  'Loc nhom ve sinh', () => locTheo('ve-sinh'), 400],
  ['06-ct-gioi-thieu','Chi tiet BellaBot Pro — tab gioi thieu, video', () => moSanPham('bellabot-pro'), 1200],
  ['07-ct-thong-so',  'Tab thong so', () => moTab('thong_so'), 500],
  ['08-ct-phu-hop',   'Tab phu hop + khach hang', () => moTab('phu_hop'), 500],
  ['09-ct-anh',       'Chuyen sang anh thu 3', () => chieuMedia(2), 700],
  ['10-ct-t600',      'Chi tiet T600 (khong video) — thong so dai', () => { moSanPham('pudu-t600'); moTab('thong_so'); }, 900],
  ['11-dung-thu',     'Dang ky dung thu — 4 buoc', () => moDungThu(), 600],
  ['12-form-trong',   'Form trong + ban phim chu', () => moForm(), 600],
  ['13-form-go-ten',  'Go Telex: "nguyeenx vawn truowngf" -> Nguyễn Văn Trường',
    () => { 'nguyeenx vawn truowngf'.split('').forEach(c => go(c)); }, 500],
  ['14-form-sdt',     'O so dien thoai -> ban phim so', () => { chonO('sdt'); '0912345678'.split('').forEach(c => go(c)); }, 500],
  ['15-form-loi',     'Gui thieu cong ty -> bao loi do', () => guiForm(), 600],
  ['16-cam-on',       'Dien du -> cam on', () => { chonO('cong_ty'); 'coong ty TNHH ABC'.split('').forEach(c => go(c)); guiForm(); }, 800],
  ['17-bang-kt',      'Bang ky thuat (giu logo) — thieu Diem 5', () => { veManCho(); thoiCho(); moKT(); }, 900],
];
const CO_Y_AN = '#phu-kt, .hop-bao, .cho-nhac';
const trinhDuyet = await chromium.launch();
const trang = await trinhDuyet.newPage({ viewport: { width: 1920, height: 1080 } });
const loi = [];
trang.on('pageerror', e => loi.push('  ✗ LOI JS: ' + e.message));
trang.on('console', m => { if (m.type() === 'error') loi.push('  ✗ console: ' + m.text()); });
await trang.goto(pathToFileURL(join(APP, 'demo', 'thu-nghiem.html')).href, { waitUntil: 'load' });
await trang.waitForTimeout(900);
let hong = 0;
for (const [ten, mo, ham, cho] of BUOC) {
  if (ham) { await trang.evaluate(ham); }
  await trang.waitForTimeout(cho || 450);
  await trang.screenshot({ path: join(RA, ten + '.png') });
  const bao = await trang.evaluate((CO_Y_AN) => {
    const hien = document.querySelector('.man-hinh.hien'); const r = [];
    if (!hien) return ['KHONG co man hinh nao dang hien'];
    if (document.documentElement.scrollHeight > innerHeight + 2) r.push('TRAN CHIEU CAO ' + document.documentElement.scrollHeight);
    const goc = document.querySelector('.phu.hien') || hien;
    goc.querySelectorAll('button, .o, .buoc > div, .tn > div, .ts > div, .ph > div').forEach(e => {
      if (e.closest(CO_Y_AN)) return;
      const ten = (e.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 40);
      let cha = e.parentElement, an = false;
      while (cha && cha !== goc) { if (getComputedStyle(cha).display === 'none') { an = true; break; } cha = cha.parentElement; }
      if (an) return;
      const b = e.getBoundingClientRect();
      if (b.width === 0 && b.height === 0) r.push('0x0: "' + ten + '"');
      else if (b.height < 8) r.push('BI CO: "' + ten + '"');
      if (!e.closest('.luoi-sp, .ct-noi, .kiem-ds')) {
        if (b.bottom > innerHeight + 2) r.push('LOT DAY (' + Math.round(b.bottom) + '): "' + ten + '"');
        if (b.right > innerWidth + 2) r.push('LOT PHAI: "' + ten + '"');
      }
    });
    return r;
  }, CO_Y_AN);
  const co = bao.length || loi.length; if (co) hong++;
  console.log((co ? '✗ ' : '✓ ') + ten + '  —  ' + mo);
  bao.forEach(x => console.log('  ✗ ' + x)); loi.splice(0).forEach(x => console.log(x));
}
const telex = await trang.evaluate(() => ['nguyeenx','vawn','truowngf','hoafng','thaor','quyfnh','ddawng','phamj','hoa','thuys'].map(t => t + '→' + telex(t)).join('  '));
console.log('Telex: ' + telex);
await trinhDuyet.close();
console.log('\nAnh o: ' + RA); console.log(hong ? `✗ ${hong} man co van de` : '✓ Khong man nao bao loi');
process.exit(hong ? 1 : 0);
