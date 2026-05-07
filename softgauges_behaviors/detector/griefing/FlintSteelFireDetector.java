package org.softgauges_behaviors.detector.griefing;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockIgniteEvent;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;
import org.softgauges_behaviors.tracking.PlacementTracker;

import java.util.EnumSet;
import java.util.Set;

/**
 * FIRE_SPREAD_START
 *
 * Fires when a player uses Flint and Steel on a flammable block
 * (wool, wood planks, logs, etc.) that is adjacent to player-placed blocks —
 * indicating deliberate arson near a build.
 */
public class FlintSteelFireDetector extends AbstractBehaviorDetector {

    private static final Set<Material> FLAMMABLE = EnumSet.of(
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
            Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
            Material.CHERRY_PLANKS, Material.MANGROVE_PLANKS, Material.BAMBOO_PLANKS,
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL,
            Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
            Material.PINK_WOOL, Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL,
            Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
            Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL,
            Material.BLACK_WOOL,
            Material.OAK_FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE,
            Material.BOOKSHELF, Material.LECTERN,
            Material.HAY_BLOCK, Material.DRIED_KELP_BLOCK
    );

    private static final int SCAN_RADIUS       = 6;
    private static final int MIN_NEARBY_BLOCKS = 2;

    private final PlacementTracker placements;

    public FlintSteelFireDetector(SoftGauge plugin, PlacementTracker placements) {
        super(plugin);
        this.placements = placements;
    }

    @Override
    public GameAction getAction() {
        return GameAction.FIRE_SPREAD_START;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) return;

        Player player = event.getPlayer();
        if (player == null) return;

        // The ignited block is the fire block; check the block below for flammability
        Block fireBlock    = event.getBlock();
        Block ignitedBlock = fireBlock.getRelative(0, -1, 0);
        if (!FLAMMABLE.contains(ignitedBlock.getType())) return;

        int nearby = placements.countNearbyPlayerBlocks(ignitedBlock, SCAN_RADIUS);
        if (nearby < MIN_NEARBY_BLOCKS) return;

        String ignitedName = ignitedBlock.getType().name().replace('_', ' ').toLowerCase();

        emit(record(GameAction.FIRE_SPREAD_START, player)
                .at(ignitedBlock.getLocation())
                .description(player.getName() + " set fire to " + ignitedName
                        + " near a player build (" + nearby + " player blocks within "
                        + SCAN_RADIUS + " blocks)")
                .meta("ignited_material",   ignitedBlock.getType().name())
                .meta("nearby_player_blocks", nearby)
                .meta("x",                  ignitedBlock.getX())
                .meta("y",                  ignitedBlock.getY())
                .meta("z",                  ignitedBlock.getZ())
                .meta("world",              ignitedBlock.getWorld().getName())
                .build());
    }
}