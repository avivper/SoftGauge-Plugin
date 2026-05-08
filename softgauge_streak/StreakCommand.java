package org.softgauge_streak;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * {@code /streak [top]} command.
 *
 * <ul>
 *   <li>{@code /streak}      — show the calling player's current and best streak</li>
 *   <li>{@code /streak top}  — show the top 10 active streaks across all players</li>
 * </ul>
 *
 * Read-only: this executor never mutates streak state. Updates only happen
 * via {@link StreakLoginListener}.
 */
public final class StreakCommand implements CommandExecutor {

    private static final int LEADERBOARD_SIZE = 10;

    private final StreakService service;

    public StreakCommand(StreakService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "This command must be run by a player.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showOwnStreak(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("top")) {
            showLeaderboard(player);
            return true;
        }

        player.sendMessage(Component.text("Usage: /streak [top]", NamedTextColor.GRAY));
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void showOwnStreak(Player player) {
        Optional<StreakRecord> recordOpt = service.getStreak(player.getUniqueId());

        if (recordOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    "No streak yet — log in tomorrow to start one!", NamedTextColor.GRAY));
            return;
        }

        StreakRecord r = recordOpt.get();
        player.sendMessage(Component.text()
                .append(Component.text("🔥 ", NamedTextColor.GOLD))
                .append(Component.text("Current streak: ", NamedTextColor.GOLD))
                .append(Component.text(r.currentStreak() + " days",
                        NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("   ✦   ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Longest ever: ", NamedTextColor.GOLD))
                .append(Component.text(r.longestStreak() + " days", NamedTextColor.AQUA))
                .append(Component.text("   ✦   ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Last login: ", NamedTextColor.GRAY))
                .append(Component.text(r.lastLogin().toString(), NamedTextColor.WHITE))
                .build());
    }

    private void showLeaderboard(Player player) {
        List<StreakRecord> top = service.getRepository().all().values().stream()
                .sorted(Comparator.comparingInt(StreakRecord::currentStreak).reversed()
                        .thenComparing(StreakRecord::longestStreak, Comparator.reverseOrder()))
                .limit(LEADERBOARD_SIZE)
                .toList();

        player.sendMessage(Component.text("🏆 Streak Leaderboard",
                NamedTextColor.GOLD, TextDecoration.BOLD));

        if (top.isEmpty()) {
            player.sendMessage(Component.text("  (no streaks yet)", NamedTextColor.GRAY));
            return;
        }

        int rank = 1;
        for (StreakRecord r : top) {
            player.sendMessage(Component.text()
                    .append(Component.text("  " + rank + ". ", NamedTextColor.GRAY))
                    .append(Component.text(r.playerName(), NamedTextColor.WHITE))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(r.currentStreak() + " days",
                            NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" (best " + r.longestStreak() + ")",
                            NamedTextColor.DARK_GRAY))
                    .build());
            rank++;
        }
    }
}
