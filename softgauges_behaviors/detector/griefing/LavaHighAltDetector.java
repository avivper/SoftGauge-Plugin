package org.softgauges_behaviors.detector.griefing;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

/**
 * LAVA_BUCKET_PLACE_HIGH_ALT
 *
 * Fires when a player pours lava at Y ≥ HIGH_ALT_THRESHOLD.
 * Lava cast from height creates unstoppable landscape destruction —
 * this threshold flags the intentional pattern without catching normal
 * nether/deep-cave lava use.
 */
public class LavaHighAltDetector extends AbstractBehaviorDetector {

    /** Minimum Y-level that triggers the high-altitude flag. */
    private static final int HIGH_ALT_THRESHOLD = 80;

    public LavaHighAltDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.LAVA_BUCKET_PLACE_HIGH_ALT;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.LAVA_BUCKET) return;

        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (target.getY() < HIGH_ALT_THRESHOLD) return;

        Player player = event.getPlayer();

        emit(record(GameAction.LAVA_BUCKET_PLACE_HIGH_ALT, player)
                .at(target.getLocation())
                .description(player.getName() + " placed lava at high altitude (Y="
                        + target.getY() + ") — potential lava cast")
                .meta("y_level", target.getY())
                .meta("x",       target.getX())
                .meta("z",       target.getZ())
                .meta("world",   target.getWorld().getName())
                .build());
    }
}
