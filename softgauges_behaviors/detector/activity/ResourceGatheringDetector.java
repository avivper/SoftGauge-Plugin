package org.softgauges_behaviors.detector.activity;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

public class ResourceGatheringDetector extends AbstractBehaviorDetector {

    public ResourceGatheringDetector(SoftGauge plugin) {
        super(plugin);
    }

    // We do not have a single Action for this detector to return since it handles session data quietly.
    // We return null and override getAction() logic if strictly required by the parent class.
    @Override
    public GameAction getAction() {
        return GameAction.SESSION_RESOURCE_SUMMARY;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player bukkitPlayer)) return;
        
        org.softgauge_player.Player session = plugin.getActiveSessions().get(bukkitPlayer.getUniqueId());
        if (session == null) return;

        ItemStack item = event.getItem().getItemStack();
        String materialName = item.getType().name();
        int amount = item.getAmount();

        session.addGatheredResource(materialName, amount);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player bukkitPlayer = event.getPlayer();
        org.softgauge_player.Player session = plugin.getActiveSessions().get(bukkitPlayer.getUniqueId());
        if (session == null) return;

        ItemStack item = event.getItemDrop().getItemStack();
        String materialName = item.getType().name();
        int amount = item.getAmount();

        session.addDiscardedResource(materialName, amount);
    }
}