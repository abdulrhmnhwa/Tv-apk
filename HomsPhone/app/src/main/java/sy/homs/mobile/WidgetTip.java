package sy.homs.mobile;

/**
 * يختار «تذكيراً» أو ذكراً لشريط الودجت بحسب الوقت واليوم والطقس.
 * منطق صرف بلا أي اعتماد على أندرويد (لسهولة الاختبار).
 * القيمة المُرجَعة: {العنوان، النص، مفتاح القسم الذي يفتحه الضغط}
 * المفتاح: "" يفتح التطبيق فقط، "tab" تبويب الأذكار، وغير ذلك مفتاح قسم (m, e, sleep, salah …)
 */
public class WidgetTip {
    static final long MIN = 60000L;
    static final int MK = 20;          // دقائق وقت الكراهة بعد الشروق
    static final int DH_LEN = 60;      // مدة إظهار تذكير الضحى
    static final int DH_GAP = 25;      // الضحى ينتهي قبل الظهر بهذه الدقائق
    static final int SLEEP_AFTER = 40; // أذكار النوم بعد العشاء بهذه الدقائق
    static final int NIGHT_BEFORE = 90;// الثلث الأخير: قبل الفجر بهذه الدقائق
    static final int POST_LEN = 20;    // مدة إظهار «أذكار بعد الصلاة»
    static final int SAT = 7, SUN = 1, MON = 2, THU = 5, FRI = 6; // قيم Calendar.DAY_OF_WEEK

    static final String[][] GEN = {
        {"📿 ذكر", "سبحان الله وبحمده، سبحان الله العظيم — كلمتان خفيفتان على اللسان ثقيلتان في الميزان"},
        {"📿 ذكر", "لا حول ولا قوة إلا بالله — كنز من كنوز الجنة"},
        {"📿 ذكر", "أحبّ الكلام إلى الله: سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر"},
        {"🤍 صلاة على النبي", "اللهم صلِّ وسلِّم على نبينا محمد — من صلّى عليه مرة صلّى الله عليه بها عشراً"},
        {"🤲 استغفار", "أستغفر الله وأتوب إليه"},
        {"🤲 دعاء", "ربَّنا آتِنا في الدنيا حسنة وفي الآخرة حسنة وقِنا عذاب النار"},
        {"🤲 دعاء", "اللهم إنك عفوّ تحب العفو فاعفُ عني"},
        {"📿 ذكر", "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير"},
        {"🤲 دعاء", "رب اغفر لي وتب عليّ إنك أنت التواب الرحيم"},
        {"📿 ذكر", "حسبنا الله ونعم الوكيل"}
    };

    static final String[][] FRIDAY = {
        {"🕌 يوم الجمعة", "اقرأ سورة الكهف — يضيء لك من النور ما بين الجمعتين", "tab"},
        {"🕌 يوم الجمعة", "أكثِروا من الصلاة على النبي صلى الله عليه وسلم في هذا اليوم", "tab"},
        {"🕌 يوم الجمعة", "في الجمعة ساعة إجابة — أكثِر من الدعاء", "tab"}
    };

    static long dhStart(long[] r) { return r[1] + MK * MIN; }
    static long dhEnd(long[] r) { return Math.min(dhStart(r) + DH_LEN * MIN, r[2] - DH_GAP * MIN); }
    static long postStart(long[] r, int[] iq, int i) { return r[i] + (iq[i] > 0 ? iq[i] : 10) * MIN; }

    static final String[] NM = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    /** يمنع بعض خطوط أندرويد من دمج «الله» في حرف ملصق يكرّر الشدّة والفتحة (ZWNJ بعد الألف، لا يغيّر الشكل) */
    static String fix(String s) {
        return s.replaceAll("([\\u0627\\u0671])(?=\\u0644[\\u064B-\\u065F\\u0670]*\\u0644[\\u064B-\\u065F\\u0670]*\\u0647)", "$1\u200C");
    }

    static String[] t(String a, String b, String c) { return new String[]{fix(a), fix(b), c}; }

