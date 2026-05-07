package org.softgauges_behaviors.model;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable data object produced by every detector.
 *
 * This is the primary API hook point for downstream consumers (data pipeline,
 * JSON serializer, dashboard, etc.).  Access fields via getters; access
 * structured detection context via {@link #getMeta(String)}.
 *
 * Usage (inside a detector):
 * <pre>
 *   emit(BehaviorRecord.detect(GameAction.ITEM_TOSS_NEAR_PLAYER, dropper)
 *       .description("Alice tossed 3x Diamond, picked up by Bob in 2.1s")
 *       .meta("recipient",       "Bob")
 *       .meta("item_type",       "DIAMOND")
 *       .meta("item_amount",     3)
 *       .meta("pickup_delay_ms", 2100L)
 *       .build());
 * </pre>
 */
public final class BehaviorRecord {

    private final UUID            actorId;
    private final String          actorName;
    private final GameAction      gameAction;
    private final String          description;
    private final Instant         timestamp;
    private final Location        location;
    private final Map<String, Object> meta;

    private BehaviorRecord(Builder b) {
        this.actorId     = b.actorId;
        this.actorName   = b.actorName;
        this.gameAction  = b.gameAction;
        this.description = b.description;
        this.timestamp   = Instant.now();
        this.location    = b.location;
        this.meta        = Collections.unmodifiableMap(new LinkedHashMap<>(b.meta));
    }

    // ── Convenience accessors that delegate to GameAction ────────────────────

    public org.softgauges_behaviors.BehaviorType     getBehaviorType() { return gameAction.getBehaviorType(); }
    public org.softgauges_behaviors.BehaviorCategory getCategory()     { return gameAction.getCategory(); }
    public BehaviorSeverity                          getSeverity()     { return gameAction.getSeverity(); }

    // ── Primary getters ───────────────────────────────────────────────────────

    public UUID            getActorId()    { return actorId; }
    public String          getActorName()  { return actorName; }
    public GameAction      getGameAction() { return gameAction; }
    public String          getDescription(){ return description; }
    public Instant         getTimestamp()  { return timestamp; }
    public Location        getLocation()   { return location; }

    /** Full metadata map — use for serialisation. */
    public Map<String, Object> getMetaMap() { return meta; }

    /** Typed metadata lookup. Returns null if the key is absent. */
    @SuppressWarnings("unchecked")
    public <T> T getMeta(String key) { return (T) meta.get(key); }

    // ── Formatted log line (written to behaviors.log) ─────────────────────────

    public String toLogLine() {
        String loc = location != null
                ? String.format("%s@(%d,%d,%d)",
                        location.getWorld() != null ? location.getWorld().getName() + ":" : "",
                        location.getBlockX(), location.getBlockY(), location.getBlockZ())
                : "unknown";

        return String.format("[%s] [%s] [%s/%s] %s %s: %s | %s | meta=%s",
                timestamp,
                gameAction.getSeverity(),
                gameAction.getCategory(),
                gameAction.getBehaviorType(),
                actorName,
                gameAction.name(),
                description,
                loc,
                meta);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /** Start building a record for a player + action pair. */
    public static Builder detect(GameAction action, Player actor) {
        return new Builder(action, actor.getUniqueId(), actor.getName(), actor.getLocation());
    }

    /** Start building a record when only UUID+name are available (async context). */
    public static Builder detect(GameAction action, UUID actorId, String actorName, Location location) {
        return new Builder(action, actorId, actorName, location);
    }

    public static final class Builder {
        private final GameAction      gameAction;
        private final UUID            actorId;
        private final String          actorName;
        private       Location        location;
        private       String          description = "";
        private final Map<String, Object> meta    = new LinkedHashMap<>();

        private Builder(GameAction action, UUID actorId, String actorName, Location location) {
            this.gameAction = action;
            this.actorId    = actorId;
            this.actorName  = actorName;
            this.location   = location;
        }

        public Builder description(String desc)           { this.description = desc;     return this; }
        public Builder at(Location loc)                   { this.location    = loc;      return this; }
        public Builder meta(String key, Object value)     { meta.put(key, value);        return this; }

        public BehaviorRecord build() { return new BehaviorRecord(this); }
    }
}
