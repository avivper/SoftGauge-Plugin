package org.softgauges_behaviors.registry;

import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.BehaviorDetector;
import org.softgauges_behaviors.detector.activity.ActivityDetector;
import org.softgauges_behaviors.detector.activity.ResourceGatheringDetector;
import org.softgauges_behaviors.detector.aggression.ArrowHarassmentDetector;
import org.softgauges_behaviors.detector.aggression.FriendlyKillDetector;
import org.softgauges_behaviors.detector.altruism.ItemTossDetector;
import org.softgauges_behaviors.detector.altruism.TradeDetector;
import org.softgauges_behaviors.detector.communication.ChatBehaviorDetector;
import org.softgauges_behaviors.detector.construction.CommunalCropDetector;
import org.softgauges_behaviors.detector.construction.HostileMobKillDetector;
import org.softgauges_behaviors.detector.construction.ResidentialBuildDetector;
import org.softgauges_behaviors.detector.griefing.ClaimedBlockBreakDetector;
import org.softgauges_behaviors.detector.griefing.FlintSteelFireDetector;
import org.softgauges_behaviors.detector.griefing.LavaHighAltDetector;
import org.softgauges_behaviors.detector.griefing.NonOwnerChestDetector;
import org.softgauges_behaviors.detector.griefing.TamedAnimalKillDetector;
import org.softgauges_behaviors.detector.griefing.TntNearStructureDetector;
import org.softgauges_behaviors.detector.support.FeedPlayerPetDetector;
import org.softgauges_behaviors.detector.support.PotionHealAllyDetector;
import org.softgauges_behaviors.tracking.PlacementTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central registry that instantiates and registers every {@link BehaviorDetector}.
 *
 * To add a new detector:
 *   1. Create the class in the appropriate {@code detector/} sub-package.
 *   2. Add one {@code register(...)} call in {@link #registerAll()}.
 *   That's it — no other file needs to change.
 */
public class DetectorRegistry {

    private final SoftGauge        plugin;
    private final PlacementTracker placements;
    private final List<BehaviorDetector> registered = new ArrayList<>();

    public DetectorRegistry(SoftGauge plugin, PlacementTracker placements) {
        this.plugin     = plugin;
        this.placements = placements;
    }

    /**
     * Instantiate and register all detectors with the Bukkit event system.
     * Call once from {@code SoftGauge.onEnable()}.
     */
    public void registerAll() {

        // ── 🟢 Altruism & Sharing ─────────────────────────────────────────────
        register(new ItemTossDetector(plugin));
        register(new TradeDetector(plugin));

        // ── 🟢 Support & Healing ──────────────────────────────────────────────
        register(new PotionHealAllyDetector(plugin));
        register(new FeedPlayerPetDetector(plugin));

        // ── 🟢 Construction & Community ───────────────────────────────────────
        register(new ResidentialBuildDetector(plugin));
        register(new CommunalCropDetector(plugin));
        register(new HostileMobKillDetector(plugin));

        // ── 🔴 PVP & Aggression ───────────────────────────────────────────────
        register(new FriendlyKillDetector(plugin));
        register(new ArrowHarassmentDetector(plugin));

        // ── 🔴 Griefing & Theft ───────────────────────────────────────────────
        register(new ClaimedBlockBreakDetector(plugin, placements));
        register(new NonOwnerChestDetector(plugin, placements));
        register(new TntNearStructureDetector(plugin, placements));
        register(new FlintSteelFireDetector(plugin, placements));
        register(new LavaHighAltDetector(plugin));
        register(new TamedAnimalKillDetector(plugin));

        // ── 💬 Communication (chat) ────────────────────────────────────────────
        register(new ChatBehaviorDetector(plugin));

        // ── 🏃 Activity & Session ─────────────────────────────────────────────
        register(new ActivityDetector(plugin));
        register(new ResourceGatheringDetector(plugin));

        plugin.getLogger().info(
                "DetectorRegistry: " + registered.size() + " detectors active.");
    }

    /** Returns an unmodifiable snapshot of all registered detectors. */
    public List<BehaviorDetector> getRegistered() {
        return Collections.unmodifiableList(registered);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void register(BehaviorDetector detector) {
        plugin.getServer().getPluginManager().registerEvents(detector, plugin);
        registered.add(detector);
        plugin.getLogger().fine("Registered detector: "
                + detector.getClass().getSimpleName()
                + " [" + detector.getAction() + "]");
    }
}