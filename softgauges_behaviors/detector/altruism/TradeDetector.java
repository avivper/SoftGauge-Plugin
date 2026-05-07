package org.softgauges_behaviors.detector.altruism;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

/**
 * TRADE_WITH_VILLAGER
 *
 * Fires when a player clicks the result slot (slot 2) of a merchant inventory
 * to complete a villager trade.
 */
public class TradeDetector extends AbstractBehaviorDetector {

    /** Slot index of the trade result in a MerchantInventory. */
    private static final int RESULT_SLOT = 2;

    public TradeDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.TRADE_WITH_VILLAGER;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof MerchantInventory merchant)) return;
        if (event.getRawSlot() != RESULT_SLOT) return;

        ItemStack result = merchant.getItem(RESULT_SLOT);
        if (result == null || result.getType().isAir()) return;

        ItemStack cost1 = merchant.getItem(0);
        ItemStack cost2 = merchant.getItem(1);

        String villagerProfession = "NONE";
        if (merchant.getHolder() instanceof Villager villager) {
            villagerProfession = villager.getProfession().getKey().getKey().toUpperCase();
        }

        String itemReceived = result.getType().name().replace('_', ' ').toLowerCase();
        String costDesc = cost1 != null
                ? cost1.getAmount() + "x " + cost1.getType().name().replace('_', ' ').toLowerCase()
                : "?";

        emit(record(GameAction.TRADE_WITH_VILLAGER, player)
                .description(player.getName() + " traded with a villager ("
                        + villagerProfession.toLowerCase() + ") — received "
                        + result.getAmount() + "x " + itemReceived
                        + " for " + costDesc)
                .meta("item_received",       result.getType().name())
                .meta("item_received_amount",result.getAmount())
                .meta("item_cost",           cost1 != null ? cost1.getType().name() : "NONE")
                .meta("item_cost_2",         cost2 != null ? cost2.getType().name() : "NONE")
                .meta("villager_profession", villagerProfession)
                .build());
    }
}
