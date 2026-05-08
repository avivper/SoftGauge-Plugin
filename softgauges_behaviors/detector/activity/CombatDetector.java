package org.softgauges_behaviors.detector.activity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

public class CombatDetector extends AbstractBehaviorDetector {

    public CombatDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.DEFEATED_MONSTER; // representative
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        String weaponName = weapon.getType().isAir() ? "fists" : weapon.getType().name().replace('_', ' ').toLowerCase();
        String mobName = dead.getType().name().replace('_', ' ').toLowerCase();

        // Check if killed a Monster (Zombies, Skeletons, Spiders, Enderman, etc.)
        if (dead instanceof Monster) {
            emit(record(GameAction.DEFEATED_MONSTER, killer)
                    .at(dead.getLocation())
                    .description(killer.getName() + " defeated a " + mobName + " using " + weaponName)
                    .meta("mob_type", dead.getType().name())
                    .meta("weapon_used", weapon.getType().name())
                    .build());
            return;
        }

        // Check if killed a Villager (Bad behavior)
        if (dead instanceof Villager villager) {
            String profession = villager.getProfession().getKey().getKey().toUpperCase();
            
            emit(record(GameAction.KILLED_VILLAGER, killer)
                    .at(dead.getLocation())
                    .description(killer.getName() + " killed a friendly villager (" + profession + ")")
                    .meta("villager_profession", profession)
                    .meta("weapon_used", weapon.getType().name())
                    .build());
        }
    }
}