    /**
     * @param now  الوقت الحالي (ميلي ثانية)
     * @param r    أوقات اليوم: الفجر، الشروق، الظهر، العصر، المغرب، العشاء (ميلي ثانية)
     * @param iq   دقائق الإقامة لكل صلاة (فهرس 0..5)
     * @param dow  يوم الأسبوع بتوقيت سوريا (قيم Calendar.DAY_OF_WEEK)
     * @param code رمز الطقس (WMO) أو -1 إن لم يتوفر
     * @param temp الحرارة المئوية
     */
    static String[] pick(long now, long[] r, int[] iq, int dow, int code, int temp) {
        int slot = (int) ((now / (30 * MIN)) % 100000);

        // 1) نوافذ الأذكار والصلوات
        if (now >= r[0] && now < dhStart(r))
            return t("☀️ أذكار الصباح", "حان وقت أذكار الصباح — حصّن نفسك بآية الكرسي والمعوذات", "m");
        if (now >= dhStart(r) && now < dhEnd(r) && now < dhStart(r) + DH_LEN * MIN)
            return t("🌤️ صلاة الضحى", "حان وقت صلاة الضحى — ركعتان فأكثر تجزئ عن صدقة كل مفصل في الجسم", "");
        if (now >= r[3] && now < r[4])
            return t("🌙 أذكار المساء", "حان وقت أذكار المساء — يمتد وقتها حتى غروب الشمس", "e");
        if (now >= r[5] + SLEEP_AFTER * MIN || now < r[0] - NIGHT_BEFORE * MIN)
            return t("😴 أذكار النوم", "لا تنسَ أذكار النوم — آية الكرسي والمعوذات وخواتيم البقرة", "sleep");
        if (now >= r[0] - NIGHT_BEFORE * MIN && now < r[0])
            return t("🌌 الثلث الأخير من الليل", "ينزل ربنا إلى السماء الدنيا فيقول: من يدعوني فأستجيب له، من يستغفرني فأغفر له", "tab");
        int[] pp = {2, 4, 5};
        for (int i : pp) {
            long s = postStart(r, iq, i);
            if (now >= s && now < s + POST_LEN * MIN)
                return t("🤲 بعد الصلاة", "أذكار بعد صلاة " + NM[i] + " — التسبيح والتحميد والتكبير ثلاثاً وثلاثين", "salah");
        }

        // 2) الطقس
        if (code >= 95)
            return t("⛈️ رعد وبرق", "سبحان الذي يسبّح الرعد بحمده والملائكة من خيفته", "misc");
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82))
            return t("🌧️ مطر", "اللهم صيّباً نافعاً — وبعد المطر: مُطرنا بفضل الله ورحمته", "misc");
        if (code >= 0 && temp <= 6 && slot % 2 == 0)
            return t("❄️ برد شديد", "إسباغ الوضوء على المكاره يمحو الله به الخطايا ويرفع به الدرجات", "wudu");
        if (code >= 0 && temp >= 36 && now < r[2] && slot % 2 == 0)
            return t("🔥 اشتدّ الحر", "يُستحب الإبراد بصلاة الظهر عند شدة الحر، وأكثِر من الحمد على نعمة الظل والماء", "");

        // 3) اليوم
        if (dow == FRI) { String[] f = FRIDAY[slot % FRIDAY.length]; return t(f[0], f[1], f[2]); }
        if ((dow == MON || dow == THU) && now < r[3] && slot % 3 == 0)
            return t("🌙 صيام الاثنين والخميس", "كان النبي صلى الله عليه وسلم يتحرّى صيام الاثنين والخميس", "tab");

        // 4) ذكر عام متجدّد
        String[] g = GEN[slot % GEN.length];
        return t(g[0], g[1], "tab");
    }

    /** أقرب لحظة بعد now يتغيّر عندها اختيار الشريط (نوافذ الأوقات)، أو Long.MAX_VALUE */
    static long nextBoundary(long now, long[][] days, int[] iq) {
        long best = Long.MAX_VALUE;
        for (long[] r : days) {
            if (r == null) continue;
            long[] b = {
                r[0] - NIGHT_BEFORE * MIN, r[0], dhStart(r), dhEnd(r), r[3], r[4], r[5] + SLEEP_AFTER * MIN,
                postStart(r, iq, 2), postStart(r, iq, 2) + POST_LEN * MIN,
                postStart(r, iq, 4), postStart(r, iq, 4) + POST_LEN * MIN,
                postStart(r, iq, 5), postStart(r, iq, 5) + POST_LEN * MIN
            };
            for (long x : b) if (x > now && x < best) best = x;
        }
        return best;
    }

    /** وقت تذكير أذكار الصباح: بعد الفجر بنصف ساعة، وقبل الشروق بعشر دقائق على الأقل */
    static long morningAt(long[] r) { return Math.min(r[0] + 30 * MIN, r[1] - 10 * MIN); }
    /** وقت تذكير الضحى: عند دخول وقت الضحى */
    static long dhuhaAt(long[] r) { return dhStart(r); }
    /** وقت تذكير أذكار المساء: بعد العصر بعشرين دقيقة، وقبل المغرب بعشر دقائق على الأقل */
    static long eveningAt(long[] r) { return Math.min(r[3] + 20 * MIN, r[4] - 10 * MIN); }
}
