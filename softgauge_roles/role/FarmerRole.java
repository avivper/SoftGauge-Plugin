package org.softgauge_roles.role;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.softgauge_roles.AbstractRolePlugin;
import org.softgauge_roles.PlayerRole;
import org.softgauge_roles.RoleManager;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Farmer role plugin.
 *
 * <p><b>Workstation:</b> Composter</p>
 *
 * <p><b>Mechanic:</b> when a player holding the {@link PlayerRole#FARMER} role
 * harvests a fully-grown crop (Wheat, Carrots, Potatoes), the resulting drops
 * are doubled. Vanilla drop computation (including Fortune) is preserved by
 * cloning the original drop list and dispatching it manually with doubled
 * quantities.</p>
 */
public class FarmerRole extends AbstractRolePlugin {

    /** Crops that benefit from the Farmer's doubled-yield bonus. */
    private static final Set<Material> HARVESTABLE_CROPS = EnumSet.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES
    );

    public FarmerRole(RoleManager roleManager) {
        super(roleManager);
    }

    @Override
    public PlayerRole getRole() {
        return PlayerRole.FARMER;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!hasThisRole(player)) return;

        Block block = event.getBlock();
        if (!isFullyGrownCrop(block)) return;

        // Capture vanilla drops (with the player's tool/Fortune applied)…
        ItemStack tool                = player.getInventory().getItemInMainHand();
        Collection<ItemStack> vanilla = block.getDrops(tool);
        if (vanilla.isEmpty()) return;

        // …suppress the default drops, then dispatch doubled stacks ourselves.
        event.setDropItems(false);

        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack stack : vanilla) {
            ItemStack doubled = stack.clone();
            doubled.setAmount(stack.getAmount() * 2);
            block.getWorld().dropItemNaturally(centre, doubled);
        }
    }

    /** Returns true when the block is a Farmer-supported crop at maximum age. */
    private boolean isFullyGrownCrop(Block block) {
        if (!HARVESTABLE_CROPS.contains(block.getType())) return false;
        if (!(block.getBlockData() instanceof Ageable ageable)) return false;
        return ageable.getAge() == ageable.getMaximumAge();
    }
}
