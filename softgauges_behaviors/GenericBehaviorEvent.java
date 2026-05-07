package org.softgauges_behaviors;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Concrete BehaviorEvent used by all non-social listeners.
 * Carries a plain-English description constructed at the call site.
 */
public class GenericBehaviorEvent extends BehaviorEvent {

    private final String description;

    public GenericBehaviorEvent(BehaviorCategory category, BehaviorType type,
                                Location location, UUID actorId, String actorName,
                                String description) {
        super(category, type, location, actorId, actorName);
        this.description = description;
    }

    @Override
    public String getDescription() {

        return description;
    }
}
