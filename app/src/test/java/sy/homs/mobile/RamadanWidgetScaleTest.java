package sy.homs.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

/** نطاق حجم خط ودجت رمضان: نفس قاعدة ودجت الصلاة (70% إلى 150%). */
public class RamadanWidgetScaleTest {
    @Test public void hundredIsIdentity() { assertEquals(1.00f, RamadanWidget.fontScale(100), 0.0001f); }
    @Test public void midRangePassesThrough() { assertEquals(1.20f, RamadanWidget.fontScale(120), 0.0001f); assertEquals(0.85f, RamadanWidget.fontScale(85), 0.0001f); }
    @Test public void clampsLow() { assertEquals(0.70f, RamadanWidget.fontScale(0), 0.0001f); assertEquals(0.70f, RamadanWidget.fontScale(69), 0.0001f); assertEquals(0.70f, RamadanWidget.fontScale(70), 0.0001f); }
    @Test public void clampsHigh() { assertEquals(1.50f, RamadanWidget.fontScale(150), 0.0001f); assertEquals(1.50f, RamadanWidget.fontScale(151), 0.0001f); assertEquals(1.50f, RamadanWidget.fontScale(1000), 0.0001f); }
    @Test public void arraysMatchWidgetLayout() {
        assertEquals(RamadanWidget.FS_IDS.length, RamadanWidget.FS_SP.length);
        assertEquals(6, RamadanWidget.FS_IDS.length);
    }
    @Test public void fitUnknownKeepsUser() { assertEquals(150, RamadanWidget.fitPct(150, 0, 0, 100, 150)); }
    @Test public void fitCapsByHeight() { assertEquals(120, RamadanWidget.fitPct(150, 120, 500, 100, 100)); assertEquals(80, RamadanWidget.fitPct(80, 120, 500, 100, 100)); }
    @Test public void fitCapsByWidth() { assertEquals(130, RamadanWidget.fitPct(150, 500, 130, 100, 100)); }
    @Test public void fitShrinksBelow100WhenNarrow() { assertEquals(50, RamadanWidget.fitPct(150, 500, 50, 100, 100)); assertEquals(30, RamadanWidget.fitPct(100, 5, 5, 100, 100)); }
    @Test public void fitNeverBelowFloor() { assertEquals(RamadanWidget.MIN_FIT, RamadanWidget.fitPct(150, 1, 1, 1000, 1000)); }
    @Test public void fitAllowsFull150WhenRoomy() { assertEquals(150, RamadanWidget.fitPct(150, 400, 400, 100, 100)); }
    @Test public void fitMonotonicInRoom() {
        int prev = 0;
        for (int h = 60; h <= 300; h += 10) { int e = RamadanWidget.fitPct(150, h, 400, 100, 100); assertTrue(e >= prev); prev = e; }
    }
}
