package org.softgauge_crafting;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * In-world entry point for smart crafting.
 *
 * <p>Mechanic: <b>shift + right-click any Crafting Table</b> opens the
 * smart-craft chat prompt. A plain right-click (no sneak) is left untouched,
 * so the vanilla 3×3 crafting GUI continues to work for players who prefer
 * it. This mirrors the role-claim pattern used by
 * {@code RoleClaimListener} (sneak + right-click on workstations).</p>
 *
 * <p>Why this gesture?
 * <ul>
 *   <li>It's discoverable — every player already knows how to right-click
 *       a crafting table.</li>
 *   <li>It doesn't break the vanilla crafting UX — the polite "advanced"
 *       interaction is gated behind sneak.</li>
 *   <li>It composes cleanly with other shift+right-click features in this
 *       plugin (role claiming on workstations) without conflict, because
 *       Crafting Table is not a role workstation.</li>
 * </ul></p>
 */
public final class SmartCraftAccessListener implements Listener {

    private final SmartCraftPromptManager promptManager;

    public SmartCraftAccessListener(SmartCraftPromptManager promptManager) {
        this.promptManager = promptManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.getPlayer().isSneaking()) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.CRAFTING_TABLE) return;

        // Suppress the vanilla crafting GUI so only the smart-craft prompt opens.
        event.setCancelled(true);

        promptManager.beginPrompt(event.getPlayer());
    }
}
