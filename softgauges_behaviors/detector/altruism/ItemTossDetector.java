package org.softgauges_behaviors.detector.altruism;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ITEM_TOSS_NEAR_PLAYER
 * Fires when a player drops a high-value item and a different player
 * picks it up within 5 seconds.
 * "High-value" includes diamonds, netherite, golden apples, tools, weapons,
 * armour, bows, shields, and all edible food items.
 */
public class ItemTossDetector extends AbstractBehaviorDetector {

    private static final long PICKUP_WINDOW_MS = 5_000;

    private static final Set<Material> HIGH_VALUE = EnumSet.of(
            Material.DIAMOND, Material.DIAMOND_BLOCK,
            Material.NETHERITE_INGOT, Material.NETHERITE_SCRAP,
            Material.EMERALD,
            Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
            Material.DIAMOND_SWORD, Material.DIAMOND_AXE, Material.DIAMOND_PICKAXE,
            Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_SWORD, Material.NETHERITE_AXE, Material.NETHERITE_PICKAXE,
            Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            Material.BOW, Material.CROSSBOW, Material.SHIELD, Material.TRIDENT,
            Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN,
            Material.BREAD, Material.COOKED_SALMON, Material.COOKED_COD
    );

    // item entity ID → DropRecord
    private final Map<Integer, DropRecord> pending = new HashMap<>();
    // player UUID → most recent drop entity ID (to exclude death drops)
    private final Set<UUID> deathDropExclusions = new java.util.HashSet<>();

    public ItemTossDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.ITEM_TOSS_NEAR_PLAYER;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!isHighValue(stack)) return;

        pending.put(event.getItemDrop().getEntityId(), new DropRecord(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                stack.clone(),
                System.currentTimeMillis()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Items dropped on death should not count as intentional giving
        deathDropExclusions.add(event.getEntity().getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> deathDropExclusions.remove(event.getEntity().getUniqueId()), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player picker)) return;

        Item droppedItem = event.getItem();
        DropRecord drop  = pending.remove(droppedItem.getEntityId());
        if (drop == null) return;
        if (drop.dropperId().equals(picker.getUniqueId())) return;
        if (deathDropExclusions.contains(drop.dropperId())) return;

        long delay = System.currentTimeMillis() - drop.droppedAt();
        if (delay > PICKUP_WINDOW_MS) return;

        String itemName = formatItem(drop.item());
        emit(record(GameAction.ITEM_TOSS_NEAR_PLAYER, picker)
                .at(picker.getLocation())
                .description(drop.dropperName() + " tossed " + itemName
                        + ", picked up by " + picker.getName()
                        + " in " + String.format("%.1f", delay / 1000.0) + "s")
                .meta("recipient",       picker.getName())
                .meta("item_type",       drop.item().getType().name())
                .meta("item_amount",     drop.item().getAmount())
                .meta("pickup_delay_ms", delay)
                .build());
    }

    private boolean isHighValue(ItemStack stack) {
        return HIGH_VALUE.contains(stack.getType()) || stack.getType().isEdible();
    }

    private String formatItem(ItemStack stack) {
        return stack.getAmount() + "x "
                + stack.getType().name().replace('_', ' ').toLowerCase();
    }

    private record DropRecord(UUID dropperId, String dropperName, ItemStack item, long droppedAt) {}
}