package org.softgauge_crafting;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bridges player chat to the smart-craft pipeline.
 *
 * <p>When a player has an active prompt (via {@link SmartCraftPromptManager}),
 * their <em>next</em> chat message is:
 * <ol>
 *   <li>cancelled (so it doesn't broadcast as public chat),</li>
 *   <li>handed off to {@link SmartCraftingService#processCraftingAttempt}
 *       on the Bukkit main thread — inventory mutations are not safe on
 *       the async chat thread.</li>
 * </ol></p>
 *
 * <p>Priority is {@link EventPriority#LOWEST} so we run before chat-analysis
 * detectors (which are all {@code MONITOR}/{@code ignoreCancelled = true}).
 * That way a craft request never gets misclassified as
 * {@code CHAT_ASKED_QUESTION} or similar.</p>
 */
public final class SmartCraftChatListener implements Listener {

    private final JavaPlugin                plugin;
    private final SmartCraftPromptManager   promptManager;
    private final SmartCraftingService      craftingService;

    public SmartCraftChatListener(JavaPlugin plugin,
                                  SmartCraftPromptManager promptManager,
                                  SmartCraftingService craftingService) {
        this.plugin          = plugin;
        this.promptManager   = promptManager;
        this.craftingService = craftingService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();

        // Atomic check-and-consume: if the player wasn't prompted, return
        // immediately without touching the event so normal chat flow is
        // completely unaffected.
        if (!promptManager.consumePromptIfActive(sender)) return;

        // Suppress the public broadcast — this message is private input.
        event.setCancelled(true);

        String input = PlainTextComponentSerializer.plainText()
                .serialize(event.message())
                .trim();

        // Allow graceful cancellation
        if (input.equalsIgnoreCase(SmartCraftPromptManager.CANCEL_KEYWORD)) {
            promptManager.cancelPrompt(sender);
            return;
        }

        // Hop to the main thread — inventory ops + recipe lookup must run sync.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!sender.isOnline()) return;
            craftingService.processCraftingAttempt(sender, input);
        });
    }
}
