/*
 * قارئ المصحف داخل التطبيق (يعمل بدون إنترنت)
 * النص: مشروع تنزيل Tanzil Project (نسخة Simple 1.1) — ترخيص CC BY 3.0 — tanzil.net
 * النص يُعرض كما هو دون أي تعديل في حروفه؛ تُفصل البسملة فقط في رأس السورة عند العرض.
 * الصفحات والأجزاء: ترقيم مصحف المدينة (604 صفحة) من بيانات تنزيل.
 * ما يحتاج إنترنت (اختياري): الاستماع للآية، والتفسير والترجمة على موقع تنزيل.
 */
(function () {
  "use strict";
  var D = document;
  function $(i) { return D.getElementById(i); }
  var Q = null, M = null, FS, FA, FT, NT = null, PI, BASE, SAJ = {}, QTR = {}, JZ, BIS = "";
  var st = { pg: 1, fs: 1, sel: -1, tab: "s", opts: null, playing: -1, built: false };
  var bm = {}, au = null, toastT = 0;
  var RC = "Alafasy_128kbps";
  var SIZES = [0.85, 1, 1.15, 1.3, 1.5, 1.75];
  var AR = "٠١٢٣٤٥٦٧٨٩";

  function lsG(k, d) { try { var v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } }
  function lsS(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} }
  function ar(n) { return String(n).replace(/\d/g, function (d) { return AR.charAt(+d); }); }
  function esc(t) { return String(t).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }
  function p3(n) { return ("00" + n).slice(-3); }

  var RX = /[ؐ-ًؚ-ٰٟۖ-ۭـ]/g;
  function norm(t) {
    return t.replace(RX, "").replace(/[أإآٱ]/g, "ا").replace(/ى/g, "ي").replace(/ة/g, "ه").replace(/ؤ/g, "و").replace(/ئ/g, "ي");
  }

  /* ---------- البيانات ---------- */
  function gi(s, a) { return BASE[s - 1] + a - 1; }
  function pageOf(i) {
    var lo = 0, hi = 603;
    while (lo < hi) { var m = (lo + hi + 1) >> 1; if (PI[m] <= i) lo = m; else hi = m - 1; }
    return lo + 1;
  }
  function juzOf(i) { var j = 1; for (var k = 0; k < 30; k++) if (JZ[k] <= i) j = k + 1; return j; }
  /* نص الآية للعرض: في أول آية من كل سورة (عدا الفاتحة والتوبة) تأتي البسملة في أول النص فتُفصل لرأس السورة */
  function body(i) {
    var t = FT[i], s = FS[i];
    if (FA[i] === 1 && s !== 1 && s !== 9) {
      var w = t.split(" ");
      if (norm(w[0]) === "بسم" && w.length > 4) t = w.slice(4).join(" ");
    }
    return t;
  }
  function prep() {
    Q = window.QTXT; M = window.QMETA;
    FS = []; FA = []; FT = []; BASE = [];
    var g = 0;
    for (var s = 0; s < 114; s++) {
      BASE[s] = g;
      for (var a = 0; a < Q[s].length; a++) { FS.push(s + 1); FA.push(a + 1); FT.push(Q[s][a]); g++; }
    }
    BIS = FT[0];
    PI = M.p.map(function (p) { return gi(p[0], p[1]); });
    M.sj.forEach(function (x) { SAJ[gi(x[0], x[1])] = 1; });
    M.q.forEach(function (x) { QTR[gi(x[0], x[1])] = 1; });
    JZ = M.j.map(function (x) { return gi(x[0], x[1]); });
    (lsG("qrb", []) || []).forEach(function (i) { bm[i] = 1; });
  }
  function loadData(cb) {
    if (Q && M) { cb(); return; }
    var n = 0, bad = 0;
    function ok() { if (++n === 2 && !bad) { prep(); cb(); } }
    ["quran-text.js", "quran-meta.js"].forEach(function (f) {
      var s = D.createElement("script");
      s.src = "quran/" + f;
      s.onload = ok;
      s.onerror = function () { bad = 1; toast("تعذّر تحميل نص المصحف"); close(); };
      D.head.appendChild(s);
    });
  }

  /* ---------- واجهة ---------- */
  function build() {
    if (st.built) return;
    st.built = true;
    var r = D.createElement("div");
    r.id = "qr";
    r.setAttribute("dir", "rtl");
    r.innerHTML =
      '<div class="qh"><div class="qhr">' +
      '<button class="qi" id="qrc" aria-label="إغلاق">✕</button>' +
      '<div class="qt"><b id="qrt"></b><small id="qrs"></small></div>' +
      '<button class="qi" id="qrq" aria-label="بحث">🔍</button>' +
      '<button class="qi" id="qri" aria-label="الفهرس">☰</button>' +
      '<button class="qi" id="qrz" aria-label="حجم الخط" style="font-size:1rem;font-weight:700">Aa</button>' +
      '<button class="qi" id="qrw" aria-label="موقع تنزيل (إنترنت)">🌐</button>' +
      '</div></div>' +
      '<div class="qk" id="qrk"><span id="qrkt"></span><button class="btn" id="qrkd">✓ انتهيت هنا</button></div>' +
      '<div class="qb" id="qrb"><div class="qfr"><div class="qpg" id="qrp"></div></div>' +
      '<div class="qcr">نص المصحف: <button id="qrcr">مشروع تنزيل Tanzil Project</button> · ترخيص CC BY 3.0<br>الترقيم: مصحف المدينة (٦٠٤ صفحة)</div></div>' +
      '<div class="qpl" id="qrpl"><span id="qrpt"></span><button id="qrps">⏹ إيقاف</button></div>' +
      '<div class="qf"><button class="qnb" id="qrpv">‹ السابقة</button>' +
      '<div class="qpn"><span>صفحة</span><input id="qrg" type="number" min="1" max="604" inputmode="numeric"><span>/ ٦٠٤</span></div>' +
      '<button class="qnb" id="qrnx">التالية ›</button></div>' +
      '<div class="qpa" id="qra"><div class="qtabs"><button data-t="s">السور</button><button data-t="j">الأجزاء</button><button data-t="f">بحث</button><button data-t="b">العلامات</button><button class="qx" id="qrax" aria-label="إغلاق">✕</button></div><div class="qpc" id="qrpc"></div></div>' +
      '<div class="qsd" id="qrd"><div class="qsx" id="qrx"></div></div>' +
      '<div class="qtoast" id="qrto"></div>';
    D.body.appendChild(r);
    st.fs = lsG("qrfs", 1);
    if (SIZES.indexOf(st.fs) < 0) st.fs = 1;

    $("qrc").onclick = close;
    $("qrkd").onclick = close;
    $("qrq").onclick = function () { openPanel("f"); };
    $("qri").onclick = function () { openPanel("s"); };
    $("qrax").onclick = closePanel;
    $("qrz").onclick = function () {
      var k = (SIZES.indexOf(st.fs) + 1) % SIZES.length;
      st.fs = SIZES[k]; lsS("qrfs", st.fs);
      fit();
      toast(st.fs === 1 ? "حجم المصحف (١٥ سطراً)" : "حجم الخط " + Math.round(st.fs * 100) + "%");
    };
    $("qrw").onclick = function () { site(PI[st.pg - 1]); };
    $("qrcr").onclick = function () { site(-1); };
    $("qrpv").onclick = function () { go(st.pg - 1, false, -1); };
    $("qrnx").onclick = function () { go(st.pg + 1, false, 1); };
    $("qrg").onchange = function () { var v = parseInt(this.value, 10); if (v >= 1 && v <= 604) go(v); else this.value = st.pg; };
    $("qrps").onclick = function () { stop(); };
    $("qrp").onclick = function (e) {
      var v = e.target.closest ? e.target.closest(".qv") : null;
      if (v) openSheet(+v.getAttribute("data-i"));
    };
    $("qrd").onclick = function (e) { if (e.target === this) closeSheet(); };
    $("qrx").onclick = onSheet;
    $("qra").querySelector(".qtabs").onclick = function (e) {
      var b = e.target.closest("button[data-t]");
      if (b) openPanel(b.getAttribute("data-t"));
    };
    $("qrpc").onclick = onPanel;
    $("qrpc").oninput = onPanelInput;
    // السحب بين الصفحات: السحب لليمين = الصفحة التالية (كالمصحف)
    var tx = 0, ty = 0, tt = 0;
    var b = $("qrb");
    b.addEventListener("touchstart", function (e) { var t = e.touches[0]; tx = t.clientX; ty = t.clientY; tt = Date.now(); }, { passive: true });
    b.addEventListener("touchend", function (e) {
      var t = e.changedTouches[0], dx = t.clientX - tx, dy = t.clientY - ty;
      if (Date.now() - tt > 700 || Math.abs(dx) < 70 || Math.abs(dx) < Math.abs(dy) * 1.8) return;
      go(st.pg + (dx > 0 ? 1 : -1), false, dx > 0 ? 1 : -1);
    }, { passive: true });
  }

  function toast(m) {
    var t = $("qrto");
    if (!t) return;
    t.textContent = m; t.className = "qtoast on";
    clearTimeout(toastT);
    toastT = setTimeout(function () { t.className = "qtoast"; }, 1900);
  }

  function pageHTML(p) {
    var a = PI[p - 1], b = p < 604 ? PI[p] : FT.length, o = "";
    for (var i = a; i < b; i++) {
      var s = FS[i], ay = FA[i];
      if (ay === 1) {
        var m = M.s[s - 1];
        o += '<span class="qsh"><i>۞</i>سورة ' + esc(m[0]) + "<i>۞</i></span>";
        if (s !== 1 && s !== 9) o += '<span class="qbs">' + esc(BIS) + "</span>";
      }
      if (QTR[i] && i > 0) o += '<span class="qq">۞</span>';
      o += '<span class="qv' + (bm[i] ? " bm" : "") + (st.playing === i ? " pl" : "") + (st.sel === i ? " sel" : "") + '" data-i="' + i + '">' +
        esc(body(i)) + (SAJ[i] ? ' <span class="qsj">۩</span>' : "") + ' <span class="qn">\u06DD' + ar(ay) + "</span></span> ";
    }
    return o;
  }

  function setHead() {
    var a = PI[st.pg - 1], b = (st.pg < 604 ? PI[st.pg] : FT.length) - 1;
    var s1 = M.s[FS[a] - 1][0], s2 = M.s[FS[b] - 1][0];
    $("qrt").textContent = "سورة " + s1 + (s2 !== s1 ? " – " + s2 : "");
    $("qrs").textContent = "الصفحة " + ar(st.pg) + " · الجزء " + ar(juzOf(a));
    $("qrg").value = st.pg;
    $("qrpv").disabled = st.pg <= 1;
    $("qrnx").disabled = st.pg >= 604;
  }

  /* ---------- ضبط الصفحة كالمصحف: ١٥ سطراً مضبوطة الحواف ---------- */
  var LH = 2, baseCache = {};
  function rowsAt(el, fs) { el.style.fontSize = fs + "px"; return Math.round(el.offsetHeight / (fs * LH)); }
  function bestFs(el, target) {
    var lo = 9, hi = Math.max(14, Math.min(44, el.clientWidth / 7.5)), best = lo;
    for (var k = 0; k < 13; k++) {
      var mid = (lo + hi) / 2;
      if (rowsAt(el, mid) <= target) { best = mid; lo = mid; } else hi = mid;
    }
    return best;
  }
  function fit() {
    var el = $("qrp");
    if (!el || !el.clientWidth || !M) return;
    var w = el.clientWidth, p = st.pg, fs, target = 15;
    el.style.paddingTop = el.style.paddingBottom = "0";
    if (p <= 2) {
      // الصفحتان الأولى والثانية بخط الصفحات العادية، والنص في وسط الإطار
      if (!baseCache[w]) {
        var keep = el.innerHTML;
        el.innerHTML = pageHTML(3);
        baseCache[w] = bestFs(el, target);
        el.innerHTML = keep;
      }
      fs = baseCache[w];
    } else {
      fs = bestFs(el, target);
      baseCache[w] = fs;
    }
    var n = rowsAt(el, fs);
    var full = n >= target;
    el.style.textAlignLast = full ? "justify" : "center";
    if (p <= 2 && n < target) {
      var pad = ((target - n) / 2) * fs * LH;
      el.style.paddingTop = el.style.paddingBottom = pad + "px";
    }
    el.style.fontSize = (fs * st.fs) + "px";
  }
  function fontsThenFit(pg) {
    try {
      if (D.fonts && D.fonts.load) D.fonts.load('20px "QuranFont"', "\u0628\u06DD\u0661").then(function () { if (st.pg === pg) { baseCache = {}; fit(); } }, function () {});
    } catch (e) {}
  }
  var rsT = 0;
  window.addEventListener("resize", function () {
    clearTimeout(rsT);
    rsT = setTimeout(function () { var ov = $("qr"); if (ov && ov.className === "on" && M) { baseCache = {}; fit(); } }, 150);
  });

  var ghost = null, anims = [];
  function clearGhost() {
    anims.forEach(function (x) { try { x.cancel(); } catch (e) {} });
    anims = [];
    if (ghost) { ghost.remove(); ghost = null; }
  }
  /* انتقال الصفحة بالسحب: dir=1 الصفحة التالية تدخل من اليسار، dir=-1 السابقة تدخل من اليمين */
  function slide(dir, render) {
    var fr = $("qrb").querySelector(".qfr");
    var calm = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!dir || calm || !fr.animate) { render(); return; }
    clearGhost();
    var r = fr.getBoundingClientRect(), W = window.innerWidth + 20;
    ghost = fr.cloneNode(true);
    var gp = ghost.querySelector("#qrp");
    if (gp) gp.removeAttribute("id");
    ghost.className = "qfr qgh";
    ghost.style.cssText = "position:absolute;margin:0;pointer-events:none;z-index:1;left:" + r.left + "px;top:" + r.top + "px;width:" + r.width + "px";
    $("qr").appendChild(ghost);
    render();
    var o = { duration: 320, easing: "cubic-bezier(.22,.8,.3,1)", fill: "both" }, g = ghost;
    var a1 = g.animate([{ transform: "translateX(0)" }, { transform: "translateX(" + (dir > 0 ? W : -W) + "px)" }], o);
    var a2 = fr.animate([{ transform: "translateX(" + (dir > 0 ? -W : W) + "px)" }, { transform: "translateX(0)" }], o);
    anims = [a1, a2];
    a1.onfinish = function () { if (ghost === g) { g.remove(); ghost = null; } try { a2.cancel(); } catch (e) {} anims = []; };
  }

  function go(p, keepScroll, dir) {
    p = Math.max(1, Math.min(604, p | 0));
    if (p === st.pg && dir) return;
    st.pg = p;
    st.sel = -1;
    slide(dir, function () {
      $("qrp").innerHTML = pageHTML(p);
      fit();
      fontsThenFit(p);
      if (!keepScroll) $("qrb").scrollTop = 0;
      setHead();
      lsS("qrp", p);
    });
  }

  function goVerse(i) {
    go(pageOf(i));
    var v = $("qrp").querySelector('.qv[data-i="' + i + '"]');
    if (v) {
      v.classList.add("sel");
      st.sel = i;
      v.scrollIntoView({ block: "center" });
      setTimeout(function () { v.classList.remove("sel"); if (st.sel === i) st.sel = -1; }, 2200);
    }
  }

  /* ---------- قائمة الآية ---------- */
  function openSheet(i) {
    st.sel = i;
    mark();
    var s = FS[i], a = FA[i];
    $("qrx").innerHTML =
      "<h3>سورة " + esc(M.s[s - 1][0]) + " · الآية " + ar(a) + '</h3><div class="qvt">' + esc(body(i)) + "</div>" +
      '<div class="qsg">' +
      '<button data-a="play">' + (st.playing === i ? "⏹ إيقاف الصوت" : "🔊 استماع") + "<small>يحتاج إنترنت</small></button>" +
      '<button data-a="bm">' + (bm[i] ? "🔖 إزالة العلامة" : "🔖 علامة مرجعية") + "</button>" +
      '<button data-a="copy">📋 نسخ الآية</button>' +
      '<button data-a="site">🌐 تفسير وترجمة<small>على موقع تنزيل · إنترنت</small></button>' +
      "</div>";
    $("qrd").className = "qsd on";
  }
  function closeSheet() {
    $("qrd").className = "qsd";
    st.sel = -1;
    mark();
  }
  function onSheet(e) {
    var b = e.target.closest("button[data-a]");
    if (!b) return;
    var i = st.sel, k = b.getAttribute("data-a");
    if (i < 0) return;
    if (k === "play") { var was = st.playing === i; closeSheet(); if (was) stop(); else play(i); return; }
    if (k === "bm") {
      if (bm[i]) delete bm[i]; else bm[i] = 1;
      lsS("qrb", Object.keys(bm).map(Number));
      toast(bm[i] ? "تمت إضافة العلامة" : "أُزيلت العلامة");
      closeSheet(); go(st.pg, true); return;
    }
    if (k === "copy") {
      copy(body(i) + " ﴿" + ar(FA[i]) + "﴾ — سورة " + M.s[FS[i] - 1][0]);
      closeSheet(); return;
    }
    if (k === "site") { closeSheet(); site(i); }
  }
  function mark() {
    [].forEach.call($("qrp").querySelectorAll(".qv"), function (v) {
      var i = +v.getAttribute("data-i");
      v.classList.toggle("sel", st.sel === i);
      v.classList.toggle("pl", st.playing === i);
    });
  }
  function copy(t) {
    function fb() {
      var x = D.createElement("textarea");
      x.value = t; x.style.cssText = "position:fixed;opacity:0;top:0;left:0";
      D.body.appendChild(x); x.select();
      try { D.execCommand("copy"); toast("تم النسخ"); } catch (e) { toast("تعذّر النسخ"); }
      x.remove();
    }
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) { navigator.clipboard.writeText(t).then(function () { toast("تم النسخ"); }, fb); return; }
    } catch (e) {}
    fb();
  }

  /* ---------- أونلاين: الموقع والصوت ---------- */
  function site(i) {
    var u = "https://tanzil.net/" + (i >= 0 ? "#" + FS[i] + ":" + FA[i] : "");
    try {
      if (window.App && App.openSite) App.openSite(u);
      else if (window.App && App.openWeb) App.openWeb(u);
      else toast("يتطلب نسخة التطبيق");
    } catch (e) { toast("تعذّر فتح الموقع"); }
  }
  function play(i) {
    stop(true);
    st.playing = i;
    var url = "https://everyayah.com/data/" + RC + "/" + p3(FS[i]) + p3(FA[i]) + ".mp3";
    try {
      au = new Audio(url);
      au.onended = function () {
        var n = i + 1;
        if (st.playing === i && n < FT.length) { if (pageOf(n) !== st.pg) go(pageOf(n), false, 1); play(n); } else stop();
      };
      au.onerror = function () { if (st.playing === i) { toast("تعذّر تشغيل الصوت — تحتاج اتصالاً بالإنترنت"); stop(); } };
      var pr = au.play();
      if (pr && pr.catch) pr.catch(function () {});
    } catch (e) { stop(); return; }
    $("qrpt").textContent = "🔊 سورة " + M.s[FS[i] - 1][0] + " · الآية " + ar(FA[i]);
    $("qrpl").className = "qpl on";
    mark();
    var v = $("qrp").querySelector('.qv[data-i="' + i + '"]');
    if (v && v.scrollIntoView) v.scrollIntoView({ block: "center", behavior: "smooth" });
  }
  function stop(quiet) {
    if (au) { au.onended = au.onerror = null; try { au.pause(); } catch (e) {} au = null; }
    st.playing = -1;
    if (!quiet) { $("qrpl").className = "qpl"; mark(); }
  }

  /* ---------- الفهرس والبحث ---------- */
  function openPanel(t) {
    st.tab = t;
    $("qra").className = "qpa on";
    [].forEach.call($("qra").querySelectorAll(".qtabs button[data-t]"), function (b) { b.classList.toggle("on", b.getAttribute("data-t") === t); });
    var c = $("qrpc"), o = "";
    if (t === "s") {
      o = '<input class="qin" id="qrfl" placeholder="ابحث باسم السورة أو رقمها…" autocomplete="off">' + '<div id="qrls">' + surasHTML("") + "</div>";
    } else if (t === "j") {
      for (var j = 0; j < 30; j++) {
        var i = JZ[j];
        o += '<button class="qrw" data-g="' + i + '"><span class="nb">' + ar(j + 1) + '</span><span class="nm">الجزء ' + ar(j + 1) + "<small>" + esc(M.s[FS[i] - 1][0]) + " · الآية " + ar(FA[i]) + '</small></span><span class="pg">ص ' + ar(pageOf(i)) + "</span></button>";
      }
    } else if (t === "f") {
      o = '<input class="qin" id="qrsq" placeholder="اكتب كلمة أو آية للبحث (بدون تشكيل)…" autocomplete="off"><div id="qrsr"><div class="qem">البحث يعمل بدون إنترنت في كامل المصحف</div></div>';
    } else {
      var ks = Object.keys(bm).map(Number).sort(function (a, b) { return a - b; });
      o = ks.length ? ks.map(function (i) {
        return '<div class="qrw" data-g="' + i + '"><span class="nb">🔖</span><span class="nm">' + esc(M.s[FS[i] - 1][0]) + " · الآية " + ar(FA[i]) + '<small class="sn">' + esc(body(i).slice(0, 70)) + '</small></span><span class="pg">ص ' + ar(pageOf(i)) + '</span><button class="pg" data-del="' + i + '" aria-label="حذف" style="font-size:1.1rem;padding:.2rem .5rem">✕</button></div>';
      }).join("") : '<div class="qem">لا توجد علامات بعد.<br>اضغط على أي آية ثم «علامة مرجعية».</div>';
    }
    c.innerHTML = o;
    c.scrollTop = 0;
    if (t === "f") setTimeout(function () { var q = $("qrsq"); if (q) q.focus(); }, 60);
  }
  function closePanel() { $("qra").className = "qpa"; }
  function surasHTML(f) {
    var q = norm(f.trim()), o = "";
    for (var s = 0; s < 114; s++) {
      var m = M.s[s];
      if (q && norm(m[0]).indexOf(q) < 0 && String(s + 1) !== q && m[1].toLowerCase().indexOf(q.toLowerCase()) < 0) continue;
      o += '<button class="qrw" data-g="' + gi(s + 1, 1) + '"><span class="nb">' + ar(s + 1) + '</span><span class="nm">' + esc(m[0]) + "<small>" + (m[3] === "M" ? "مكية" : "مدنية") + " · " + ar(m[4]) + ' آية</small></span><span class="pg">ص ' + ar(pageOf(gi(s + 1, 1))) + "</span></button>";
    }
    return o || '<div class="qem">لا نتائج</div>';
  }
  var searchT = 0;
  function onPanelInput(e) {
    var t = e.target;
    if (t.id === "qrfl") { $("qrls").innerHTML = surasHTML(t.value); return; }
    if (t.id === "qrsq") { clearTimeout(searchT); searchT = setTimeout(function () { search(t.value); }, 250); }
  }
  function search(v) {
    var box = $("qrsr");
    if (!box) return;
    var q = norm(v).replace(/\s+/g, " ").trim();
    if (q.length < 2) { box.innerHTML = '<div class="qem">اكتب حرفين على الأقل</div>'; return; }
    if (!NT) NT = FT.map(function (_, i) { return norm(body(i)); });
    var ws = q.split(" "), hits = [], i, k;
    for (i = 0; i < NT.length; i++) {
      var ok = true;
      for (k = 0; k < ws.length; k++) if (NT[i].indexOf(ws[k]) < 0) { ok = false; break; }
      if (ok) hits.push(i);
    }
    if (!hits.length) { box.innerHTML = '<div class="qem">لا نتائج لـ «' + esc(v) + "»</div>"; return; }
    var o = '<div class="qem" style="padding:.4rem">' + ar(hits.length) + " نتيجة" + (hits.length > 80 ? " (أول ٨٠)" : "") + "</div>";
    hits.slice(0, 80).forEach(function (i) {
      o += '<button class="qrw" data-g="' + i + '"><span class="nm sn">' + esc(body(i)) + "<small>" + esc(M.s[FS[i] - 1][0]) + " · الآية " + ar(FA[i]) + '</small></span><span class="pg">ص ' + ar(pageOf(i)) + "</span></button>";
    });
    box.innerHTML = o;
  }
  function onPanel(e) {
    var d = e.target.closest("[data-del]");
    if (d) {
      e.stopPropagation();
      delete bm[+d.getAttribute("data-del")];
      lsS("qrb", Object.keys(bm).map(Number));
      openPanel("b");
      return;
    }
    var g = e.target.closest("[data-g]");
    if (g) { closePanel(); goVerse(+g.getAttribute("data-g")); }
  }

  /* ---------- فتح وإغلاق ---------- */
  function open(opts) {
    opts = opts || {};
    st.opts = opts;
    build();
    var ov = $("qr");
    ov.className = "on";
    if (!Q) $("qrp").innerHTML = '<div class="qld">جارٍ تحميل المصحف…</div>';
    loadData(function () {
      if (opts.sura) goVerse(gi(opts.sura, 1)); // يفتح على أول آية من السورة مع تمييزها لحظة
      else go(opts.page || lsG("qrp", 1));
      var k = $("qrk");
      if (opts.khatma) { $("qrkt").textContent = "📖 " + opts.khatma; k.className = "qk on"; } else k.className = "qk";
    });
  }
  function close() {
    var ov = $("qr");
    if (!ov || ov.className !== "on") return;
    stop(); clearGhost();
    closeSheet(); closePanel();
    ov.className = "";
    var o = st.opts; st.opts = null;
    if (o && o.onClose) { try { o.onClose(st.pg); } catch (e) {} }
  }
  function back() {
    var ov = $("qr");
    if (!ov || ov.className !== "on") return false;
    if ($("qrd").className === "qsd on") { closeSheet(); return true; }
    if ($("qra").className === "qpa on") { closePanel(); return true; }
    close();
    return true;
  }

  window.QR = { open: open, close: close, back: back, page: function () { return st.pg; }, go: function (p) { if (Q) go(p); } };
})();
