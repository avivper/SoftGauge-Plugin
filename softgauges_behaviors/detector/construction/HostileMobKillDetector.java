package org.softgauges_behaviors.detector.construction;

import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * MOB_KILL_HOSTILE_NEAR_ALLY
 *
 * Fires when a player kills a Creeper or Skeleton while another player
 * is within 12 blocks, indicating they may have protected their ally.
 *
 * If the mob's current target was the nearby ally, the metadata flag
 * {@code mob_was_targeting_ally} is set to {@code true}.
 */
public class HostileMobKillDetector extends AbstractBehaviorDetector {

    private static final Set<EntityType> DANGEROUS_MOBS = EnumSet.of(
            EntityType.CREEPER,
            EntityType.SKELETON,
            EntityType.STRAY,
            EntityType.WITHER_SKELETON
    );

    private static final double ALLY_RADIUS = 12.0;

    public HostileMobKillDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.MOB_KILL_HOSTILE_NEAR_ALLY;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!DANGEROUS_MOBS.contains(dead.getType())) return;

        Player killer = dead.getKiller();
        if (killer == null) return;

        Optional<Player> allySaved = findNearbyAlly(dead, killer);
        if (allySaved.isEmpty()) return;

        Player ally               = allySaved.get();
        double dist               = dead.getLocation().distance(ally.getLocation());
        boolean mobTargetingAlly  = dead instanceof Creature creature
                && creature.getTarget() != null
                && creature.getTarget().getUniqueId().equals(ally.getUniqueId());

        String mobName = dead.getType().name().replace('_', ' ').toLowerCase();

        emit(record(GameAction.MOB_KILL_HOSTILE_NEAR_ALLY, killer)
                .at(killer.getLocation())
                .description(killer.getName() + " killed a " + mobName
                        + " that was near " + ally.getName()
                        + (mobTargetingAlly ? " (mob was targeting ally)" : ""))
                .meta("mob_type",              dead.getType().name())
                .meta("ally_saved",            ally.getName())
                .meta("distance_to_ally",      String.format("%.1f", dist))
                .meta("mob_was_targeting_ally",mobTargetingAlly)
                .build());
    }

    private Optional<Player> findNearbyAlly(LivingEntity mob, Player killer) {
        Collection<Entity> nearby = mob.getWorld().getNearbyEntities(
                mob.getLocation(), ALLY_RADIUS, ALLY_RADIUS, ALLY_RADIUS);
        return nearby.stream()
                .filter(e -> e instanceof Player p
                        && !p.getUniqueId().equals(killer.getUniqueId()))
                .map(e -> (Player) e)
                .min((a, b) -> Double.compare(
                        a.getLocation().distanceSquared(mob.getLocation()),
                        b.getLocation().distanceSquared(mob.getLocation())));
    }
}
