package org.softgauges_behaviors.detector.griefing;

import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

/**
 * KILL_TAMED_ANIMAL
 *
 * Fires when a player kills a tamed animal that belongs to a different player.
 * Covers wolves, cats, horses, llamas, parrots — any entity implementing
 * {@link Tameable} with a non-null owner.
 */
public class TamedAnimalKillDetector extends AbstractBehaviorDetector {

    public TamedAnimalKillDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.KILL_TAMED_ANIMAL;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Tameable tameable)) return;
        if (!tameable.isTamed()) return;

        AnimalTamer owner = tameable.getOwner();
        if (owner == null) return;

        Player killer = dead.getKiller();
        if (killer == null) return;
        if (killer.getUniqueId().equals(owner.getUniqueId())) return;

        String ownerName  = owner instanceof org.bukkit.OfflinePlayer op && op.getName() != null
                ? op.getName() : owner.getUniqueId().toString();
        String animalType = dead.getType().name().replace('_', ' ').toLowerCase();

        emit(record(GameAction.KILL_TAMED_ANIMAL, killer)
                .at(dead.getLocation())
                .description(killer.getName() + " killed " + ownerName
                        + "'s tamed " + animalType)
                .meta("animal_type",  dead.getType().name())
                .meta("animal_owner", ownerName)
                .meta("x",           dead.getLocation().getBlockX())
                .meta("y",           dead.getLocation().getBlockY())
                .meta("z",           dead.getLocation().getBlockZ())
                .meta("world",       dead.getWorld().getName())
                .build());
    }
}
