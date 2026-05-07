package org.softgauges_behaviors.detector.activity;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multi-action detector covering all non-chat, non-griefing in-game activities.
 *
 * Detected GameActions:
 *   ENTERED_NEW_CHUNK          — player enters a chunk they haven't visited this session
 *   BUILD_SELF_CORRECTED       — player breaks and re-places a block at the same spot within 30 s
 *   DIED_SAME_SPOT             — player dies within 20 blocks of their previous death within 5 min
 *   DISCONNECTED_AFTER_DEATH   — player quits within 10 s of dying (rage-quit signal)
 *   RESUMED_AFTER_DEATH        — player is still online and active 60 s after respawning
 *   FOLLOWED_PLAYER_PROXIMITY  — player stays within 8 blocks of a moving player for ≥ 20 s
 *   ACTIVE_WITHOUT_CHATTING    — player has moved in the last 60 s but hasn't chatted in 5 min
 *
 * Primary action (informational only — this detector covers many): ENTERED_NEW_CHUNK.
 */
public class ActivityDetector extends AbstractBehaviorDetector {

    // ── ENTERED_NEW_CHUNK ─────────────────────────────────────────────────────
    private final Map<UUID, Set<String>> visitedChunks = new HashMap<>();

    // ── BUILD_SELF_CORRECTED ──────────────────────────────────────────────────
    /** "playerUUID:world:x:y:z" → placement timestamp */
    private final Map<String, Long> recentPlacements = new HashMap<>();
    private static final long SELF_CORRECT_WINDOW_MS = 30_000;

    // ── DIED_SAME_SPOT ────────────────────────────────────────────────────────
    private final Map<UUID, DeathSnapshot> lastDeaths  = new HashMap<>();
    private static final double REPEAT_DEATH_RADIUS    = 20.0;
    private static final long   REPEAT_DEATH_WINDOW_MS = 5 * 60_000L;

    // ── DISCONNECTED_AFTER_DEATH ──────────────────────────────────────────────
    private final Map<UUID, Long> recentDeathTimes = new HashMap<>();
    private static final long RAGE_QUIT_WINDOW_MS  = 10_000;

    // ── RESUMED_AFTER_DEATH ───────────────────────────────────────────────────
    private final Set<UUID> awaitingCalmCheck              = new HashSet<>();
    /** Players who rage-quit or sent aggressive chat after last respawn — disqualified. */
    private final Set<UUID> disqualifiedFromCalmRecovery   = new HashSet<>();
    private static final long CALM_RECOVERY_DELAY_TICKS    = 20L * 60; // 60 s

    // ── FOLLOWED_PLAYER_PROXIMITY ─────────────────────────────────────────────
    private final Map<UUID, LocationSnapshot>  lastLocations     = new HashMap<>();
    private final Map<String, Long>            followStartTimes  = new HashMap<>();
    private final Map<String, Long>            followCooldowns   = new HashMap<>();
    private static final double FOLLOW_RADIUS       = 8.0;
    private static final long   FOLLOW_DURATION_MS  = 20_000;
    private static final long   FOLLOW_COOLDOWN_MS  = 60_000;

    // ── ACTIVE_WITHOUT_CHATTING ───────────────────────────────────────────────
    private final Map<UUID, Long> lastMoveTime    = new HashMap<>();
    private final Map<UUID, Long> lastSilentFired = new HashMap<>();
    /**
     * Populated by observing CHAT_* BehaviorRecords via the consumer registered
     * in {@link #registerChatObserver()}.
     */
    private final Map<UUID, Long> lastChatObserved = new HashMap<>();
    private static final long SILENT_PERIOD_MS      = 5 * 60_000L;
    private static final long SILENT_COOLDOWN_MS    = 5 * 60_000L;
    private static final long ACTIVE_MOVE_THRESHOLD = 60_000L;

    // ─────────────────────────────────────────────────────────────────────────

    public ActivityDetector(SoftGauge plugin) {
        super(plugin);
        startScheduledTasks();
        registerChatObserver();
    }

    @Override
    public GameAction getAction() {
        return GameAction.ENTERED_NEW_CHUNK; // representative; detector covers many actions
    }

    // ── ENTERED_NEW_CHUNK ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        Player player = event.getPlayer();
        long   now    = System.currentTimeMillis();

