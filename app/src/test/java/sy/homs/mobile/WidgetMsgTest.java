package sy.homs.mobile;

import static org.junit.Assert.*;
import java.util.ArrayList;
import org.junit.Test;

public class WidgetMsgTest {
    @Test public void activeOnlyWithinTenMinutes() {
        long now = 1_000_000L;
        assertTrue(WidgetMsg.isActive(now + WidgetMsg.SHOW_MS, now));
        assertTrue(WidgetMsg.isActive(now + 1, now));
        assertFalse(WidgetMsg.isActive(now, now));
        assertFalse(WidgetMsg.isActive(now - 1, now));
        assertFalse(WidgetMsg.isActive(0, now));
        // ساعة الجهاز رجعت للخلف: الانتهاء أبعد من المدة المسموحة => غير فعّالة
        assertFalse(WidgetMsg.isActive(now + WidgetMsg.SHOW_MS + 1, now));
    }
    @Test public void thirdPositionAfterTwoItems() {
        assertEquals(0, WidgetMsg.insertIndex(0));
        assertEquals(1, WidgetMsg.insertIndex(1));
        assertEquals(2, WidgetMsg.insertIndex(2));
        assertEquals(2, WidgetMsg.insertIndex(5));
        ArrayList<String> l = new ArrayList<>(); l.add("a"); l.add("b"); l.add("c");
        l.add(WidgetMsg.insertIndex(l.size()), "MSG");
        assertEquals("MSG", l.get(2));
        ArrayList<String> one = new ArrayList<>(); one.add("a");
        one.add(WidgetMsg.insertIndex(one.size()), "MSG");
        assertEquals("MSG", one.get(1));
    }
    @Test public void cleanTrimsAndLimits() {
        assertNull(WidgetMsg.clean(null, 10));
        assertNull(WidgetMsg.clean("  \n\t ", 10));
        assertEquals("a b", WidgetMsg.clean(" a \n\n  b ", 10));
        assertEquals(5, WidgetMsg.clean("abcdefghij", 5).length());
    }
    @Test public void duplicateIdsRejectedAndListBounded() {
        ArrayList<String> ids = new ArrayList<>();
        assertFalse(WidgetMsg.seenBefore(ids, "id-1"));
        assertTrue(WidgetMsg.seenBefore(ids, "id-1"));
        for (int i = 0; i < 100; i++) WidgetMsg.seenBefore(ids, "x" + i);
        assertTrue(ids.size() <= 30);
    }
}
