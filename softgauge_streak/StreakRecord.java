package org.softgauge_streak;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable per-player streak data carrier.
 *
 * @param playerId       Bukkit UUID of the player
 * @param playerName     last-known display name (refreshed on every login)
 * @param lastLogin      the calendar date of the player's most recent login
 * @param currentStreak  number of consecutive days, including {@code lastLogin}
 * @param longestStreak  the player's all-time best streak length
 *
 * <p>Persistence: serialised by {@link StreakRepository} to {@code streaks.yml}.
 * Records are replaced wholesale on update — there is no in-place mutation.</p>
 */
public record StreakRecord(
        UUID      playerId,
        String    playerName,
        LocalDate lastLogin,
        int       currentStreak,
        int       longestStreak
) {

    /** Build the very first streak record for a brand-new player. */
    public static StreakRecord initial(UUID id, String name, LocalDate today) {
        return new StreakRecord(id, name, today, 1, 1);
    }
}