        lastMoveTime.put(player.getUniqueId(), now);
        lastLocations.put(player.getUniqueId(),
                new LocationSnapshot(player.getLocation().clone(), now));

        String chunkKey = player.getWorld().getName()
                + ":" + player.getLocation().getChunk().getX()
                + ":" + player.getLocation().getChunk().getZ();

        boolean isNew = visitedChunks
                .computeIfAbsent(player.getUniqueId(), k -> new HashSet<>())
                .add(chunkKey);

        if (isNew) {
            emit(record(GameAction.ENTERED_NEW_CHUNK, player)
                    .description(player.getName() + " entered a new area at chunk ("
                            + player.getLocation().getChunk().getX()
                            + ", " + player.getLocation().getChunk().getZ() + ")")
                    .meta("chunk_x", player.getLocation().getChunk().getX())
                    .meta("chunk_z", player.getLocation().getChunk().getZ())
                    .meta("world",   player.getWorld().getName())
                    .build());
        }
    }

    // ── BUILD_SELF_CORRECTED ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        recentPlacements.put(
                placementKey(event.getPlayer(), event.getBlock()),
                System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        String key    = placementKey(event.getPlayer(), event.getBlock());
        Long   placed = recentPlacements.remove(key);
        if (placed == null) return;
        if (System.currentTimeMillis() - placed > SELF_CORRECT_WINDOW_MS) return;

        Player player    = event.getPlayer();
        String blockName = event.getBlock().getType().name().replace('_', ' ').toLowerCase();

        emit(record(GameAction.BUILD_SELF_CORRECTED, player)
                .at(event.getBlock().getLocation())
                .description(player.getName()
                        + " self-corrected a build (broke and replaced " + blockName + ")")
                .meta("block_type", event.getBlock().getType().name())
                .meta("x",         event.getBlock().getX())
                .meta("y",         event.getBlock().getY())
                .meta("z",         event.getBlock().getZ())
                .build());
    }

    // ── DIED_SAME_SPOT / DISCONNECTED_AFTER_DEATH ────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player  = event.getEntity();
        Location loc   = player.getLocation();
        long     now   = System.currentTimeMillis();

        // DISCONNECTED_AFTER_DEATH (rage-quit) tracking
        recentDeathTimes.put(player.getUniqueId(), now);

        // DIED_SAME_SPOT
        DeathSnapshot prev = lastDeaths.get(player.getUniqueId());
        if (prev != null
                && now - prev.timestamp() < REPEAT_DEATH_WINDOW_MS
                && prev.location().getWorld() != null
                && prev.location().getWorld().equals(loc.getWorld())
                && prev.location().distance(loc) <= REPEAT_DEATH_RADIUS) {

            String cause = event.getDeathMessage() != null
                    ? event.getDeathMessage() : "unknown cause";
            emit(record(GameAction.DIED_SAME_SPOT, player)
                    .at(loc)
                    .description(player.getName()
                            + " died again at the same location (" + cause + ")")
                    .meta("death_cause",  cause)
                    .meta("x", loc.getBlockX())
                    .meta("y", loc.getBlockY())
                    .meta("z", loc.getBlockZ())
                    .build());
        }
        lastDeaths.put(player.getUniqueId(), new DeathSnapshot(loc.clone(), now));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player  = event.getPlayer();
        Long   deathAt = recentDeathTimes.remove(player.getUniqueId());

        if (deathAt != null
                && System.currentTimeMillis() - deathAt <= RAGE_QUIT_WINDOW_MS) {
            emit(record(GameAction.DISCONNECTED_AFTER_DEATH, player)
                    .at(player.getLocation())
                    .description(player.getName()
                            + " disconnected within "
                            + RAGE_QUIT_WINDOW_MS / 1000 + "s of dying (rage-quit signal)")
                    .build());
            disqualifiedFromCalmRecovery.add(player.getUniqueId());
        }

        // Clean up session state
        visitedChunks.remove(player.getUniqueId());
        lastMoveTime.remove(player.getUniqueId());
        lastLocations.remove(player.getUniqueId());
        lastSilentFired.remove(player.getUniqueId());
        awaitingCalmCheck.remove(player.getUniqueId());
        disqualifiedFromCalmRecovery.remove(player.getUniqueId());
    }

    // ── RESUMED_AFTER_DEATH ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        awaitingCalmCheck.add(id);
        disqualifiedFromCalmRecovery.remove(id);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!awaitingCalmCheck.remove(id)) return;
            if (disqualifiedFromCalmRecovery.contains(id)) return;

            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) return;

            emit(record(GameAction.RESUMED_AFTER_DEATH, player)
                    .at(player.getLocation())
                    .description(player.getName()
                            + " recovered calmly and continued playing after death")
                    .build());
        }, CALM_RECOVERY_DELAY_TICKS);
    }

    // ── Scheduled: FOLLOWED_PLAYER_PROXIMITY + ACTIVE_WITHOUT_CHATTING ───────

    private void startScheduledTasks() {
        // Follow detection every 5 s
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkFollowing,
                20L * 5, 20L * 5);

        // Silent period check every 60 s
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkSilentPeriods,
                20L * 60, 20L * 60);
    }

    private void checkFollowing() {
        Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
        if (online.size() < 2) return;
        long now = System.currentTimeMillis();

        for (Player follower : online) {
            LocationSnapshot fSnap = lastLocations.get(follower.getUniqueId());
            if (fSnap == null) continue;

            for (Player leader : online) {
                if (leader.getUniqueId().equals(follower.getUniqueId())) continue;
                if (!leader.getWorld().equals(follower.getWorld())) continue;

                LocationSnapshot lSnap = lastLocations.get(leader.getUniqueId());
                if (lSnap == null || now - lSnap.timestamp() > 5_000) continue; // leader stale

                double dist = fSnap.location().distance(lSnap.location());
                String key  = follower.getUniqueId() + ">" + leader.getUniqueId();

                if (dist > FOLLOW_RADIUS) {
                    followStartTimes.remove(key);
                    continue;
                }

                long startedAt = followStartTimes.computeIfAbsent(key, k -> now);
                if (now - startedAt < FOLLOW_DURATION_MS) continue;

                Long cooldown = followCooldowns.get(key);
                if (cooldown != null && now - cooldown < FOLLOW_COOLDOWN_MS) continue;

                followCooldowns.put(key, now);
                followStartTimes.remove(key);

                emit(record(GameAction.FOLLOWED_PLAYER_PROXIMITY, follower)
                        .at(follower.getLocation())
                        .description(follower.getName() + " has been following "
                                + leader.getName() + " for "
                                + FOLLOW_DURATION_MS / 1000 + "+ seconds")
                        .meta("leader",      leader.getName())
                        .meta("distance",    String.format("%.1f", dist))
                        .build());
            }
        }
    }

    private void checkSilentPeriods() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();

            Long lastMove  = lastMoveTime.get(id);
            Long lastChat  = lastChatObserved.get(id);
            Long lastFired = lastSilentFired.get(id);

            if (lastMove == null || now - lastMove > ACTIVE_MOVE_THRESHOLD) continue;
            if (lastChat != null && now - lastChat < SILENT_PERIOD_MS) continue;
            if (lastFired != null && now - lastFired < SILENT_COOLDOWN_MS) continue;

            lastSilentFired.put(id, now);
            emit(record(GameAction.ACTIVE_WITHOUT_CHATTING, player)
                    .at(player.getLocation())
                    .description(player.getName()
                            + " has been active but silent for "
                            + SILENT_PERIOD_MS / 60_000 + "+ minutes")
                    .build());
        }
    }

    // ── Chat observer — tracks last-chat time via BehaviorRecord consumer ─────

    private void registerChatObserver() {
        plugin.addBehaviorConsumer(record -> {
            // If it's a chat-origin record, update our last-chat tracking
            String actionName = record.getGameAction().name();
            if (actionName.startsWith("CHAT_")) {
                lastChatObserved.put(record.getActorId(), record.getTimestamp().toEpochMilli());

                // If the player sent aggressive chat, disqualify calm recovery
                if (record.getGameAction() == GameAction.CHAT_AGGRESSIVE) {
                    disqualifiedFromCalmRecovery.add(record.getActorId());
                }
            }
        });
    }

    // ── Internal records ──────────────────────────────────────────────────────

    private static String placementKey(Player p, Block b) {
        return p.getUniqueId() + ":" + b.getWorld().getName()
                + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private record DeathSnapshot(Location location, long timestamp) {}
    private record LocationSnapshot(Location location, long timestamp) {}
}
