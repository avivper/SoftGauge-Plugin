package org.softgauges_behaviors.api;

import org.bukkit.event.Listener;
import org.softgauges_behaviors.model.GameAction;

/**
 * Marker interface for all behavior detectors.
 *
 * Each concrete detector:
 *  - extends {@link AbstractBehaviorDetector}
 *  - declares one or more {@code @EventHandler} methods
 *  - calls {@code emit(BehaviorRecord)} to publish a detected behavior
 *
 * Detectors are registered automatically by {@link org.softgauges_behaviors.registry.DetectorRegistry}.
 */
public interface BehaviorDetector extends Listener {

    /** The specific game action this detector is responsible for. */
    GameAction getAction();
}
