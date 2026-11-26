package sy.homs.mobile;

import static org.junit.Assert.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class AnnounceGateTest {
    private final long now = 1_700_000_000_000L;

    @Test public void freshUnseenShows() {
        assertTrue(AnnounceGate.shouldShow("m1", now - 5_000, now, new HashSet<String>()));
    }
    @Test public void seenIdDropped() {
        Set<String> seen = new HashSet<String>(); seen.add("m1");
        assertFalse(AnnounceGate.shouldShow("m1", now - 5_000, now, seen));
    }
    @Test public void oldMessageDropped() {
        assertFalse(AnnounceGate.shouldShow("m2", now - 121_000, now, new HashSet<String>()));
        assertFalse(AnnounceGate.shouldShow("m2", now - 3_600_000, now, new HashSet<String>()));
    }
    @Test public void boundaryExactly120sShows() {
        assertTrue(AnnounceGate.shouldShow("m3", now - 120_000, now, new HashSet<String>()));
    }
    @Test public void missingOrEmptyIdDropped() {
        assertFalse(AnnounceGate.shouldShow(null, now - 5_000, now, new HashSet<String>()));
        assertFalse(AnnounceGate.shouldShow("", now - 5_000, now, new HashSet<String>()));
    }
    @Test public void missingSentAtDropped() {
        assertFalse(AnnounceGate.shouldShow("m4", 0, now, new HashSet<String>()));
    }
    @Test public void smallFutureSkewAllowed() {
        assertTrue(AnnounceGate.shouldShow("m5", now + 30_000, now, new HashSet<String>()));
    }
    @Test public void largeFutureSkewDropped() {
        assertFalse(AnnounceGate.shouldShow("m6", now + 61_000, now, new HashSet<String>()));
    }
    @Test public void nullSeenSetTreatedEmpty() {
        assertTrue(AnnounceGate.shouldShow("m7", now - 5_000, now, null));
    }
}
