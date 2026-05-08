package org.softgauges_behaviors.model;

import org.softgauges_behaviors.BehaviorCategory;
import org.softgauges_behaviors.BehaviorType;

/**
 * Every distinct in-game detection signal.
 *
 * Each constant is self-describing: it knows its educational BehaviorType,
 * its BehaviorCategory, and its moral BehaviorSeverity.
 * A BehaviorRecord carries one GameAction; downstream consumers never need
 * to maintain a separate lookup table.
 *
 * Metadata keys documented per action — the workmate programmer can extract
 * them from BehaviorRecord.getMeta(key).
 */
public enum GameAction {

    // ── Positive: Altruism & Sharing ────────────────────────────────────────
    /**
     * Player drops a high-value item; a different player picks it up within 5 s.
     * meta: recipient, item_type, item_amount, pickup_delay_ms
     */
    ITEM_TOSS_NEAR_PLAYER(
            BehaviorType.GAVE_ITEM, BehaviorCategory.SOCIAL, BehaviorSeverity.POSITIVE),

    /**
     * Player completes a trade with a villager.
     * meta: item_received, item_cost, item_cost_2 (optional), villager_profession
     */
    TRADE_WITH_VILLAGER(
            BehaviorType.GAVE_ITEM, BehaviorCategory.SOCIAL, BehaviorSeverity.POSITIVE),

    // ── Positive: Support & Healing ─────────────────────────────────────────
    /**
     * Healing or Regen splash potion thrown by a player lands on a different player.
     * meta: targets (CSV of player names), effect_type, potion_type
     */
    POTION_SPLASH_HEAL_ALLY(
            BehaviorType.HELPED_PLAYER, BehaviorCategory.SOCIAL, BehaviorSeverity.POSITIVE),

    /**
     * Player feeds food to another player's tamed wolf or cat.
     * meta: pet_type, pet_owner, food_item
     */
    FEED_PLAYER_PET(
            BehaviorType.HELPED_PLAYER, BehaviorCategory.SOCIAL, BehaviorSeverity.POSITIVE),

    // ── Positive: Construction & Community ──────────────────────────────────
    /**
     * "Homemaking" block (bed, furnace, chest, torch…) placed in a concentrated zone.
     * meta: block_type, x, y, z, world, nearby_residential_count
     */
    BLOCK_PLACE_RESIDENTIAL(
            BehaviorType.INITIATED_GROUP_TASK, BehaviorCategory.LEADERSHIP, BehaviorSeverity.POSITIVE),

    /**
     * Seeds or crops planted; the chunk already has crops from ≥1 other player.
     * meta: crop_type, x, y, z, world, co_planters
     */
    CROP_PLANT_COMMUNAL(
            BehaviorType.INITIATED_GROUP_TASK, BehaviorCategory.LEADERSHIP, BehaviorSeverity.POSITIVE),

    /**
     * Player kills a Creeper or Skeleton that was targeting or near an ally.
     * meta: mob_type, ally_saved, distance_to_ally, mob_was_targeting_ally
     */
    MOB_KILL_HOSTILE_NEAR_ALLY(
            BehaviorType.SAVED_PLAYER, BehaviorCategory.SOCIAL, BehaviorSeverity.POSITIVE),

    // ── Negative: PVP & Aggression ───────────────────────────────────────────
    /**
     * Player kills another player (possible friendly-fire).
     * meta: victim, same_team, world
     */
    PLAYER_KILL_FRIENDLY(
            BehaviorType.KILLED_FRIENDLY_PLAYER, BehaviorCategory.EMOTIONAL_REGULATION, BehaviorSeverity.NEGATIVE),

    /**
     * Arrow shot by a player hits another player. Tracks harassment (repeated hits).
     * meta: victim, damage, hits_in_60s
     */
    ARROW_HIT_PLAYER(
            BehaviorType.HARASSED_PLAYER, BehaviorCategory.EMOTIONAL_REGULATION, BehaviorSeverity.NEGATIVE),

