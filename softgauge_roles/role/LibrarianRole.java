package org.softgauge_roles.role;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.softgauge_roles.AbstractRolePlugin;
import org.softgauge_roles.PlayerRole;
import org.softgauge_roles.RoleManager;

import java.util.EnumSet;
import java.util.Set;

/**
 * Librarian role plugin.
 *
 * <p><b>Workstation:</b> Lectern</p>
 *
 * <p><b>Mechanic:</b> when a player holding the {@link PlayerRole#LIBRARIAN}
 * role mines a Lapis Lazuli ore (overworld or deepslate variant), the dropped
 * experience is doubled. Vanilla item drops and Silk Touch behaviour are left
 * untouched — only the XP orb amount is amplified.</p>
 */
public class LibrarianRole extends AbstractRolePlugin {

    /** Lapis Lazuli ore variants the Librarian's bonus applies to. */
    private static final Set<Material> LAPIS_ORES = EnumSet.of(
            Material.LAPIS_ORE,
            Material.DEEPSLATE_LAPIS_ORE
    );

    private static final int XP_MULTIPLIER = 2;

    public LibrarianRole(RoleManager roleManager) {
        super(roleManager);
    }

    @Override
    public PlayerRole getRole() {
        return PlayerRole.LIBRARIAN;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!hasThisRole(player)) return;
        if (!LAPIS_ORES.contains(event.getBlock().getType())) return;

        int vanillaXp = event.getExpToDrop();
        if (vanillaXp <= 0) return; // Silk Touch or other modifier suppressed XP — respect it.

        event.setExpToDrop(vanillaXp * XP_MULTIPLIER);
    }
}
