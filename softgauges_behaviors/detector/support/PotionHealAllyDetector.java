package org.softgauges_behaviors.detector.support;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffectType;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

import java.util.ArrayList;
import java.util.List;

/**
 * POTION_SPLASH_HEAL_ALLY
 *
 * Fires when a player throws a Splash Potion of Healing or Regeneration
 * that lands on at least one other player.
 */
public class PotionHealAllyDetector extends AbstractBehaviorDetector {

    public PotionHealAllyDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.POTION_SPLASH_HEAL_ALLY;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player healer)) return;

        boolean isHealingPotion = event.getPotion().getEffects().stream()
                .anyMatch(e -> e.getType().equals(PotionEffectType.INSTANT_HEALTH)
                            || e.getType().equals(PotionEffectType.REGENERATION));
        if (!isHealingPotion) return;

        List<String> targets = new ArrayList<>();
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player target)) continue;
            if (target.getUniqueId().equals(healer.getUniqueId())) continue;
            targets.add(target.getName());
        }
        if (targets.isEmpty()) return;

        String effectType = event.getPotion().getEffects().stream()
                .anyMatch(e -> e.getType().equals(PotionEffectType.INSTANT_HEALTH))
                ? "INSTANT_HEALTH" : "REGENERATION";

        String potionMaterial = event.getPotion().getItem().getType().name();
        String targetsStr     = String.join(", ", targets);

        emit(record(GameAction.POTION_SPLASH_HEAL_ALLY, healer)
                .description(healer.getName() + " healed " + targetsStr
                        + " with a " + potionMaterial.replace('_', ' ').toLowerCase())
                .meta("targets",     targetsStr)
                .meta("effect_type", effectType)
                .meta("potion_type", potionMaterial)
                .build());
    }
}
