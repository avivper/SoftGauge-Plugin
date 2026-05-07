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

/**
 * TNT_PRIME_NEAR_STRUCTURE
 *
 * Fires when a player ignites TNT (via flint and steel or fire charge)
 * and there are player-placed blocks within a 10-block radius — indicating
 * the explosion may damage another player's structure.
 */
public class TntNearStructureDetector extends AbstractBehaviorDetector {

    private static final int  SCAN_RADIUS       = 10;
    private static final int  MIN_NEARBY_BLOCKS = 3; // at least 3 player blocks nearby

    private final PlacementTracker placements;

    public TntNearStructureDetector(SoftGauge plugin, PlacementTracker placements) {
        super(plugin);
        this.placements = placements;
    }

    @Override
    public GameAction getAction() {
        return GameAction.TNT_PRIME_NEAR_STRUCTURE;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.TNT) return;

        Player player = event.getPlayer();
        if (player == null) return;

        int    nearby       = placements.countNearbyPlayerBlocks(block, SCAN_RADIUS);
        if (nearby < MIN_NEARBY_BLOCKS) return;

        String nearestOwner = placements.nearestOwnerName(block, SCAN_RADIUS);

        emit(record(GameAction.TNT_PRIME_NEAR_STRUCTURE, player)
                .at(block.getLocation())
                .description(player.getName() + " ignited TNT near a player-built structure"
                        + " (" + nearby + " player-placed blocks within " + SCAN_RADIUS
                        + " blocks, nearest owner: " + nearestOwner + ")")
                .meta("nearby_player_blocks", nearby)
                .meta("nearest_owner",        nearestOwner)
                .meta("x",                    block.getX())
                .meta("y",                    block.getY())
                .meta("z",                    block.getZ())
                .meta("world",                block.getWorld().getName())
                .build());
    }
}
