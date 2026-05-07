package org.softgauges_behaviors.tracking;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks which player placed each block so griefing detectors can determine ownership.
 *
 * Lifecycle: registered as a Bukkit Listener by SoftGauge.onEnable().
 * All access is on the main thread (BlockPlace/Break are synchronous events).
 */
public class PlacementTracker implements Listener {

    /** "world:x:y:z" → placer UUID */
    private final Map<String, UUID>   ownerIds   = new HashMap<>();
    /** "world:x:y:z" → placer name (cache — avoids offline player lookup) */
    private final Map<String, String> ownerNames = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        String key = key(event.getBlock());
        ownerIds.put(key, event.getPlayer().getUniqueId());
        ownerNames.put(key, event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        String key = key(event.getBlock());
        ownerIds.remove(key);
        ownerNames.remove(key);
    }

    /** Returns the UUID of the player who placed this block, if tracked. */
    public Optional<UUID> getOwner(Block block) {
        return Optional.ofNullable(ownerIds.get(key(block)));
    }

    /** Returns the name of the player who placed this block, if tracked. */
    public Optional<String> getOwnerName(Block block) {
        return Optional.ofNullable(ownerNames.get(key(block)));
    }

    /** True when the block was placed by a player other than {@code actor}. */
    public boolean isOwnedByOther(Block block, Player actor) {
        UUID owner = ownerIds.get(key(block));
        return owner != null && !owner.equals(actor.getUniqueId());
    }

    /** True when the block was placed by any tracked player. */
    public boolean isPlayerPlaced(Block block) {
        return ownerIds.containsKey(key(block));
    }

    /**
     * Count player-placed blocks within a cubic radius around the given block.
     * Used by TNT / fire detectors to assess proximity to builds.
     */
    public int countNearbyPlayerBlocks(Block center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = center.getRelative(dx, dy, dz);
                    if (isPlayerPlaced(b)) count++;
                }
            }
        }
        return count;
    }

    /**
     * Find the name of the most-represented owner among blocks near the center.
     * Returns "unknown" when no player blocks are present.
     */
    public String nearestOwnerName(Block center, int radius) {
        Map<String, Integer> counts = new HashMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    String name = ownerNames.get(key(center.getRelative(dx, dy, dz)));
                    if (name != null) counts.merge(name, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }
}
