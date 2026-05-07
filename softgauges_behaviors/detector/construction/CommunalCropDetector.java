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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CROP_PLANT_COMMUNAL
 *
 * Fires when a player plants seeds/crops in a chunk where at least one
 * OTHER player has already planted.  This signals a shared farm is forming.
 *
 * Tracking is per-chunk and per-session (resets on server restart).
 */
public class CommunalCropDetector extends AbstractBehaviorDetector {

    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT_SEEDS, Material.CARROT, Material.POTATO,
            Material.BEETROOT_SEEDS, Material.PUMPKIN_SEEDS, Material.MELON_SEEDS,
            Material.SWEET_BERRIES, Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD,
            Material.NETHER_WART
    );

    /** "world:cx:cz" → set of player UUIDs who planted in that chunk */
    private final Map<String, Set<UUID>> chunkPlanters = new HashMap<>();

    public CommunalCropDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.CROP_PLANT_COMMUNAL;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!CROPS.contains(block.getType())) return;

        Player player    = event.getPlayer();
        String chunkKey  = chunkKey(block);
        Set<UUID> planters = chunkPlanters.computeIfAbsent(chunkKey, k -> new HashSet<>());

        boolean othersPresent = planters.stream()
                .anyMatch(id -> !id.equals(player.getUniqueId()));

        planters.add(player.getUniqueId());

        if (!othersPresent) return; // first planter — wait for someone else

        Set<String> coNames = new HashSet<>();
        for (UUID id : planters) {
            if (!id.equals(player.getUniqueId())) {
                org.bukkit.OfflinePlayer op = plugin.getServer().getOfflinePlayer(id);
                coNames.add(op.getName() != null ? op.getName() : id.toString());
            }
        }
        String coPlanters = String.join(", ", coNames);
        String cropName   = block.getType().name().replace('_', ' ').toLowerCase();

        emit(record(GameAction.CROP_PLANT_COMMUNAL, player)
                .at(block.getLocation())
                .description(player.getName() + " planted " + cropName
                        + " in a shared farm (co-planters: " + coPlanters + ")")
                .meta("crop_type",  block.getType().name())
                .meta("x",         block.getX())
                .meta("y",         block.getY())
                .meta("z",         block.getZ())
                .meta("world",     block.getWorld().getName())
                .meta("co_planters", coPlanters)
                .build());
    }

    private String chunkKey(Block block) {
        return block.getWorld().getName()
                + ":" + block.getChunk().getX()
                + ":" + block.getChunk().getZ();
    }
}
