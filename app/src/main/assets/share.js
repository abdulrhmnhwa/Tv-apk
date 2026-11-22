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

  function fonts() {
    if (!D.fonts || !D.fonts.load) return Promise.resolve();
    var p = ["40px QuranFont", "700 40px QuranFont", "40px GoogleSans", "700 40px GoogleSans"].map(function (f) {
      return D.fonts.load(f, "اب1").catch(function () {});
    });
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
  function footer(b) {
    var x = b.x, t = b.t;
    x.strokeStyle = t.gold; x.globalAlpha = 0.6; x.lineWidth = 1.5;
    x.beginPath(); x.moveTo(W / 2 - 150, H - 190); x.lineTo(W / 2 + 150, H - 190); x.stroke(); x.globalAlpha = 1;
    crescent(x, W / 2, H - 150, 20, t.gold);
    x.textAlign = "center"; x.fillStyle = t.gold; x.font = "700 40px " + AF; x.fillText(APP, W / 2, H - 100);
  }
  function pill(b, text, y, font, maxW) {
    var x = b.x, t = b.t; x.font = font; var w = Math.min(maxW || 900, x.measureText(text).width + 90), h = 84;
    x.fillStyle = t.pill; rr(x, W / 2 - w / 2, y - h / 2, w, h, 42); x.fill();
    x.strokeStyle = t.gold; x.lineWidth = 2; rr(x, W / 2 - w / 2, y - h / 2, w, h, 42); x.stroke();
    x.textAlign = "center"; x.fillStyle = t.gold; x.fillText(text, W / 2, y + 3, w - 40);
  }
  function wrap(x, text, maxW) {
    var words = String(text).replace(/\s+/g, " ").trim().split(" "), lines = [], cur = "";
    words.forEach(function (w) {
      var tr = cur ? cur + " " + w : w;
      if (cur && x.measureText(tr).width > maxW) { lines.push(cur); cur = w; } else cur = tr;
    });
    if (cur) lines.push(cur);
    return lines;
  }
  /* يختار أكبر خط يتّسع النص به داخل المساحة، وإن لم يتّسع يقتطع بنقاط */
  function fit(x, text, face, weight, maxW, maxH, from, to, lh) {
    for (var s = from; s >= to; s -= 2) {
      x.font = weight + " " + s + "px " + face;
      var L = wrap(x, text, maxW);
      if (L.length * s * lh <= maxH) return { s: s, L: L, lh: lh };
    }
    x.font = weight + " " + to + "px " + face;
    var L2 = wrap(x, text, maxW), mx = Math.max(1, Math.floor(maxH / (to * lh)));
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
    d = { t: d.t, a: noSrc(d.a) || d.a, s: noSrc(d.s), c: d.c };
    var b = base(t), x = b.x;
    pill(b, d.t || "ذكر", 170, "700 42px " + AF);
    var hasS = d.s && d.s.trim();
    var f = fit(x, d.a, QF, "400", 860, hasS ? 560 : 720, 62, 28, 1.9);
    drawLines(b, f, QF, "400", 250, hasS ? 580 : 760, t.txt);
    var y = hasS ? 860 : 1030;
    if (d.c) { x.textAlign = "center"; x.fillStyle = t.gold; x.font = "700 34px " + AF; x.fillText("التكرار: " + d.c, W / 2, y); y += 70; }
    if (hasS) {
      var g = fit(x, d.s, AF, "400", 820, 1130 - y, 30, 22, 1.65);
      drawLines(b, g, AF, "400", y - 15, 1130 - y + 15, t.mut);
    }
    footer(b); return b.c;
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
      var cv = cur.k === "v" ? drawVerse(cur.d, TH[th]) : cur.k === "z" ? drawDhikr(cur.d, TH[th]) : drawTimes(cur.d, TH[th]);
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
    times: function (d) { open("t", d); },
    close: hide,
    isOpen: function () { return !!(ui && ui.classList.contains("on")); }
  };
})();
