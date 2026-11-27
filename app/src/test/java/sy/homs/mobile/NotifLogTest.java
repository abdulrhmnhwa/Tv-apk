package sy.homs.mobile;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public class NotifLogTest {
    @Test public void keepsNewestTenNewestFirst() {
        List<String[]> l = null;
        for (int i = 1; i <= 13; i++) l = NotifLog.pushEntries(l, new String[]{"" + (1000 + i), "adhan", "t" + i, "b" + i}, 10);
        assertEquals(10, l.size());
        assertEquals("t13", l.get(0)[2]);
        assertEquals("t4", l.get(9)[2]);
    }
    @Test public void underLimitKeepsAll() {
        List<String[]> l = NotifLog.pushEntries(null, new String[]{"1", "k", "a", "b"}, 10);
        l = NotifLog.pushEntries(l, new String[]{"2", "k", "c", "d"}, 10);
        assertEquals(2, l.size());
        assertEquals("c", l.get(0)[2]);
    }
}
