package sy.homs.mobile;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Atomic check-and-record for announcement display.
 * checkAndRecord is synchronized: two identical deliveries racing can never both pass.
 * The id is persisted BEFORE any notification is shown; if persisting fails,
 * nothing is shown (at-most-once is preserved at the cost of a possible drop).
 */
public final class AnnounceDedupe {
    public interface Store {
        Set<String> load();
        /** Must durably store the set and return true, or return false on failure. */
        boolean persist(Set<String> ids);
    }

    private static final int CAP = 100;
    private final Store store;

    public AnnounceDedupe(Store store) { this.store = store; }

    /** Returns true exactly once per fresh msg_id; safe under concurrent calls. */
    public synchronized boolean checkAndRecord(String msgId, long sentAtMs, long nowMs) {
        Set<String> seen = new LinkedHashSet<String>(store.load());
        if (!AnnounceGate.shouldShow(msgId, sentAtMs, nowMs, seen)) return false;
        seen.add(msgId);
        while (seen.size() > CAP) seen.remove(seen.iterator().next());
        return store.persist(seen);
    }
}
