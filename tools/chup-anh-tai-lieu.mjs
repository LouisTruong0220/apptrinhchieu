/**
 * Chụp ảnh cho tài liệu hướng dẫn kỹ thuật (PDF): màn robot + bảng kỹ thuật có dòng "Tivi" + màn tivi.
 *   python tools/gia-lap-may-chu.py 8095     (chạy trước)
 *   node tools/chup-anh-tai-lieu.mjs          → tai-lieu/anh/
 * Địa chỉ tivi trên bảng kỹ thuật là ĐỊA CHỈ MẪU (192.168.1.76) — bản giả lập không có IP robot thật.
 */
import { existsSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url)), APP = resolve(HERE, '..');
const PW = join(APP, '..', '..', 'tools', 'html-video', 'packages', 'adapter-hyperframes', 'node_modules', 'playwright', 'index.mjs');
const { chromium } = existsSync(PW) ? await import(pathToFileURL(PW).href) : await import('playwright');
const GOC = process.argv[2] || 'http://127.0.0.1:8095';
const RA = join(APP, 'tai-lieu', 'anh'); mkdirSync(RA, { recursive: true });
const doi = ms => new Promise(r => setTimeout(r, ms));

const td = await chromium.launch();
const robot = await td.newPage({ viewport: { width: 1920, height: 1080 } });
const tivi = await td.newPage({ viewport: { width: 1920, height: 1080 } });
await robot.goto(GOC + '/robot'); await doi(1200);
await tivi.goto(GOC + '/'); await doi(1500);
await tivi.keyboard.press('Enter'); await doi(4500);
const r = f => robot.evaluate(f);

await r(() => { const MAU = JSON.stringify({ diaChi: 'http://192.168.1.76:8090', soTivi: 1, soTiviCoTieng: 1, cheDo: 'tu-dong', robotIm: true }); CAU.thongTinTivi = () => MAU; thoiCho(); moKT(); window.baoTivi(JSON.stringify({ diaChi: 'http://192.168.1.76:8090', soTivi: 1, soTiviCoTieng: 1, cheDo: 'tu-dong', robotIm: true })); });
await doi(900);
await robot.screenshot({ path: join(RA, 'robot-bang-ky-thuat.png') });
await r(() => { dongKT(); moSanPham('bellabot-pro'); }); await doi(1500);
await robot.screenshot({ path: join(RA, 'robot-chi-tiet.png') });
await tivi.screenshot({ path: join(RA, 'tivi-chi-tiet.png') });
await r(() => moLinhVuc('')); await doi(1200);
await tivi.screenshot({ path: join(RA, 'tivi-danh-sach.png') });
await r(() => moForm()); await doi(1000);
await tivi.screenshot({ path: join(RA, 'tivi-form.png') });
await r(() => veManCho()); await doi(1500);
const tivi2 = await td.newPage({ viewport: { width: 1920, height: 1080 } });
await tivi2.goto(GOC + '/'); await doi(2000);
await tivi2.screenshot({ path: join(RA, 'tivi-cho-chua-bat-tieng.png') });
console.log('Ảnh: ' + RA);
await td.close();
