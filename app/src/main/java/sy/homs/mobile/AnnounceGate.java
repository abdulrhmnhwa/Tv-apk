package sy.homs.mobile;

import java.util.Set;

/** Pure decision logic for central announcements: show at most once, only when fresh. */
public final class AnnounceGate {
    /** Drop messages whose send time is older than this (defense-in-depth behind FCM ttl=0). */
    public static final long MAX_AGE_MS = 120_000L;
    /** Tolerate sender clock being ahead by up to this much. */
    public static final long MAX_FUTURE_SKEW_MS = 60_000L;

    private AnnounceGate() {}

    public static boolean shouldShow(String msgId, long sentAtMs, long nowMs, Set<String> seenIds) {
        if (msgId == null || msgId.length() == 0) return false;
        if (seenIds != null && seenIds.contains(msgId)) return false;
        if (sentAtMs <= 0) return false;
        long age = nowMs - sentAtMs;
        if (age > MAX_AGE_MS) return false;
        if (age < -MAX_FUTURE_SKEW_MS) return false;
        return true;
    }
}
