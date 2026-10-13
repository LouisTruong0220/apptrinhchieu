/* ═══════════════════════════════════════════════════════════════════════
   GIẢ LẬP ROBOT — chỉ cho bản thử trên máy tính (demo/thu-nghiem.html).
   KHÔNG đóng vào APK. Trên robot thật, CAU do tầng Android cấp.
   Dựng lại đúng bộ hàm của Cau.kt: noi · dungDoc · danDuongToiDiem · dungDanDuong ·
   lyDoChuaDanDuongDuoc · datDiemVeCho · kiemTraBanDo · thongTinMay · ghiKhach.
   ═══════════════════════════════════════════════════════════════════════ */
(function () {
  'use strict';
  var GL = { giay: 4, khach: [], daNoi: [], dangDoc: null, dangDi: null, lyDoChan: '' };
  window.GIA_LAP = GL;
  var DIEM_GIA = ['Diem 1', 'Diem 2', 'Diem 3', 'Diem 4', 'Tram sac'];   // cố ý THIẾU Diem 5

  function ghi(s) { console.log('[GIẢ LẬP] ' + s); }
  function doc(id, vanBan) {
    dungDoc();
    ghi('🔊 ' + id + ': ' + vanBan);
    GL.daNoi.push(id);
    try { var u = new SpeechSynthesisUtterance(vanBan); u.lang = 'vi-VN'; speechSynthesis.speak(u); } catch (e) {}
    var giay = Math.max(1.2, vanBan.length / 15);
    GL.dangDoc = setTimeout(function () { GL.dangDoc = null; if (window.robotDocXong) window.robotDocXong(); }, giay * 1000);
  }
  function dungDoc() { if (GL.dangDoc) { clearTimeout(GL.dangDoc); GL.dangDoc = null; } try { speechSynthesis.cancel(); } catch (e) {} }
  function bao(tt, chu) { if (window.baoDanDuong) window.baoDanDuong(tt, chu); }
  function danDuongToiDiem(ten) {
    if (GL.lyDoChan) { bao('loi', GL.lyDoChan); return; }
    if (DIEM_GIA.indexOf(ten) < 0) { ghi('✘ không có điểm ' + ten); bao('loi', 'Chưa có điểm này trên bản đồ.'); return; }
    ghi('🚶 đi tới ' + ten); bao('bat-dau', '');
    clearTimeout(GL.dangDi);
    GL.dangDi = setTimeout(function () { GL.dangDi = null; ghi('📍 tới ' + ten); bao('toi-noi', ''); }, GL.giay * 1000);
  }
  window.CAU = {
    noi: doc, dungDoc: dungDoc,
    danDuongToiDiem: danDuongToiDiem,
    dungDanDuong: function () { clearTimeout(GL.dangDi); GL.dangDi = null; ghi('■ dừng đi'); },
    lyDoChuaDanDuongDuoc: function () { return GL.lyDoChan; },
    robotSanSang: function () { return !GL.lyDoChan; },
    datDiemVeCho: function (t) { ghi('điểm về chỗ = ' + t); },
    kiemTraBanDo: function () {
      setTimeout(function () {
        if (window.baoTinhTrangBanDo) window.baoTinhTrangBanDo(JSON.stringify({ daDinhVi: true,
          danhSachDiem: JSON.stringify(DIEM_GIA.map(function (n) { return { name: n }; })) }));
      }, 300);
    },
    thongTinMay: function () { return JSON.stringify({ daKetNoi: true, biTreo: false, soKhach: GL.khach.length, soGiong: 0, telegram: 'giả lập' }); },
    ghiKhach: function (json) { var o = JSON.parse(json); GL.khach.push(o); ghi('📝 KHÁCH: ' + json); return true; },
    /* Tivi — chỉ chạy khi mở qua tools/gia-lap-may-chu.py (http://127.0.0.1:8090/robot). Mở bằng file:// thì
       chỉ ghi lại để bộ thử đếm (GL.tivi), không gửi đi đâu. */
    tivi: function (json) {
      GL.tivi.push(json);
      if (location.protocol.indexOf('http') === 0) { try { fetch('/gia-lap/tivi', { method: 'POST', body: json }); } catch (e) {} }
    },
    cheDoTieng: function () { return GL.cheDo; },
    datCheDoTieng: function (m) { GL.cheDo = m; ghi('nguồn tiếng = ' + m); },
    thongTinTivi: function () {
      return JSON.stringify({ diaChi: location.protocol.indexOf('http') === 0 ? location.origin : '', soTivi: GL.tt.soTivi,
        soTiviCoTieng: GL.tt.soTiviCoTieng, cheDo: GL.cheDo, robotIm: GL.cheDo === 'tu-dong' && GL.tt.soTiviCoTieng > 0 });
    }
  };
  GL.tivi = []; GL.cheDo = 'tu-dong'; GL.tt = { soTivi: 0, soTiviCoTieng: 0 };
  /* Bản giả lập hỏi máy chủ số tivi 2 giây/lần, báo về lớp web y như Cau.baoTivi() bên Kotlin */
  if (location.protocol.indexOf('http') === 0) setInterval(function () {
    fetch('/gia-lap/tt').then(function (r) { return r.json(); }).then(function (o) {
      var doi = o.soTiviCoTieng !== GL.tt.soTiviCoTieng || o.soTivi !== GL.tt.soTivi; GL.tt = o;
      if (doi && window.baoTivi) window.baoTivi(window.CAU.thongTinTivi());
    }).catch(function () {});
  }, 2000);
})();
