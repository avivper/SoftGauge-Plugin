package org.softgauges_behaviors.detector.construction;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

import java.util.EnumSet;
import java.util.Set;

/**
 * BLOCK_PLACE_RESIDENTIAL
 *
 * Fires whenever a player places a "homemaking" block — a bed, furnace, chest,
 * crafting table, torch, bookshelf, or lantern — indicating settlement activity.
 * The record includes a nearby-count so the downstream consumer can infer
 * whether a concentrated community area is forming.
 */
public class ResidentialBuildDetector extends AbstractBehaviorDetector {

    private static final Set<Material> RESIDENTIAL = EnumSet.of(
            // Beds (all 16 dye colours)
            Material.WHITE_BED, Material.ORANGE_BED, Material.MAGENTA_BED,
            Material.LIGHT_BLUE_BED, Material.YELLOW_BED, Material.LIME_BED,
            Material.PINK_BED, Material.GRAY_BED, Material.LIGHT_GRAY_BED,
            Material.CYAN_BED, Material.PURPLE_BED, Material.BLUE_BED,
            Material.BROWN_BED, Material.GREEN_BED, Material.RED_BED, Material.BLACK_BED,
            // Storage
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.SHULKER_BOX,
            // Cooking / crafting
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.CRAFTING_TABLE,
            // Lighting
            Material.TORCH, Material.WALL_TORCH,
            Material.SOUL_TORCH, Material.SOUL_WALL_TORCH,
            Material.LANTERN, Material.SOUL_LANTERN,
            // Knowledge
            Material.BOOKSHELF
    );

    /** Scan radius used to compute a nearby residential count. */
    private static final int SCAN_RADIUS = 8;

    public ResidentialBuildDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.BLOCK_PLACE_RESIDENTIAL;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlock();
        if (!RESIDENTIAL.contains(placed.getType())) return;

        Player player = event.getPlayer();
        int nearby    = countNearbyResidential(placed);
        String blockName = placed.getType().name().replace('_', ' ').toLowerCase();

        emit(record(GameAction.BLOCK_PLACE_RESIDENTIAL, player)
                .at(placed.getLocation())
                .description(player.getName() + " placed a " + blockName
                        + " (homemaking block, " + nearby
                        + " similar blocks nearby)")
                .meta("block_type",               placed.getType().name())
                .meta("x",                        placed.getX())
                .meta("y",                        placed.getY())
                .meta("z",                        placed.getZ())
                .meta("world",                    placed.getWorld().getName())
                .meta("nearby_residential_count", nearby)
                .build());
    }

    private int countNearbyResidential(Block center) {
        int count = 0;
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (RESIDENTIAL.contains(center.getRelative(dx, dy, dz).getType())) count++;
                }
            }
        }
        return count;
    }
}
