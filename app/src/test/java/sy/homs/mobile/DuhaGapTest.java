package sy.homs.mobile;

import static org.junit.Assert.*;
import org.junit.Test;

public class DuhaGapTest {
    static final long M = 60000L;
    // فجر 5:50، شروق 7:19، ظهر 12:22، عصر 15:04، مغرب 17:25، عشاء 18:50 (ميلي ثانية من بداية اليوم)
    static long[] day() {
        return new long[]{(5 * 60 + 50) * M, (7 * 60 + 19) * M, (12 * 60 + 22) * M, (15 * 60 + 4) * M, (17 * 60 + 25) * M, (18 * 60 + 50) * M};
    }

    @Test public void duhaReminderEndsFifteenBeforeDhuhr() {
        long[] r = day();
        int[] iq = {0, 0, 0, 0, 0, 0};
        assertEquals(r[2] - 15 * M, WidgetTip.dhEnd(r));
        assertTrue(WidgetTip.pick(r[2] - 16 * M, r, iq, 2, -1, 20)[0].contains("الضحى"));
        assertFalse(WidgetTip.pick(r[2] - 15 * M, r, iq, 2, -1, 20)[0].contains("الضحى"));
    }

    @Test public void duhaTipSameOnFriday() {
        long[] r = day();
        int[] iq = {0, 0, 0, 0, 0, 0};
        assertTrue(WidgetTip.pick(r[2] - 16 * M, r, iq, WidgetTip.FRI, -1, 20)[0].contains("الضحى"));
        assertFalse(WidgetTip.pick(r[2] - 15 * M, r, iq, WidgetTip.FRI, -1, 20)[0].contains("الضحى"));
    }

    @Test public void morningAdhkarWindowUnchanged() {
        long[] r = day();
        assertEquals(r[2] - 20 * M, WidgetTip.mornEnd(r));
        assertNotNull(WidgetTip.adhkarTip(r[2] - 21 * M, r, "x"));
        assertNull(WidgetTip.adhkarTip(r[2] - 19 * M, r, "x"));
    }

    @Test public void boundaryListHasBothEnds() {
        long[] r = day();
        int[] iq = {0, 0, 0, 0, 0, 0};
        long now = r[2] - 25 * M;
        assertEquals(r[2] - 20 * M, WidgetTip.nextBoundary(now, new long[][]{r}, iq));
        assertEquals(r[2] - 15 * M, WidgetTip.nextBoundary(r[2] - 20 * M, new long[][]{r}, iq));
    }
}
