package org.softgauge;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.softgauge_player.Player;
import org.softgauge_roles.AssignRolesCommand;
import org.softgauge_roles.RoleManager;
import org.softgauge_roles.RoleRegistry;
import org.softgauges_behaviors.logging.BehaviorLogger;
import org.softgauges_behaviors.model.BehaviorRecord;
import org.softgauges_behaviors.model.GameAction;
import org.softgauges_behaviors.registry.DetectorRegistry;
import org.softgauges_behaviors.tracking.PlacementTracker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Plugin entry-point.
 *
 * Exposes two integration points for workmate programmers:
 *
 *   1. {@code behaviors.log} — one structured line per event (for log parsing)
 *   2. {@link #addBehaviorConsumer(Consumer)} — in-process Java callback with
 *      the full {@link BehaviorRecord} object (for direct data pipeline hooks)
 */
public class SoftGauge extends JavaPlugin implements Listener {

    private BehaviorLogger   behaviorLogger;
    private PlacementTracker placementTracker;
    private RoleManager      roleManager;

    private final Map<UUID, Player>               activeSessions    = new HashMap<>();
    private final CopyOnWriteArrayList<Consumer<BehaviorRecord>> consumers = new CopyOnWriteArrayList<>();

    @Override
    public void onEnable() {
        behaviorLogger   = new BehaviorLogger(this);
        placementTracker = new PlacementTracker();
        roleManager      = new RoleManager();

        getServer().getPluginManager().registerEvents(placementTracker, this);
        getServer().getPluginManager().registerEvents(this, this);

        // Behavior detection subsystem
        new DetectorRegistry(this, placementTracker).registerAll();

        // Team-role subsystem (Farmer / Librarian / Armorer)
        new RoleRegistry(this, roleManager).registerAll();

        // Commands
        getCommand("sg").setExecutor(new AssignRolesCommand(this));

        getLogger().info("SoftGauges behavior tracking enabled — " +
                "output: plugins/SoftGaugesBehaviors/behaviors.log");
    }

    @Override
    public void onDisable() {
        if (behaviorLogger != null) behaviorLogger.close();
        getLogger().info("SoftGauges disabled.");
    }

    // ── Session tracking ─────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        activeSessions.put(e.getPlayer().getUniqueId(),
                new Player(e.getPlayer().getUniqueId(), e.getPlayer().getName()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID playerId = e.getPlayer().getUniqueId();
        Player session = activeSessions.get(playerId);
        
        if (session != null) {
            // Log gathered resources summary
            if (!session.getGatheredResources().isEmpty()) {
                String itemsStr = session.getGatheredResources().entrySet().stream()
                        .map(entry -> entry.getValue() + "x " + entry.getKey())
                        .collect(Collectors.joining(", "));
                        
                dispatch(BehaviorRecord.detect(GameAction.SESSION_RESOURCE_SUMMARY, session)
                        .description(session.getPlayerName() + " collected items during session: " + itemsStr)
                        .meta("items", session.getGatheredResources())
                        .build());
            }
            
            // Log discarded resources summary
            if (!session.getDiscardedResources().isEmpty()) {
                String itemsStr = session.getDiscardedResources().entrySet().stream()
                        .map(entry -> entry.getValue() + "x " + entry.getKey())
                        .collect(Collectors.joining(", "));
                        
                dispatch(BehaviorRecord.detect(GameAction.SESSION_RESOURCE_DISCARDED_SUMMARY, session)
                        .description(session.getPlayerName() + " discarded items during session: " + itemsStr)
                        .meta("items", session.getDiscardedResources())
                        .build());
            }
        }
        
        activeSessions.remove(playerId);
    }

    // ── Dispatch (called by every detector) ──────────────────────────────────

    /**
     * Publish a BehaviorRecord:
     *  - writes to the behavior log file
     *  - notifies all registered consumers (synchronous, in order)
     *  - appends to the player's session history
     */
    public void dispatch(BehaviorRecord record) {
        behaviorLogger.log(record);
        for (Consumer<BehaviorRecord> consumer : consumers) {
            consumer.accept(record);
        }
        Player session = activeSessions.get(record.getActorId());
        if (session != null) session.recordBehavior(record);
    }

    // ── Workmate API ─────────────────────────────────────────────────────────

    /**
     * Register an in-process consumer that receives every BehaviorRecord.
     * Useful for JSON serialisation, dashboards, or data pipelines.
     *
     * <pre>
     *   plugin.addBehaviorConsumer(record -> myPipeline.send(record));
     * </pre>
     */
    public void addBehaviorConsumer(Consumer<BehaviorRecord> consumer) {
        consumers.add(consumer);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public PlacementTracker  getPlacementTracker() { return placementTracker; }
    public RoleManager       getRoleManager()      { return roleManager; }
    public Map<UUID, Player> getActiveSessions()   { return activeSessions; }
}
