package org.softgauges_behaviors.detector.support;

import org.bukkit.Material;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

import java.util.EnumSet;
import java.util.Set;

/**
 * FEED_PLAYER_PET
 *
 * Fires when a player right-clicks a tamed Wolf or Cat (owned by a different player)
 * while holding appropriate food — indicating care for another player's pet.
 */
public class FeedPlayerPetDetector extends AbstractBehaviorDetector {

    private static final Set<Material> WOLF_FOOD = EnumSet.of(
            Material.BEEF, Material.COOKED_BEEF,
            Material.PORKCHOP, Material.COOKED_PORKCHOP,
            Material.CHICKEN, Material.COOKED_CHICKEN,
            Material.ROTTEN_FLESH, Material.MUTTON, Material.COOKED_MUTTON,
            Material.RABBIT, Material.COOKED_RABBIT
    );

    private static final Set<Material> CAT_FOOD = EnumSet.of(
            Material.COD, Material.COOKED_COD,
            Material.SALMON, Material.COOKED_SALMON
    );

    public FeedPlayerPetDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.FEED_PLAYER_PET;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        if (!(entity instanceof Tameable tameable)) return;
        if (!tameable.isTamed()) return;

        AnimalTamer owner = tameable.getOwner();
        if (owner == null) return;
        if (owner.getUniqueId().equals(event.getPlayer().getUniqueId())) return;

        Player feeder = event.getPlayer();
        ItemStack held = feeder.getInventory().getItemInMainHand();
        if (!isValidFood(entity, held.getType())) return;

        String petType   = entity instanceof Wolf ? "Wolf" : "Cat";
        String ownerName = owner instanceof org.bukkit.OfflinePlayer op
                ? (op.getName() != null ? op.getName() : owner.getUniqueId().toString())
                : owner.getUniqueId().toString();
        String foodName  = held.getType().name().replace('_', ' ').toLowerCase();

        emit(record(GameAction.FEED_PLAYER_PET, feeder)
                .at(entity.getLocation())
                .description(feeder.getName() + " fed " + ownerName + "'s "
                        + petType + " with " + foodName)
                .meta("pet_type",  petType)
                .meta("pet_owner", ownerName)
                .meta("food_item", held.getType().name())
                .build());
    }

    private boolean isValidFood(Entity entity, Material material) {
        if (entity instanceof Wolf) return WOLF_FOOD.contains(material);
        if (entity instanceof Cat)  return CAT_FOOD.contains(material);
        return false;
    }
}
