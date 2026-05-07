package org.softgauges_behaviors.detector.griefing;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;
import org.softgauges_behaviors.tracking.PlacementTracker;

/**
 * BLOCK_BREAK_CLAIMED
 *
 * Fires when a player breaks a block that was placed by a different player —
 * indicating a potential griefing action on another player's structure.
 */
public class ClaimedBlockBreakDetector extends AbstractBehaviorDetector {

    private final PlacementTracker placements;

    public ClaimedBlockBreakDetector(SoftGauge plugin, PlacementTracker placements) {
        super(plugin);
        this.placements = placements;
    }

    @Override
    public GameAction getAction() {
        return GameAction.BLOCK_BREAK_CLAIMED;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block  block  = event.getBlock();
        Player player = event.getPlayer();

        if (!placements.isOwnedByOther(block, player)) return;

        String ownerName = placements.getOwnerName(block).orElse("unknown");
        String blockName = block.getType().name().replace('_', ' ').toLowerCase();

        emit(record(GameAction.BLOCK_BREAK_CLAIMED, player)
                .at(block.getLocation())
                .description(player.getName() + " broke " + ownerName
                        + "'s " + blockName + " block")
                .meta("block_owner", ownerName)
                .meta("block_type",  block.getType().name())
                .meta("x",          block.getX())
                .meta("y",          block.getY())
                .meta("z",          block.getZ())
                .meta("world",      block.getWorld().getName())
                .build());
    }
}
