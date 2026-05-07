package org.softgauge_roles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

/**
 * Handles role claiming via the workstation block.
 *
 * Mechanic: when a player <b>shift + right-clicks</b> a workstation block
 * (Composter / Lectern / Blast Furnace), they either:
 *   <ul>
 *     <li>claim that role (if they have none),</li>
 *     <li>see their TODO list (if they already hold this role),</li>
 *     <li>or are reminded of their existing role (if they hold a different one).</li>
 *   </ul>
 *
 * Plain right-clicks (no sneak) are passed through so the workstation behaves
 * normally for vanilla use (composting, opening the lectern, smelting).
 */
public class RoleClaimListener implements Listener {

    private final RoleManager roleManager;

    public RoleClaimListener(RoleManager roleManager) {
        this.roleManager = roleManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.getPlayer().isSneaking()) return;
        if (event.getClickedBlock() == null) return;

        Block      block    = event.getClickedBlock();
        Material   material = block.getType();
        PlayerRole target   = PlayerRole.fromWorkstation(material);
        if (target == null) return;

        Player player = event.getPlayer();
        Optional<PlayerRole> existing = roleManager.getRole(player);

        if (existing.isEmpty()) {
            roleManager.assign(player.getUniqueId(), target);
            announceClaim(player, target);
        } else if (existing.get() == target) {
            announceTodo(player, target);
        } else {
            announceConflict(player, existing.get(), target);
        }

        // Suppress vanilla GUI / interaction so the claim is the only outcome
        event.setCancelled(true);
    }

    // ── Messaging helpers ─────────────────────────────────────────────────────

    private void announceClaim(Player player, PlayerRole role) {
        player.sendMessage(Component.text("You are now a " + role.getDisplayName() + ".",
                NamedTextColor.GREEN));
        sendTodo(player, role);
    }

    private void announceTodo(Player player, PlayerRole role) {
        player.sendMessage(Component.text(role.getDisplayName() + " — progression goals:",
                NamedTextColor.AQUA));
        sendTodo(player, role);
    }

    private void announceConflict(Player player, PlayerRole existing, PlayerRole attempted) {
        player.sendMessage(Component.text(
                "You are already a " + existing.getDisplayName() + ". "
                        + "You cannot claim " + attempted.getDisplayName() + ".",
                NamedTextColor.YELLOW));
    }

    private void sendTodo(Player player, PlayerRole role) {
        for (String goal : role.getProgressionGoals()) {
            player.sendMessage(Component.text("  • " + goal, NamedTextColor.GRAY));
        }
    }
}
