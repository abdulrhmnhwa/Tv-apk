package sy.homs.mobile;

import static org.junit.Assert.*;
import org.junit.Test;

public class KhatmaWidgetTest {
    @Test public void fixedGoalFromDays() {
        assertEquals(21, KhatmaWidget.goal(30));
        assertEquals(41, KhatmaWidget.goal(15));
        assertEquals(604, KhatmaWidget.goal(1));
        assertEquals(21, KhatmaWidget.goal(29.4)); // يوم رمضان الكسري يُقرَّب
    }
    @Test public void sameDayReadAndDone() {
        KhatmaWidget.State s = KhatmaWidget.compute(true, 238, 100, 30, 109, 220, 109);
        assertEquals(10, s.dayN); assertEquals(30, s.days); assertEquals(18, s.read); assertEquals(21, s.goal);
        assertFalse(s.done);
        s = KhatmaWidget.compute(true, 270, 100, 30, 109, 220, 109); // قرأ 50 والورد 21
        assertEquals(50, s.read); assertTrue(s.done);
    }
    @Test public void newDayResetsReadButKeepsSameGoal() {
        KhatmaWidget.State s = KhatmaWidget.compute(true, 270, 100, 30, 109, 220, 110);
        assertEquals(0, s.read); assertEquals(21, s.goal); assertFalse(s.done); assertEquals(11, s.dayN);
    }
    @Test public void noKhatmaAndFinishedAndOverPlan() {
        assertFalse(KhatmaWidget.compute(false, 0, 0, 30, 0, 0, 5).has);
        KhatmaWidget.State f = KhatmaWidget.compute(true, 604, 100, 30, 109, 600, 109);
        assertTrue(f.finished && f.done); assertEquals(30, f.juz); assertEquals(100, f.pct);
        KhatmaWidget.State o = KhatmaWidget.compute(true, 100, 100, 30, 125, 90, 135);
        assertTrue(o.overPlan); assertEquals(36, o.dayN);
    }
    @Test public void juzMatchesApp() {
        assertEquals(1, KhatmaWidget.compute(true, 0, 0, 30, -1, 0, 0).juz);
        assertEquals(2, KhatmaWidget.compute(true, 21, 0, 30, -1, 0, 0).juz);
        assertEquals(30, KhatmaWidget.compute(true, 603, 0, 30, -1, 0, 0).juz);
    }
    @Test public void textsNoTapWording() {
        KhatmaWidget.State s = KhatmaWidget.compute(true, 238, 100, 30, 109, 220, 109);
        String[] t = KhatmaWidget.texts(s, true, true, "ختمة رمضان");
        assertEquals("ختمة رمضان", t[0]);
        assertEquals("اليوم 10 من 30", t[1]);
        assertEquals("238 من 604 صفحة · الجزء 12", t[3]);
        assertEquals("قرأت اليوم 18 من 21 صفحة", t[2]);
        for (String x : t) assertFalse(x.contains("اضغط"));
    }
}
