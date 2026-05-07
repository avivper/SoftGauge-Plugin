package org.softgauges_behaviors.detector.aggression;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

import java.util.HashMap;
import java.util.Map;

/**
 * ARROW_HIT_PLAYER
 *
 * Fires on every arrow hit by a player against another player.
 * Tracks cumulative hit count per shooter→victim pair over a 60-second window,
 * making harassment patterns visible to the downstream consumer.
 */
public class ArrowHarassmentDetector extends AbstractBehaviorDetector {

    private static final long  WINDOW_MS = 60_000;

    /** "shooterUUID:victimUUID" → HitRecord */
    private final Map<String, HitRecord> hitCounts = new HashMap<>();

    public ArrowHarassmentDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.ARROW_HIT_PLAYER;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Arrow || event.getDamager() instanceof SpectralArrow)) return;

        Player shooter = resolveShooter(event.getDamager());
        if (shooter == null) return;
        if (shooter.getUniqueId().equals(victim.getUniqueId())) return;

        String key = shooter.getUniqueId() + ":" + victim.getUniqueId();
        long   now = System.currentTimeMillis();

        HitRecord record = hitCounts.get(key);
        if (record == null || now - record.windowStart() > WINDOW_MS) {
            record = new HitRecord(now, 0);
        }
        record = new HitRecord(record.windowStart(), record.count() + 1);
        hitCounts.put(key, record);

        int hitsInWindow = record.count();
        double damage    = event.getFinalDamage();

        emit(record(GameAction.ARROW_HIT_PLAYER, shooter)
                .at(victim.getLocation())
                .description(shooter.getName() + " hit " + victim.getName()
                        + " with an arrow (hit #" + hitsInWindow + " in 60s, "
                        + String.format("%.1f", damage) + " dmg)")
                .meta("victim",       victim.getName())
                .meta("damage",       String.format("%.1f", damage))
                .meta("hits_in_60s",  hitsInWindow)
                .build());
    }

    private Player resolveShooter(org.bukkit.entity.Entity damager) {
        if (damager instanceof Arrow arrow && arrow.getShooter() instanceof Player p) return p;
        if (damager instanceof SpectralArrow sa && sa.getShooter() instanceof Player p) return p;
        return null;
    }

    private record HitRecord(long windowStart, int count) {}
}
