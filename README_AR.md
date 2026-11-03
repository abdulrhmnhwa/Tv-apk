# لوحة حمص عاصمة الثورة (تطبيق تلفاز عادي)

## البناء
ارفع كل محتويات المجلد على GitHub، ثم Actions > Build APK > نزّل app-debug.apk من Artifacts.
احذف النسخة القديمة قبل تثبيت الجديدة (التوقيع يتغير مع كل بناء).

## أزرار الريموت داخل التطبيق
- OK: إطفاء الشاشة (شاشة سوداء بإضاءة منخفضة). أي زر يرجّعها.
- السهم لأعلى: تشغيل أو إيقاف تكبير الأذان (الأيقونة 🔔 أعلى اليسار).
- السهم لأسفل: تجربة صوت التكبير.
- Back: خروج.

## الأذان
- عند كل صلاة (بدون الشروق) يوقظ الشاشة ويشغّل «الله أكبر».
- لصوت حقيقي ضع takbeer.mp3 في app/src/main/assets قبل البناء، وإلا يستخدم نطق الجهاز العربي.
- اختبار: adb shell am broadcast -a sy.homs.dash.ADHAN -n sy.homs.dash/.AdhanReceiver
- أوامر مرة واحدة بعد التثبيت (تساعد على الإيقاظ):
  adb shell appops set sy.homs.dash SYSTEM_ALERT_WINDOW allow
  adb shell cmd deviceidle whitelist +sy.homs.dash

## تعديل الواجهة
عدّل app/src/main/assets/index.html ثم أعد البناء.
