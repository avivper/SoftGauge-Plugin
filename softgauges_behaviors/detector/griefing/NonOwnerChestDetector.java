package org.softgauges_behaviors.detector.griefing;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;
import org.softgauges_behaviors.tracking.PlacementTracker;

import java.util.EnumSet;
import java.util.Set;

/**
 * CHEST_OPEN_NON_OWNER
 *
 * Fires when a player opens a storage container they did not place.
 * Covers chests, barrels, shulker boxes, trapped chests, and hoppers.
 */
public class NonOwnerChestDetector extends AbstractBehaviorDetector {

    private final PlacementTracker placements;

    private static final Set<Material> CONTAINERS = EnumSet.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
            Material.HOPPER,
            Material.DROPPER,
            Material.DISPENSER,
            Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX,
            Material.SHULKER_BOX
    );

    public NonOwnerChestDetector(SoftGauge plugin, PlacementTracker placements) {
        super(plugin);
        this.placements = placements;
    }

    @Override
    public GameAction getAction() {
        return GameAction.CHEST_OPEN_NON_OWNER;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        if (inv.getLocation() == null) return;

        Block block = inv.getLocation().getBlock();
        if (!CONTAINERS.contains(block.getType())) return;
        if (!placements.isOwnedByOther(block, player)) return;

        String ownerName    = placements.getOwnerName(block).orElse("unknown");
        String containerName = block.getType().name().replace('_', ' ').toLowerCase();

        emit(record(GameAction.CHEST_OPEN_NON_OWNER, player)
                .at(block.getLocation())
                .description(player.getName() + " opened " + ownerName
                        + "'s " + containerName)
                .meta("container_owner", ownerName)
                .meta("container_type",  block.getType().name())
                .meta("x",              block.getX())
                .meta("y",              block.getY())
                .meta("z",              block.getZ())
                .meta("world",          block.getWorld().getName())
                .build());
    }
}
