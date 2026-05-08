package org.softgauge;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.softgauge_player.Player;
import org.softgauge_roles.AssignRolesCommand;
import org.softgauge_roles.MissionsCommand;
import org.softgauge_roles.ResponCommand;
import org.softgauge_roles.RoleManager;
import org.softgauge_roles.RoleRegistry;
import org.softgauge_crafting.SmartCraftAccessListener;
import org.softgauge_crafting.SmartCraftChatListener;
import org.softgauge_crafting.SmartCraftCommand;
import org.softgauge_crafting.SmartCraftPromptManager;
import org.softgauge_crafting.SmartCraftingService;
import org.softgauge_streak.StreakCommand;
import org.softgauge_streak.StreakLoginListener;
import org.softgauge_streak.StreakRepository;
import org.softgauge_streak.StreakService;
import org.softgauges_behaviors.logging.BehaviorLogger;
import org.softgauges_behaviors.logging.ChatLogger;
import org.softgauges_behaviors.logging.ScoreExporter;
import org.softgauges_behaviors.logging.ScoreExporterCommand;
import org.softgauges_behaviors.model.BehaviorRecord;
import org.softgauges_behaviors.model.GameAction;
import org.softgauges_behaviors.registry.DetectorRegistry;
import org.softgauges_behaviors.tracking.ChatHistoryTracker;
import org.softgauges_behaviors.tracking.PlacementTracker;
import org.softgauge.ai.AIFeedbackExporter;

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

    private BehaviorLogger          behaviorLogger;
    private ChatLogger              chatLogger;
    private PlacementTracker        placementTracker;
    private RoleManager             roleManager;
    private StreakService           streakService;
    private SmartCraftingService    smartCraftingService;
    private SmartCraftPromptManager smartCraftPromptManager;

    private final Map<UUID, Player>               activeSessions    = new HashMap<>();
    private final CopyOnWriteArrayList<Consumer<BehaviorRecord>> consumers = new CopyOnWriteArrayList<>();

    @Override
    public void onEnable() {
        // Create default config
        saveDefaultConfig();

        behaviorLogger   = new BehaviorLogger(this);
        chatLogger       = new ChatLogger(this);
        placementTracker = new PlacementTracker();
        roleManager      = new RoleManager();

        getServer().getPluginManager().registerEvents(placementTracker, this);
        getServer().getPluginManager().registerEvents(new ChatHistoryTracker(this, chatLogger), this);
        getServer().getPluginManager().registerEvents(this, this);

        // Behavior detection subsystem
        new DetectorRegistry(this, placementTracker).registerAll();

        // Team-role subsystem (Farmer / Librarian / Armorer)
        new RoleRegistry(this, roleManager).registerAll();

        // Daily login streak subsystem (Duolingo-style)
        StreakRepository streakRepo = new StreakRepository(this);
        streakService = new StreakService(streakRepo);
        getServer().getPluginManager()
                .registerEvents(new StreakLoginListener(this, streakService), this);

        // Smart-craft subsystem (typo-tolerant chat-prompt crafting)
        smartCraftingService    = new SmartCraftingService(this);
        smartCraftPromptManager = new SmartCraftPromptManager();
        getServer().getPluginManager().registerEvents(smartCraftPromptManager, this);
        getServer().getPluginManager().registerEvents(
                new SmartCraftAccessListener(smartCraftPromptManager), this);
        getServer().getPluginManager().registerEvents(
                new SmartCraftChatListener(this, smartCraftPromptManager, smartCraftingService),
                this);

        // Commands
        if (getCommand("sg") != null) {
            getCommand("sg").setExecutor(new AssignRolesCommand(this));
        }
        if (getCommand("respon") != null) {
            getCommand("respon").setExecutor(new ResponCommand(this));
        }
        if (getCommand("missions") != null) {
            getCommand("missions").setExecutor(new MissionsCommand(this));
        }
        if (getCommand("exportscores") != null) {
            getCommand("exportscores").setExecutor(new ScoreExporterCommand(this));
        }
        if (getCommand("streak") != null) {
            getCommand("streak").setExecutor(new StreakCommand(streakService));
        }
        if (getCommand("craft") != null) {
            getCommand("craft").setExecutor(
                    new SmartCraftCommand(smartCraftPromptManager, smartCraftingService));
        }

        getLogger().info("SoftGauges behavior tracking enabled — " +
                "output: plugins/SoftGaugesBehaviors/behaviors.log");
    }

    @Override
    public void onDisable() {
        if (behaviorLogger != null) behaviorLogger.close();
        if (chatLogger != null)     chatLogger.close();

        // Persist any in-memory streak changes that haven't already been flushed
        if (streakService != null) streakService.getRepository().persist();

        // Auto-export scores to JSON on shutdown
        getLogger().info("Auto-exporting behavior scores to player_scores.json...");
        ScoreExporter exporter = new ScoreExporter(this);
        exporter.runExport();
        
        // Auto-generate AI English feedback on shutdown
        getLogger().info("Auto-generating AI feedback...");
        AIFeedbackExporter aiExporter = new AIFeedbackExporter(this);
        aiExporter.runExport();

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
            org.bukkit.entity.Player bukkitPlayer = e.getPlayer();
            
            // Log gathered resources summary
            if (!session.getGatheredResources().isEmpty()) {
                String itemsStr = session.getGatheredResources().entrySet().stream()
                        .map(entry -> entry.getValue() + "x " + entry.getKey())
                        .collect(Collectors.joining(", "));
                        
                dispatch(BehaviorRecord.detect(GameAction.SESSION_RESOURCE_SUMMARY, bukkitPlayer)
                        .description(session.getPlayerName() + " collected items during session: " + itemsStr)
                        .meta("items", session.getGatheredResources())
                        .build());
            }
            
            // Log discarded resources summary
            if (!session.getDiscardedResources().isEmpty()) {
                String itemsStr = session.getDiscardedResources().entrySet().stream()
                        .map(entry -> entry.getValue() + "x " + entry.getKey())
                        .collect(Collectors.joining(", "));

                dispatch(BehaviorRecord.detect(GameAction.SESSION_RESOURCE_DISCARDED_SUMMARY, bukkitPlayer)
                        .description(session.getPlayerName() + " discarded items during session: " + itemsStr)
                        .meta("items", session.getDiscardedResources())
                        .build());
            }

            // Log full chat transcript summary
            Map<Long, String> chatHistory = session.getChatHistory();
            if (!chatHistory.isEmpty()) {
                long firstAt = chatHistory.keySet().stream().min(Long::compareTo).orElse(0L);
                long lastAt  = chatHistory.keySet().stream().max(Long::compareTo).orElse(0L);

                dispatch(BehaviorRecord.detect(GameAction.SESSION_CHAT_SUMMARY, bukkitPlayer)
                        .description(session.getPlayerName() + " sent "
                                + chatHistory.size() + " chat message(s) during session")
                        .meta("chat_history",     chatHistory)
                        .meta("message_count",    chatHistory.size())
                        .meta("first_message_at", firstAt)
                        .meta("last_message_at",  lastAt)
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

    public PlacementTracker        getPlacementTracker()       { return placementTracker; }
    public ChatLogger              getChatLogger()             { return chatLogger; }
    public RoleManager             getRoleManager()            { return roleManager; }
    public StreakService           getStreakService()          { return streakService; }
    public SmartCraftingService    getSmartCraftingService()   { return smartCraftingService; }
    public SmartCraftPromptManager getSmartCraftPromptManager(){ return smartCraftPromptManager; }
    public Map<UUID, Player>       getActiveSessions()         { return activeSessions; }
}