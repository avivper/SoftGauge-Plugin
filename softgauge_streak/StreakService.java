package org.softgauge_streak;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for the daily-login streak system.
 *
 * <p>This is the only class that interprets the calendar — the repository is a
 * dumb store, the listener is a dumb adapter. Decisions about
 * "did the player keep the streak?" all live here.</p>
 *
 * <p>Rules (Duolingo-style):</p>
 * <ul>
 *   <li><b>First ever login</b>      → {@code current = 1}, {@code longest = max(1, prev longest)}</li>
 *   <li><b>Same calendar day</b>     → no change (idempotent re-login)</li>
 *   <li><b>Exactly the next day</b>  → {@code current += 1}, {@code longest = max(longest, current)}</li>
 *   <li><b>Gap of 2+ days</b>        → {@code current = 1} (today restarts the streak),
 *                                       longest is preserved</li>
 * </ul>
 *
 * <p>Calendar arithmetic uses the <i>server's</i> default time zone via
 * {@link Clock#systemDefaultZone()}, so all players share a consistent
 * "today" boundary regardless of where they connect from.</p>
 *
 * <p>The injectable {@link Clock} makes the service trivially unit-testable
 * with {@link Clock#fixed(java.time.Instant, java.time.ZoneId)}.</p>
 */
public final class StreakService {

    /** What happened on this login, for downstream presentation logic. */
    public enum UpdateType {
        FIRST_LOGIN,
        SAME_DAY,
        CONTINUED,
        RESET
    }

    /**
     * Outcome of a {@link #recordLogin(UUID, String)} call.
     *
     * @param record    the newly-saved (or unchanged) streak record
     * @param type      the kind of update that occurred
     * @param daysGap   number of full days between previous login and today
     *                  (0 for FIRST/SAME, 1 for CONTINUED, >1 for RESET)
     */
    public record LoginOutcome(StreakRecord record, UpdateType type, long daysGap) {}

    private final StreakRepository repo;
    private       Clock             clock;

    public StreakService(StreakRepository repo) {
        this.repo  = repo;
        this.clock = Clock.systemDefaultZone();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Apply the streak rules for the supplied player and return what happened.
     * Persists the change immediately so a server crash mid-session doesn't
     * lose the day's increment.
     */
    public synchronized LoginOutcome recordLogin(UUID id, String name) {
        LocalDate today = LocalDate.now(clock);
        Optional<StreakRecord> existing = repo.find(id);

        // ── First login ever ────────────────────────────────────────────────
        if (existing.isEmpty()) {
            StreakRecord first = StreakRecord.initial(id, name, today);
            repo.put(first);
            repo.persist();
            return new LoginOutcome(first, UpdateType.FIRST_LOGIN, 0);
        }

        StreakRecord prev = existing.get();
        long gap = ChronoUnit.DAYS.between(prev.lastLogin(), today);

        // ── Already logged in today — idempotent no-op ──────────────────────
        if (gap == 0) {
            // Refresh the stored display name (it may have changed) but skip persist.
            if (!prev.playerName().equals(name)) {
                repo.put(new StreakRecord(id, name, prev.lastLogin(),
                        prev.currentStreak(), prev.longestStreak()));
            }
            return new LoginOutcome(prev, UpdateType.SAME_DAY, 0);
        }

        // ── Consecutive day — extend the streak ─────────────────────────────
        if (gap == 1) {
            int newCurrent = prev.currentStreak() + 1;
            int newLongest = Math.max(prev.longestStreak(), newCurrent);
            StreakRecord updated = new StreakRecord(id, name, today, newCurrent, newLongest);
            repo.put(updated);
            repo.persist();
            return new LoginOutcome(updated, UpdateType.CONTINUED, 1);
        }

        // ── Gap of 2+ days — streak broken, today restarts at 1 ─────────────
        StreakRecord reset = new StreakRecord(id, name, today, 1, prev.longestStreak());
        repo.put(reset);
        repo.persist();
        return new LoginOutcome(reset, UpdateType.RESET, gap);
    }

    /** Read-only access — used by the {@code /streak} command. */
    public Optional<StreakRecord> getStreak(UUID id) {
        return repo.find(id);
    }

    public StreakRepository getRepository() {
        return repo;
    }

    // ── Test seam ────────────────────────────────────────────────────────────

    /** Visible-for-testing — inject a {@link Clock#fixed} to simulate dates. */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
