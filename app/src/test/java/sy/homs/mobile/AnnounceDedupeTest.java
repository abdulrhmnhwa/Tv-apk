package sy.homs.mobile;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;

public class AnnounceDedupeTest {
    private final long now = 1_700_000_000_000L;

    static final class MemStore implements AnnounceDedupe.Store {
        Set<String> ids = new HashSet<String>();
        boolean failPersist = false;
        public synchronized Set<String> load() { return new HashSet<String>(ids); }
        public synchronized boolean persist(Set<String> s) {
            if (failPersist) return false;
            ids = new HashSet<String>(s);
            return true;
        }
    }

    @Test public void firstTrueSecondFalse() {
        AnnounceDedupe d = new AnnounceDedupe(new MemStore());
        assertTrue(d.checkAndRecord("m1", now - 5_000, now));
        assertFalse(d.checkAndRecord("m1", now - 5_000, now));
    }

    @Test public void persistFailureBlocksDisplay() {
        MemStore s = new MemStore();
        s.failPersist = true;
        AnnounceDedupe d = new AnnounceDedupe(s);
        assertFalse(d.checkAndRecord("m1", now - 5_000, now));
        assertTrue(s.ids.isEmpty());
    }

    @Test public void concurrentIdenticalDeliveriesExactlyOnePasses() throws Exception {
        final AnnounceDedupe d = new AnnounceDedupe(new MemStore());
        final int n = 64;
        ExecutorService ex = Executors.newFixedThreadPool(16);
        List<Future<Boolean>> fs = new ArrayList<Future<Boolean>>();
        for (int i = 0; i < n; i++) {
            fs.add(ex.submit(new Callable<Boolean>() {
                public Boolean call() { return d.checkAndRecord("same-id", now - 5_000, now); }
            }));
        }
        int passed = 0;
        for (Future<Boolean> f : fs) if (f.get()) passed++;
        ex.shutdown();
        assertEquals(1, passed);
    }

    @Test public void distinctIdsEachPassOnce() {
        AnnounceDedupe d = new AnnounceDedupe(new MemStore());
        for (int i = 0; i < 20; i++) {
            assertTrue(d.checkAndRecord("id-" + i, now - 5_000, now));
            assertFalse(d.checkAndRecord("id-" + i, now - 5_000, now));
        }
    }

    @Test public void staleMessageNeverRecorded() {
        MemStore s = new MemStore();
        AnnounceDedupe d = new AnnounceDedupe(s);
        assertFalse(d.checkAndRecord("old", now - 121_000, now));
        assertTrue(s.ids.isEmpty());
    }
}
