package org.softgauge_crafting;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /craft} command — alternative entry point to the smart-craft pipeline.
 *
 * <ul>
 *   <li>{@code /craft}                     — open the chat prompt
 *       (equivalent to sneak + right-clicking a crafting table).</li>
 *   <li>{@code /craft <item name>}         — skip the prompt and try the
 *       attempt directly with the supplied input (handy for keyboard players
 *       who want a one-shot command).</li>
 * </ul>
 *
 * The command itself does no inventory mutation — it just delegates to the
 * {@link SmartCraftPromptManager} or {@link SmartCraftingService} so all the
 * spell-check / recipe-check / craft logic lives in one place.
 *
 * Note: the command runs on the main thread, so calling
 * {@link SmartCraftingService#processCraftingAttempt} directly is safe.
 */
public final class SmartCraftCommand implements CommandExecutor {

    private final SmartCraftPromptManager   promptManager;
    private final SmartCraftingService      craftingService;

    public SmartCraftCommand(SmartCraftPromptManager promptManager,
                             SmartCraftingService craftingService) {
        this.promptManager   = promptManager;
        this.craftingService = craftingService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "This command must be run by a player.", NamedTextColor.RED));
            return true;
        }

        // No args → open the interactive chat prompt
        if (args.length == 0) {
            promptManager.beginPrompt(player);
            return true;
        }

        // With args → one-shot direct attempt; the service handles everything,
        // including spell-correction and the educator log entry.
        String input = String.join(" ", args).trim();
        craftingService.processCraftingAttempt(player, input);
        return true;
    }
}