    // ── Negative: Griefing & Theft ───────────────────────────────────────────
    /**
     * Player breaks a block placed by a different player.
     * meta: block_owner, block_type, x, y, z, world
     */
    BLOCK_BREAK_CLAIMED(
            BehaviorType.GRIEFED_STRUCTURE, BehaviorCategory.SOCIAL, BehaviorSeverity.NEGATIVE),

    /**
     * Player opens a chest/barrel/shulker they did not place.
     * meta: container_owner, container_type, x, y, z, world
     */
    CHEST_OPEN_NON_OWNER(
            BehaviorType.STOLE_FROM_CONTAINER, BehaviorCategory.SOCIAL, BehaviorSeverity.NEGATIVE),

    /**
     * Player ignites TNT near player-built structures.
     * meta: nearby_player_blocks, nearest_owner, x, y, z, world
     */
    TNT_PRIME_NEAR_STRUCTURE(
            BehaviorType.PLACED_TNT_NEAR_STRUCTURE, BehaviorCategory.SOCIAL, BehaviorSeverity.NEGATIVE),

    /**
     * Player uses Flint & Steel on a flammable block near a player-built structure.
     * meta: ignited_material, nearby_player_blocks, x, y, z, world
     */
    FIRE_SPREAD_START(
            BehaviorType.STARTED_FIRE_IN_BUILD, BehaviorCategory.SOCIAL, BehaviorSeverity.NEGATIVE),

    /**
     * Player pours lava at high Y-level (lava-casting).
     * meta: y_level, world, x, z
     */
    LAVA_BUCKET_PLACE_HIGH_ALT(
            BehaviorType.PLACED_LAVA_DESTRUCTIVELY, BehaviorCategory.SOCIAL, BehaviorSeverity.NEGATIVE),

    /**
     * Player kills a tamed animal that belongs to someone else.
     * meta: animal_type, animal_owner, x, y, z, world
     */
    KILL_TAMED_ANIMAL(
            BehaviorType.KILLED_TAMED_ANIMAL, BehaviorCategory.SOCIAL, BehaviorSeverity.NEGATIVE),

    // ── Communication (chat-based) ───────────────────────────────────────────
    CHAT_GAVE_INSTRUCTION(
            BehaviorType.GAVE_INSTRUCTION, BehaviorCategory.LEADERSHIP, BehaviorSeverity.POSITIVE),
    CHAT_GROUP_TASK(
            BehaviorType.INITIATED_GROUP_TASK, BehaviorCategory.LEADERSHIP, BehaviorSeverity.POSITIVE),
    CHAT_DIVIDED_TASKS(
            BehaviorType.DIVIDED_TASKS, BehaviorCategory.LEADERSHIP, BehaviorSeverity.POSITIVE),
    CHAT_ASKED_FOR_HELP(
            BehaviorType.ASKED_FOR_HELP, BehaviorCategory.PROBLEM_SOLVING, BehaviorSeverity.NEUTRAL),
    CHAT_ENCOURAGEMENT(
            BehaviorType.SENT_ENCOURAGEMENT, BehaviorCategory.COMMUNICATION, BehaviorSeverity.POSITIVE),
    CHAT_DIRECTED_MESSAGE(
            BehaviorType.DIRECTED_MESSAGE, BehaviorCategory.COMMUNICATION, BehaviorSeverity.POSITIVE),
    CHAT_ASKED_QUESTION(
            BehaviorType.ASKED_QUESTION, BehaviorCategory.COMMUNICATION, BehaviorSeverity.POSITIVE),
    CHAT_AGGRESSIVE(
            BehaviorType.AGGRESSIVE_CHAT, BehaviorCategory.EMOTIONAL_REGULATION, BehaviorSeverity.NEGATIVE),
    CHAT_POSITIVE(
            BehaviorType.POSITIVE_EXPRESSION, BehaviorCategory.EMOTIONAL_REGULATION, BehaviorSeverity.POSITIVE),

