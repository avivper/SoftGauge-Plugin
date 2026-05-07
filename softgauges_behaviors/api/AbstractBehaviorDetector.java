package org.softgauges_behaviors.api;

import org.softgauge.SoftGauge;
import org.softgauges_behaviors.model.BehaviorRecord;
import org.softgauges_behaviors.model.GameAction;

/**
 * Base class for all detectors (Template Method pattern).
 *
 * Subclasses declare their Bukkit {@code @EventHandler} methods and
 * call {@link #emit} to publish a completed {@link BehaviorRecord}.
 */
public abstract class AbstractBehaviorDetector implements BehaviorDetector {

    protected final SoftGauge plugin;

    protected AbstractBehaviorDetector(SoftGauge plugin) {
        this.plugin = plugin;
    }

    /**
     * Publish a detection result.  Thread-safe: may be called from async event threads.
     */
    protected final void emit(BehaviorRecord record) {
        plugin.dispatch(record);
    }

    /**
     * Convenience factory: begin building a record for an online player.
     */
    protected final BehaviorRecord.Builder record(GameAction action,
                                                   org.bukkit.entity.Player actor) {
        return BehaviorRecord.detect(action, actor);
    }
}
