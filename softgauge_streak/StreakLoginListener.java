package org.softgauge_streak;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Wires {@link PlayerJoinEvent} to the streak system and surfaces the result
 * to the player as a styled chat message.
 *
 * <p>The message is sent on a 1-second delay (20 ticks) so it lands after
 * any other "welcome" output produced by the server / other plugins —
 * which keeps the streak headline visually clean and easy to spot.</p>
 *
 * <p>This listener is the only piece of the streak subsystem that knows
 * about Bukkit events; the service and repository are framework-agnostic.</p>
 */
public final class StreakLoginListener implements Listener {

    /** Delay before sending the streak banner (ticks; 20 ticks = 1 second). */
    private static final long MESSAGE_DELAY_TICKS = 20L;

    private final JavaPlugin    plugin;
    private final StreakService service;

    public StreakLoginListener(JavaPlugin plugin, StreakService service) {
        this.plugin  = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Update the streak immediately — even if the player drops before our
        // delayed banner runs, the day is correctly recorded.
        StreakService.LoginOutcome outcome =
                service.recordLogin(player.getUniqueId(), player.getName());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(buildBanner(outcome));
        }, MESSAGE_DELAY_TICKS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Message construction (Adventure components)
    // ─────────────────────────────────────────────────────────────────────────

    private Component buildBanner(StreakService.LoginOutcome outcome) {
        StreakRecord r = outcome.record();

        return switch (outcome.type()) {

            case FIRST_LOGIN -> Component.text()
                    .append(Component.text("🎉 ", NamedTextColor.GOLD))
                    .append(Component.text("Welcome to SoftGauge! ", NamedTextColor.GREEN))
                    .append(Component.text("Day 1 ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("of your streak starts today!", NamedTextColor.GREEN))
                    .build();

            case SAME_DAY -> Component.text()
                    .append(Component.text("🔥 ", NamedTextColor.GOLD))
                    .append(Component.text("Day " + r.currentStreak() + " ",
                            NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("streak — keep it up!", NamedTextColor.YELLOW))
                    .build();

            case CONTINUED -> Component.text()
                    .append(Component.text("🔥 ", NamedTextColor.GOLD))
                    .append(Component.text("Day " + r.currentStreak(),
                            NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("! You're on fire!", NamedTextColor.GREEN))
                    .append(longestSuffix(r))
                    .build();

            case RESET -> Component.text()
                    .append(Component.text("💔 ", NamedTextColor.RED))
                    .append(Component.text("Streak reset — last login was "
                            + outcome.daysGap() + " days ago. ", NamedTextColor.GRAY))
                    .append(Component.text("Day 1 ",
                            NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("starts now!", NamedTextColor.GRAY))
                    .append(longestSuffix(r))
                    .build();
        };
    }

    /** Trailing "(best: N days)" component, suppressed when current == longest. */
    private Component longestSuffix(StreakRecord r) {
        if (r.longestStreak() <= r.currentStreak()) return Component.empty();
        return Component.text()
                .append(Component.text("  ✦ best ever: ", NamedTextColor.GRAY))
                .append(Component.text(r.longestStreak() + " days", NamedTextColor.AQUA))
                .build();
    }
}
