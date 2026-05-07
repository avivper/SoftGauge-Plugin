package org.softgauges_behaviors;

import org.bukkit.Location;

import java.time.Instant;
import java.util.UUID;

public abstract class BehaviorEvent {
    private final BehaviorCategory category;
    private final BehaviorType type;
    private final long timestamp;
    private final Location location;
    private final UUID actorId;
    private final String actorName;

    protected BehaviorEvent(BehaviorCategory category, BehaviorType type,
                            Location location, UUID actorId, String actorName) {
        this.category = category;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.location = location;
        this.actorId = actorId;
        this.actorName = actorName;
    }

    /** Human-readable plain-English description, e.g. "Player1 gave Oak Log to Player2". */
    public abstract String getDescription();

    /** Formatted line written to the behavior log. */
    public String toLogLine() {
        String loc = location != null
                ? String.format("(%d,%d,%d)", location.getBlockX(), location.getBlockY(), location.getBlockZ())
                : "unknown";
        return String.format("[%s] [%s] [%s] %s: %s @%s",
                Instant.ofEpochMilli(timestamp), category, type, actorName, getDescription(), loc);
    }

    public BehaviorCategory getCategory() {
        return category;
    }
    public BehaviorType getType() {
        return type;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public Location getLocation() {
        return location;
    }
    public UUID getActorId() {
        return actorId;
    }
    public String getActorName() {
        return actorName;
    }
}