    // ── Activity ─────────────────────────────────────────────────────────────
    FOLLOWED_PLAYER_PROXIMITY(
            BehaviorType.FOLLOWED_PLAYER, BehaviorCategory.LEADERSHIP, BehaviorSeverity.POSITIVE),
    BUILD_SELF_CORRECTED(
            BehaviorType.SELF_CORRECTED_BUILD, BehaviorCategory.PROBLEM_SOLVING, BehaviorSeverity.POSITIVE),
    DIED_SAME_SPOT(
            BehaviorType.REPEATED_MISTAKE, BehaviorCategory.PROBLEM_SOLVING, BehaviorSeverity.NEGATIVE),
    DISCONNECTED_AFTER_DEATH(
            BehaviorType.RAGE_QUIT, BehaviorCategory.EMOTIONAL_REGULATION, BehaviorSeverity.NEGATIVE),
    RESUMED_AFTER_DEATH(
            BehaviorType.CALM_RECOVERY, BehaviorCategory.EMOTIONAL_REGULATION, BehaviorSeverity.POSITIVE),
    ACTIVE_WITHOUT_CHATTING(
            BehaviorType.SILENT_PERIOD, BehaviorCategory.COMMUNICATION, BehaviorSeverity.NEUTRAL),
    IGNORED_NEARBY_HELP(
            BehaviorType.IGNORED_HELP_REQUEST, BehaviorCategory.SOCIAL, BehaviorSeverity.NEGATIVE),
            
    // ── Economy & Resources ──────────────────────────────────────────────────
    /**
     * Emitted when a player leaves the server, summarizing the items they collected.
     * meta: items (Map of Item Name -> Count)
     */
    SESSION_RESOURCE_SUMMARY(
            BehaviorType.COLLECTED_ITEM, BehaviorCategory.PROBLEM_SOLVING, BehaviorSeverity.NEUTRAL),
            
    /**
     * Emitted when a player leaves the server, summarizing the items they discarded/dropped.
     * meta: items (Map of Item Name -> Count)
     */
    SESSION_RESOURCE_DISCARDED_SUMMARY(
            BehaviorType.DISCARDED_ITEM, BehaviorCategory.PROBLEM_SOLVING, BehaviorSeverity.NEUTRAL),

    /**
     * Emitted when a player leaves the server, carrying their full chat transcript.
     * meta:
     *   chat_history (Map&lt;Long, String&gt;)  — epoch-ms → plain-text message, in order
     *   message_count (int)                  — total number of messages sent this session
     *   first_message_at (long, optional)    — epoch-ms of the first message (absent if none)
     *   last_message_at  (long, optional)    — epoch-ms of the last message (absent if none)
     */
    SESSION_CHAT_SUMMARY(
            BehaviorType.SESSION_CHAT_HISTORY, BehaviorCategory.COMMUNICATION, BehaviorSeverity.NEUTRAL),
            
    // ── Combat ───────────────────────────────────────────────────────────────
    /**
     * Player successfully defeats a hostile monster (Zombie, Skeleton, etc.)
     * meta: mob_type, weapon_used
     */
    DEFEATED_MONSTER(
            BehaviorType.DEFEATED_MONSTER, BehaviorCategory.PROBLEM_SOLVING, BehaviorSeverity.POSITIVE),
            
    /**
     * Player kills a friendly villager.
     * meta: villager_profession
     */
    KILLED_VILLAGER(
            BehaviorType.KILLED_VILLAGER, BehaviorCategory.SOCIAL, BehaviorSeverity.NEGATIVE);

    // ── Fields ────────────────────────────────────────────────────────────────

    private final BehaviorType behaviorType;
    private final BehaviorCategory category;
    private final BehaviorSeverity severity;

    GameAction(BehaviorType behaviorType, BehaviorCategory category, BehaviorSeverity severity) {
        this.behaviorType = behaviorType;
        this.category     = category;
        this.severity     = severity;
    }

    public BehaviorType    getBehaviorType() { return behaviorType; }
    public BehaviorCategory getCategory()   { return category; }
    public BehaviorSeverity getSeverity()   { return severity; }
}