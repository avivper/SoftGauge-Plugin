package org.softgauge_roles.role;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.softgauge_roles.AbstractRolePlugin;
import org.softgauge_roles.PlayerRole;
import org.softgauge_roles.RoleManager;

/**
 * Armorer role plugin.
 *
 * <p><b>Workstation:</b> Blast Furnace</p>
 *
 * <p><b>Mechanic:</b> when a player holding the {@link PlayerRole#ARMORER}
 * role wears armor and that armor takes durability damage, the damage applied
 * to each piece is halved (50 % less wear). Tool durability damage is not
 * affected — the bonus is armor-only.</p>
 *
 * <p>Implementation notes: the event fires once per damaged item, so we don't
 * need to differentiate slots — we simply identify "is this an armor item?"
 * and halve the damage. Java integer division naturally clamps a single point
 * of damage to zero, which is the desired "tiny damage gets absorbed" effect.</p>
 */
public class ArmorerRole extends AbstractRolePlugin {

    public ArmorerRole(RoleManager roleManager) {
        super(roleManager);
    }

    @Override
    public PlayerRole getRole() {
        return PlayerRole.ARMORER;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!hasThisRole(player)) return;

        ItemStack item = event.getItem();
        if (!isArmor(item.getType())) return;

        // Halve the durability damage. Integer floor; min 0.
        int reduced = event.getDamage() / 2;
        event.setDamage(reduced);

        // If the reduction wiped the damage entirely we still want a clean event —
        // setDamage(0) leaves the item undamaged, which is the intended effect.
    }

    /**
     * True when the material is wearable armor (helmet, chestplate, leggings,
     * boots, plus the special-case Turtle Helmet / Elytra).
     */
    private static boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || material == Material.TURTLE_HELMET
                || material == Material.ELYTRA;
    }
}
