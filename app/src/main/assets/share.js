/* بطاقات المشاركة: تولّد صورة جميلة (canvas) لآية أو ذكر أو مواقيت اليوم، مع معاينة ومشاركة وحفظ.
   window.ShareCard.verse({text,sura,ayah,page}) · .dhikr({t,a,s,c}) · .times({city,d1,d2,rows:[[اسم,وقت],..],next}) */
(function () {
  "use strict";
  var W = 1080, H = 1350, D = document, APP = "مواقيت الصلاة سوريا";
  var TH = {
    n: { bg0: "#0b3b2b", bg1: "#04170f", gold: "#d6b25e", txt: "#f4f1e8", mut: "#a9c2b4", pat: "rgba(214,178,94,.075)", pill: "rgba(214,178,94,.13)", row: "rgba(255,255,255,.05)" },
    d: { bg0: "#fffdf5", bg1: "#f0e4c2", gold: "#8a5a00", txt: "#2b2416", mut: "#7a6a45", pat: "rgba(138,90,0,.10)", pill: "rgba(138,90,0,.10)", row: "rgba(138,90,0,.06)" }
  };
  var QF = '"QuranFont","Amiri",serif', AF = '"GoogleSans","Segoe UI",Tahoma,sans-serif';
  function ar(n) { return String(n).replace(/\d/g, function (d) { return "٠١٢٣٤٥٦٧٨٩"[d]; }); }
  function lsG(k, d) { try { var v = localStorage.getItem(k); return v == null ? d : v; } catch (e) { return d; } }
  function lsS(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }

  var MQ = null;
  function loadMq() {
    return new Promise(function (res) {
      if (MQ) return res();
      var i = new Image(); i.onload = function () { MQ = i; res(); }; i.onerror = function () { res(); }; i.src = "data:image/png;base64,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";
      setTimeout(res, 1500);
    });
  }
  function fonts() {
    if (!D.fonts || !D.fonts.load) return loadMq();
    var p = ["40px QuranFont", "700 40px QuranFont", "40px GoogleSans", "700 40px GoogleSans"].map(function (f) {
      return D.fonts.load(f, "اب1").catch(function () {});
    });
    p.push(loadMq());
    return Promise.race([Promise.all(p), new Promise(function (r) { setTimeout(r, 2500); })]);
  }

  /* ---------- أدوات الرسم ---------- */
  function rr(x, a, b, w, h, r) {
    x.beginPath();
    x.moveTo(a + r, b); x.arcTo(a + w, b, a + w, b + h, r); x.arcTo(a + w, b + h, a, b + h, r);
    x.arcTo(a, b + h, a, b, r); x.arcTo(a, b, a + w, b, r); x.closePath();
  }
  function star(x, cx, cy, r) { // نجمة ثمانية (مربعان متداخلان)
    for (var k = 0; k < 2; k++) {
      x.beginPath();
      for (var i = 0; i < 4; i++) {
        var an = Math.PI / 2 * i + (k ? Math.PI / 4 : 0) + Math.PI / 4;
        var px = cx + r * Math.cos(an), py = cy + r * Math.sin(an);
        if (i) x.lineTo(px, py); else x.moveTo(px, py);
      }
      x.closePath(); x.stroke();
    }
  }
  function crescent(x, cx, cy, r, col) {
    x.save(); x.fillStyle = col; x.beginPath();
    x.arc(cx, cy, r, 0, Math.PI * 2); x.arc(cx + r * 0.38, cy - r * 0.1, r * 0.82, 0, Math.PI * 2, true);
    x.fill("evenodd"); x.restore();
    x.save(); x.strokeStyle = col; x.lineWidth = 2; star(x, cx + r * 0.9, cy - r * 0.2, r * 0.24); x.restore();
  }
  function base(t) {
    var c = D.createElement("canvas"); c.width = W; c.height = H;
    var x = c.getContext("2d");
    var g = x.createLinearGradient(0, 0, W * 0.4, H); g.addColorStop(0, t.bg0); g.addColorStop(1, t.bg1);
    x.fillStyle = g; x.fillRect(0, 0, W, H);
    x.strokeStyle = t.pat; x.lineWidth = 2;
    for (var j = 0; j < 11; j++) for (var i = 0; i < 9; i++) star(x, 60 + i * 120 + (j % 2 ? 60 : 0), 60 + j * 120, 46);
    // إطار مزدوج
    x.strokeStyle = t.gold; x.lineWidth = 4; rr(x, 34, 34, W - 68, H - 68, 26); x.stroke();
    x.lineWidth = 1.5; rr(x, 52, 52, W - 104, H - 104, 16); x.stroke();
    [[70, 70], [W - 70, 70], [70, H - 70], [W - 70, H - 70]].forEach(function (p) {
      x.fillStyle = t.gold; x.save(); x.translate(p[0], p[1]); x.rotate(Math.PI / 4); x.fillRect(-7, -7, 14, 14); x.restore();
    });
    x.direction = "rtl"; x.textBaseline = "middle";
    return { c: c, x: x, t: t };
  }
  var QR_URL = "https://github.com/abdulrhmnhwa/Tv-apk/releases/latest";
  function qrCode(b) { // رمز QR أسفل اليسار يفتح صفحة آخر نسخة من التطبيق
    try {
      if (!window.QRGen) return;
      var x = b.x, q = QRGen.make(QR_URL), cell = Math.max(3, Math.floor(128 / q.n)), sz = q.n * cell, pad = 12,
          bx = 104, by = H - 108 - 22 - (sz + pad * 2);
      x.save(); x.fillStyle = "#ffffff"; rr(x, bx, by, sz + pad * 2, sz + pad * 2, 14); x.fill();
      x.fillStyle = "#000000";
      for (var r = 0; r < q.n; r++) for (var c = 0; c < q.n; c++) if (q.d(r, c)) x.fillRect(bx + pad + c * cell, by + pad + r * cell, cell, cell);
      x.fillStyle = b.t.mut; x.font = "400 20px " + AF; x.textAlign = "center"; x.direction = "rtl";
      x.fillText("لتحميل التطبيق", bx + (sz + pad * 2) / 2, by + sz + pad * 2 + 20);
      x.restore();
    } catch (e) {}
  }
  function footer(b) {
    var x = b.x, t = b.t;
    qrCode(b);
    x.strokeStyle = t.gold; x.globalAlpha = 0.6; x.lineWidth = 1.5;
    x.beginPath(); x.moveTo(W / 2 - 150, H - 214); x.lineTo(W / 2 + 150, H - 214); x.stroke(); x.globalAlpha = 1;
    if (MQ) { // أيقونة المسجد (نفس أيقونة الإشعارات) بلون الثيم
      var m = D.createElement("canvas"); m.width = m.height = 96; var mx = m.getContext("2d");
      mx.drawImage(MQ, 0, 0, 96, 96); mx.globalCompositeOperation = "source-in"; mx.fillStyle = t.gold; mx.fillRect(0, 0, 96, 96);
      x.drawImage(m, W / 2 - 30, H - 204, 60, 60);
    } else crescent(x, W / 2, H - 150, 20, t.gold);
    x.textAlign = "center"; x.fillStyle = t.gold; x.font = "700 40px " + AF; x.fillText(APP, W / 2, H - 100);
  }
  function pill(b, text, y, font, maxW) {
    var x = b.x, t = b.t; x.font = font; var w = Math.min(maxW || 900, x.measureText(text).width + 90), h = 84;
    x.fillStyle = t.pill; rr(x, W / 2 - w / 2, y - h / 2, w, h, 42); x.fill();
    x.strokeStyle = t.gold; x.lineWidth = 2; rr(x, W / 2 - w / 2, y - h / 2, w, h, 42); x.stroke();
    x.textAlign = "center"; x.fillStyle = t.gold; x.fillText(text, W / 2, y + 3, w - 40);
  }
  function wrap(x, text, maxW, nl) {
    if (nl) { /* يحترم فواصل الأسطر الصريحة: كل مقطع يُلفّ مستقلاً */
      var out = [];
      String(text).split(/\n/).forEach(function (seg) { wrap(x, seg, maxW).forEach(function (ln) { out.push(ln); }); });
      return out;
    }
    var words = String(text).replace(/\s+/g, " ").trim().split(" "), lines = [], cur = "";
    words.forEach(function (w) {
      var tr = cur ? cur + " " + w : w;
      if (cur && x.measureText(tr).width > maxW) { lines.push(cur); cur = w; } else cur = tr;
    });
    if (cur) lines.push(cur);
    return lines;
  }
  /* يختار أكبر خط يتّسع النص به داخل المساحة، وإن لم يتّسع يقتطع بنقاط */
  function fit(x, text, face, weight, maxW, maxH, from, to, lh, nl) {
    for (var s = from; s >= to; s -= 2) {
      x.font = weight + " " + s + "px " + face;
      var L = wrap(x, text, maxW, nl);
      if (L.length * s * lh <= maxH) return { s: s, L: L, lh: lh };
    }
    x.font = weight + " " + to + "px " + face;
    var L2 = wrap(x, text, maxW, nl), mx = Math.max(1, Math.floor(maxH / (to * lh)));
    if (L2.length > mx) { L2 = L2.slice(0, mx); L2[mx - 1] = L2[mx - 1].replace(/\s*\S*$/, "") + " …"; }
    return { s: to, L: L2, lh: lh };
  }
  function drawLines(b, f, face, weight, y0, h, color) {
    var x = b.x; x.font = weight + " " + f.s + "px " + face; x.textAlign = "center"; x.fillStyle = color;
    var step = f.s * f.lh, top = y0 + (h - f.L.length * step) / 2 + step / 2;
    f.L.forEach(function (ln, i) { x.fillText(ln, W / 2, top + i * step); });
  }

  /* ---------- البطاقات ---------- */
  function drawVerse(d, t) {
    var b = base(t), x = b.x;
    pill(b, "سورة " + d.sura, 170, "700 44px " + QF);
    var f = fit(x, "﴿" + d.text + "﴾", QF, "400", 860, 780, 86, 30, 1.95);
    drawLines(b, f, QF, "400", 250, 800, t.txt);
    x.textAlign = "center"; x.fillStyle = t.gold; x.font = "700 36px " + AF;
    x.fillText("الآية " + ar(d.ayah) + (d.page ? "  ·  صفحة " + ar(d.page) : ""), W / 2, 1090);
    footer(b); return b.c;
  }
  /* لا يُعرض أبداً سطر التخريج (رواه/أخرجه/متفق عليه/صححه…) في بطاقة الذكر */
  var SRC = /(رواه|أخرجه|أخرجها|أخرجهما|متفق عليه|صححه|حسّنه|حسنه|صحيح\s)/;
  function noSrc(t) {
    t = String(t || "").replace(/[\(\[（][^\)\]）]*(رواه|أخرجه|متفق عليه)[^\)\]）]*[\)\]）]/g, " ");
    return t.split(/\n+/).map(function (ln) {
      return ln.split(/(?<=[.؟!])\s+/).filter(function (z) { return !SRC.test(z.trim().slice(0, 14)); }).join(" ");
    }).filter(function (z) { return z.trim() && !SRC.test(z.trim().slice(0, 14)); }).join("\n").replace(/\s+/g, " ").trim();
  }
  function drawDhikr(d, t) {
    /* على الكانفس تُعرض أرقام الآيات بأقواس ﴿ ﴾ بدل علامة ۝ (تظهر مزدوجة ومطبوعة فوق الرقم في رسم الكانفس) */
    /* تنظيف التخريج سطراً سطراً حتى تبقى فواصل الأسطر الصريحة (مثل البسملة بسطر وحدها) */
    var aClean = String(d.a || "").split(/\n/).map(function (ln) { return noSrc(ln); }).filter(function (z, i, arr) { return z || arr.length === 1; }).join("\n");
    d = { t: d.t, a: (aClean || d.a).replace(/۝\s*([٠-٩0-9]+)/g, '\uFD3F$1\uFD3E'), s: noSrc(d.s), c: d.c, g: d.g };
    var b = base(t), x = b.x;
    pill(b, d.t || "ذكر", 170, "700 42px " + AF);
    var hasS = d.s && d.s.trim();
    var f = fit(x, d.a, QF, "400", 860, hasS ? 560 : 720, 62, 28, 1.9, 1);
    drawLines(b, f, QF, "400", 250, hasS ? 580 : 760, t.txt);
    var y = hasS ? 860 : 1030;
    if (d.c) { x.textAlign = "center"; x.fillStyle = t.gold; x.font = "700 34px " + AF; x.fillText("التكرار: " + d.c, W / 2, y); y += 70; }
    if (hasS) {
      var g = fit(x, d.s, AF, "400", 820, 1068 - y, 30, 22, 1.65);
      drawLines(b, g, AF, "400", y - 15, 1068 - y + 15, t.mut);
    }
    if (d.g) { /* اسم القسم تحت، فوق خط التذييل الناعم بمسافة، وبين معيّنتين (زخرفة) يميناً ويساراً */
      var gl = (/^(أذكار|دعاء)/.test(d.g) ? "من " : "من أذكار ") + d.g;
      x.textAlign = "center"; x.fillStyle = t.gold; x.font = "700 30px " + AF;
      var gy = H - 214 - 36;
      x.fillText(gl, W / 2, gy);
      var gw = x.measureText(gl).width;
      [W / 2 - gw / 2 - 42, W / 2 + gw / 2 + 42].forEach(function (px) {
        x.save(); x.translate(px, gy); x.rotate(Math.PI / 4); x.fillRect(-6, -6, 12, 12); x.restore();
      });
    }
    footer(b); return b.c;
  }
  /* بطاقة التفسير: يُقاس النص أولاً ويُضبط ارتفاع البطاقة حتى لا تُقتطع الآية ولا التفسير أبداً */
  function drawTafsir(d, t) {
    var mx = D.createElement("canvas").getContext("2d");
    var ayTxt = "\uFD3F" + d.text + "\uFD3E", tfTxt = String(d.tafsir || "");
    var sA, LA;
    for (sA = 48; sA >= 18; sA -= 2) { mx.font = "400 " + sA + "px " + QF; LA = wrap(mx, ayTxt, 860); if (LA.length <= 6) break; }
    var ayH = LA.length * sA * 1.8;
    var sT, LT;
    for (sT = 32; sT > 20; sT -= 2) { mx.font = "400 " + sT + "px " + AF; LT = wrap(mx, tfTxt, 880); if (LT.length * sT * 1.7 <= 560) break; }
    mx.font = "400 " + sT + "px " + AF; LT = wrap(mx, tfTxt, 880);
    var tfH = LT.length * sT * 1.7;
    var bandA = Math.max(ayH, 200), bandT = Math.max(tfH, 560);
    var sepY = 262 + bandA + 24, tfY = sepY + 28;
    var l1Y = tfY + bandT + 18, l2Y = l1Y + 32;
    H = Math.max(1350, Math.ceil(l2Y + 28 + 214));
    var b = base(t), x = b.x;
    pill(b, "سورة " + d.sura + " · الآية " + ar(d.ayah) + (d.page ? " · صفحة " + ar(d.page) : ""), 140, "700 36px " + AF, 940);
    x.textAlign = "center"; x.fillStyle = t.gold; x.font = "700 34px " + AF;
    x.fillText("تفسير الآية · التفسير الميسّر", W / 2, 236);
    drawLines(b, { s: sA, L: LA, lh: 1.8 }, QF, "400", 262, bandA, t.gold);
    x.strokeStyle = t.gold; x.globalAlpha = 0.5; x.lineWidth = 1.5;
    x.beginPath(); x.moveTo(W / 2 - 120, sepY); x.lineTo(W / 2 + 120, sepY); x.stroke(); x.globalAlpha = 1;
    drawLines(b, { s: sT, L: LT, lh: 1.7 }, AF, "400", tfY, bandT, t.txt);
    /* سطرا المصدر: بمحاذاة اليمين وعرض أقصى 690px فلا يقربان رمز QR أسفل اليسار */
    x.fillStyle = t.mut; x.font = "400 20px " + AF; x.textAlign = "right";
    x.fillText("المصدر: التفسير الميسّر — مجمع الملك فهد لطباعة المصحف الشريف", W - 130, l1Y, 690);
    x.fillText("النص الرقمي: quranenc.com · إصدار V1.0.1 · يُعرض بنصّه دون تغيير", W - 130, l2Y, 690);
    footer(b);
    H = 1350;
    return b.c;
  }
  function drawTimes(d, t) {
    var b = base(t), x = b.x;
    x.textAlign = "center"; x.fillStyle = t.gold; x.font = "700 64px " + AF; x.fillText("مواقيت الصلاة", W / 2, 170);
    pill(b, d.city || "", 262, "700 40px " + AF, 600);
    x.fillStyle = t.txt; x.font = "400 34px " + AF; x.fillText(d.d1 || "", W / 2, 350);
    if (d.d2) { x.fillStyle = t.mut; x.font = "400 30px " + AF; x.fillText(d.d2, W / 2, 396); }
    var y0 = 460, step = 108;
    (d.rows || []).forEach(function (r, i) {
      var y = y0 + i * step, on = i === d.next;
      x.fillStyle = on ? t.pill : t.row; rr(x, 120, y - 42, W - 240, 84, 24); x.fill();
      if (on) { x.strokeStyle = t.gold; x.lineWidth = 2.5; rr(x, 120, y - 42, W - 240, 84, 24); x.stroke(); }
      x.textAlign = "right"; x.fillStyle = on ? t.gold : t.txt; x.font = (on ? "700 " : "400 ") + "46px " + AF; x.fillText(r[0], W - 160, y + 2);
      x.textAlign = "left"; x.direction = "ltr"; x.font = (on ? "700 " : "400 ") + "46px " + AF; x.fillText(r[1], 160, y + 2); x.direction = "rtl";
    });
    footer(b); return b.c;
  }

  function drawImsak(d, t) {
    var b = base(t), x = b.x, R = d.rows || [], half = Math.ceil(R.length / 2);
    x.textAlign = "center"; x.fillStyle = t.gold; x.font = "700 64px " + AF; x.fillText("إمساكية رمضان", W / 2, 150);
    pill(b, (d.city || "") + (d.year ? "  ·  " + d.year : ""), 232, "700 36px " + AF, 760);
    var HW = 440, cols = [48, 150, 265, 385]; // مواضع الأعمدة من يمين كل نصف
    [0, 1].forEach(function (h) {
      var right = W - 90 - h * (HW + 20), y = 318;
      x.fillStyle = t.mut; x.font = "400 26px " + AF; x.textAlign = "center";
      ["اليوم", "التاريخ", "الإمساك", "الإفطار"].forEach(function (n, i) { x.fillText(n, right - cols[i], y); });
      for (var i = 0; i < half; i++) {
        var r = R[h * half + i]; if (!r) break;
        var yy = 372 + i * 50, on = r[4];
        x.fillStyle = on ? t.pill : (i % 2 ? "rgba(0,0,0,0)" : t.row); rr(x, right - HW, yy - 22, HW + 8, 44, 12); x.fill();
        if (on) { x.strokeStyle = t.gold; x.lineWidth = 2; rr(x, right - HW, yy - 22, HW + 8, 44, 12); x.stroke(); }
        x.fillStyle = on ? t.gold : t.txt; x.font = (on ? "700 " : "400 ") + "30px " + AF; x.textAlign = "center";
        x.direction = "ltr";
        for (var k = 0; k < 4; k++) { x.fillStyle = k === 0 ? t.gold : (on ? t.gold : t.txt); x.fillText(k ? ar(r[k]) : r[k], right - cols[k], yy + 2); }
        x.direction = "rtl";
      }
    });
    x.textAlign = "center"; x.fillStyle = t.mut; x.font = "400 26px " + AF; x.fillText("الإمساك = الفجر · الإفطار = المغرب", W / 2, 1122);
    footer(b); return b.c;
  }

  /* ---------- النافذة ---------- */
  var ui = null, cur = null;
  function css() {
    if (D.getElementById("shcss")) return;
    var s = D.createElement("style"); s.id = "shcss";
    s.textContent = "#shr{position:fixed;inset:0;z-index:90;background:rgba(0,0,0,.93);display:none;flex-direction:column;align-items:center;justify-content:center;gap:.7rem;padding:.8rem;font-family:GoogleSans,sans-serif;direction:rtl}" +
      "#shr.on{display:flex}#shr img{max-width:100%;max-height:62vh;border-radius:.9rem;box-shadow:0 8px 30px rgba(0,0,0,.5);background:#111}" +
      "#shr .shb2{display:flex;gap:.5rem;flex-wrap:wrap;justify-content:center;width:100%;max-width:26rem}" +
      "#shr button{flex:1;min-width:7rem;padding:.7rem .6rem;border-radius:.9rem;border:1px solid #d6b25e;background:rgba(214,178,94,.16);color:#f4f1e8;font:inherit;font-size:1rem}" +
      "#shr button.on{background:#d6b25e;color:#04170f;font-weight:700}#shr button:active{opacity:.7}#shr .shm{color:#f4f1e8;font-size:.85rem;min-height:1.2rem}" +
      ".shb{background:none;border:0;font-size:1.05rem;line-height:1;padding:.1rem .35rem;margin-inline-start:.3rem;cursor:pointer;opacity:.85}";
    D.head.appendChild(s);
  }
  function build() {
    if (ui) return;
    css();
    var r = D.createElement("div"); r.id = "shr";
    r.innerHTML = '<img id="shi" alt=""><div class="shb2"><button data-th="n">🌙 ليلي</button><button data-th="d">☀️ نهاري</button></div>' +
      '<div class="shb2"><button data-a="share"><svg viewBox="0 0 24 24" width="1.1em" height="1.1em" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-.2em"><circle cx="18" cy="5.5" r="2.8"/><circle cx="6" cy="12" r="2.8"/><circle cx="18" cy="18.5" r="2.8"/><path d="M8.5 10.7l7-4M8.5 13.3l7 4"/></svg> مشاركة</button><button data-a="save">💾 حفظ في المعرض</button><button data-a="close">✕ إغلاق</button></div><div class="shm" id="shm"></div>';
    D.body.appendChild(r);
    r.addEventListener("click", function (e) {
      var b = e.target.closest("button"); if (e.target === r) return hide();
      if (!b) return;
      if (b.dataset.th) { lsS("shth", b.dataset.th); render(); return; }
      var a = b.dataset.a;
      if (a === "close") hide(); else if (a === "share") act("share"); else if (a === "save") act("save");
    });
    ui = r;
  }
  function hide() { if (ui) ui.classList.remove("on"); }
  function msg(t) { var m = D.getElementById("shm"); if (m) { m.textContent = t; clearTimeout(msg.t); msg.t = setTimeout(function () { m.textContent = ""; }, 3000); } }
  function render() {
    if (!cur) return;
    var th = lsG("shth", "n") === "d" ? "d" : "n";
    [].forEach.call(ui.querySelectorAll("[data-th]"), function (b) { b.classList.toggle("on", b.dataset.th === th); });
    msg("جارٍ التصميم…");
    fonts().then(function () {
      var cv = cur.k === "v" ? drawVerse(cur.d, TH[th]) : cur.k === "z" ? drawDhikr(cur.d, TH[th]) : cur.k === "f" ? drawTafsir(cur.d, TH[th]) : cur.k === "i" ? drawImsak(cur.d, TH[th]) : drawTimes(cur.d, TH[th]);
      cur.url = cv.toDataURL("image/png");
      D.getElementById("shi").src = cur.url; msg("");
    });
  }
  function act(a) {
    if (!cur || !cur.url) return;
    var b64 = cur.url.split(",")[1], cap = "من تطبيق «" + APP + "»";
    try {
      if (window.App && App.shareImg) {
        if (a === "share") { var r = App.shareImg(b64, cap); if (r !== "ok") msg("تعذّرت المشاركة"); }
        else { var s = App.saveImg(b64); msg(s === "ok" ? "✅ حُفظت الصورة في المعرض" : s === "na" ? "الحفظ المباشر غير متاح على هذا الإصدار، استخدم المشاركة" : "تعذّر الحفظ"); }
      } else { // خارج التطبيق (متصفح): تنزيل الصورة
        var l = D.createElement("a"); l.href = cur.url; l.download = "card.png"; D.body.appendChild(l); l.click(); l.remove();
      }
    } catch (e) { msg("تعذّر التنفيذ"); }
  }
  function open(k, d) { build(); cur = { k: k, d: d, url: "" }; ui.classList.add("on"); render(); }

  if (D.head) css();
  window.ShareCard = {
    verse: function (d) { open("v", d); },
    dhikr: function (d) { open("z", d); },
    tafsir: function (d) { open("f", d); },
    times: function (d) { open("t", d); },
    imsak: function (d) { open("i", d); },
    close: hide,
    isOpen: function () { return !!(ui && ui.classList.contains("on")); }
  };
})();
