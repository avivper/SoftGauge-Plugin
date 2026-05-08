package org.softgauge_crafting;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player session state for the smart-craft chat prompt.
 *
 * <p>"Prompted" means: the player has just opened the smart-craft UI (via
 * sneak+right-clicking a crafting table or running {@code /craft} with no
 * arguments) and the next chat message they send should be treated as a
 * crafting request rather than public chat.</p>
 *
 * <p>Each prompt has a hard expiry of {@link #PROMPT_TIMEOUT_MS}; stale
 * entries are pruned lazily on lookup so we don't need a scheduled task.
 * Players who quit while prompted are cleaned up by the
 * {@link Listener} hook below — defensive, since the timeout would catch
 * them anyway.</p>
 *
 * <p>Thread-safe: {@code AsyncChatEvent}-fed callers (on the async thread)
 * read state through {@link #consumePromptIfActive(Player)} which atomically
 * checks-and-removes via {@link ConcurrentHashMap#remove(Object)}.</p>
 */
public final class SmartCraftPromptManager implements Listener {

    /** A prompt expires after this many milliseconds of inactivity. */
    public static final long PROMPT_TIMEOUT_MS = 30_000L;

    /** Reserved input string the player can type to back out of the prompt. */
    public static final String CANCEL_KEYWORD = "cancel";

    /** Player UUID → epoch-ms timestamp when the prompt was opened. */
    private final Map<UUID, Long> activePrompts = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Open a prompt for {@code player}. Sends them a styled banner explaining
     * how to use it. Idempotent — calling twice just refreshes the timeout.
     */
    public void beginPrompt(Player player) {
        activePrompts.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage(banner());
    }

    /**
     * Atomically remove the player's prompt entry and report whether they
     * were actually in one. Used by the chat listener to decide if the
     * incoming message should be intercepted.
     *
     * @return {@code true} if the player had an active, non-expired prompt
     *         (and it has now been consumed); {@code false} otherwise.
     */
    public boolean consumePromptIfActive(Player player) {
        Long startedAt = activePrompts.remove(player.getUniqueId());
        if (startedAt == null) return false;

        long age = System.currentTimeMillis() - startedAt;
        if (age > PROMPT_TIMEOUT_MS) {
            // Already expired — silently drop and treat as "not prompted".
            return false;
        }
        return true;
    }

    /**
     * Cancel an active prompt without consuming a craft attempt.
     * No-op if the player wasn't prompted.
     */
    public void cancelPrompt(Player player) {
        if (activePrompts.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("✗ Smart-craft prompt cancelled.",
                    NamedTextColor.GRAY));
        }
    }

    /** Read-only check used for command help / debugging. Does not mutate state. */
    public boolean isPrompted(Player player) {
        Long startedAt = activePrompts.get(player.getUniqueId());
        if (startedAt == null) return false;
        return System.currentTimeMillis() - startedAt <= PROMPT_TIMEOUT_MS;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Listener hook (cleanup on quit)
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activePrompts.remove(event.getPlayer().getUniqueId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Banner
    // ─────────────────────────────────────────────────────────────────────────

    private Component banner() {
        Component divider = Component.text(
                "═══════════════════════════════════════",
                NamedTextColor.DARK_AQUA);

        return Component.text()
                .append(divider).append(Component.newline())
                .append(Component.text("  ✨ SMART CRAFT MODE",
                        NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.newline())
                .append(divider).append(Component.newline())
                .append(Component.text("  Type the item you want to craft. Spelling is forgiving!",
                        NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("  Examples: ", NamedTextColor.GRAY))
                .append(Component.text("Diamond Sword, Wooden Pickaxe, Cake",
                        NamedTextColor.YELLOW))
                .append(Component.newline()).append(Component.newline())
                .append(Component.text("  💬 Just send your next chat message.",
                        NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.text("  ⏱  Times out in "
                                + (PROMPT_TIMEOUT_MS / 1000) + " seconds.",
                        NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("  ❌ Type \"" + CANCEL_KEYWORD
                                + "\" to back out.",
                        NamedTextColor.GRAY))
                .append(Component.newline())
                .append(divider)
                .build();
    }
}
